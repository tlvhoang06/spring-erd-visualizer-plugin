package com.github.tlvhoang06.springerdvisualizer.analyzer

import com.github.tlvhoang06.springerdvisualizer.model.FieldModel
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiModifier

object FieldAnalyzer {

    private val PRIMARY_KEY_ANNOTATIONS = setOf(
        "javax.persistence.Id",
        "jakarta.persistence.Id",
        "javax.persistence.EmbeddedId",
        "jakarta.persistence.EmbeddedId",
        "Id",
        "EmbeddedId"
    )

    private val COLUMN_ANNOTATIONS = setOf(
        "javax.persistence.Column",
        "jakarta.persistence.Column",
        "Column"
    )

    private val TRANSIENT_ANNOTATIONS = setOf(
        "javax.persistence.Transient",
        "jakarta.persistence.Transient",
        "Transient"
    )

    private val EMBEDDED_ANNOTATIONS = setOf(
        "javax.persistence.Embedded",
        "jakarta.persistence.Embedded",
        "Embedded"
    )

    private val EMBEDDABLE_ANNOTATIONS = setOf(
        "javax.persistence.Embeddable",
        "jakarta.persistence.Embeddable",
        "Embeddable"
    )

    private val EMBEDDED_ID_ANNOTATIONS = setOf(
        "javax.persistence.EmbeddedId",
        "jakarta.persistence.EmbeddedId",
        "EmbeddedId"
    )

    private val RELATIONSHIP_ANNOTATIONS = setOf(
        "javax.persistence.OneToOne",
        "jakarta.persistence.OneToOne",
        "javax.persistence.OneToMany",
        "jakarta.persistence.OneToMany",
        "javax.persistence.ManyToOne",
        "jakarta.persistence.ManyToOne",
        "javax.persistence.ManyToMany",
        "jakarta.persistence.ManyToMany",
        "OneToOne",
        "OneToMany",
        "ManyToOne",
        "ManyToMany"
    )

    fun isTransient(field: PsiField): Boolean {
        if (field.hasModifierProperty(PsiModifier.TRANSIENT) || field.hasModifierProperty(PsiModifier.STATIC)) {
            return true
        }
        return field.annotations.any { annotationMatches(it, TRANSIENT_ANNOTATIONS) }
    }

    fun isRelationshipField(field: PsiField): Boolean {
        return field.annotations.any { annotationMatches(it, RELATIONSHIP_ANNOTATIONS) }
    }

    fun isEmbeddedField(field: PsiField): Boolean {
        if (field.annotations.any { annotationMatches(it, EMBEDDED_ANNOTATIONS) || annotationMatches(it, EMBEDDED_ID_ANNOTATIONS) }) {
            return true
        }
        val typeClass = (field.type as? PsiClassType)?.resolve()
        return typeClass != null && isEmbeddableClass(typeClass)
    }

    fun isEmbeddableClass(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotationMatches(it, EMBEDDABLE_ANNOTATIONS) }
    }

    fun analyzeEmbeddedField(field: PsiField): List<FieldModel> {
        val result = mutableListOf<FieldModel>()
        val isPk = field.annotations.any { annotationMatches(it, PRIMARY_KEY_ANNOTATIONS) }
        val prefix = field.name

        val typeClass = (field.type as? PsiClassType)?.resolve() ?: return emptyList()
        if (!isEmbeddableClass(typeClass) && !field.annotations.any { annotationMatches(it, EMBEDDED_ANNOTATIONS) || annotationMatches(it, EMBEDDED_ID_ANNOTATIONS) }) {
            return emptyList()
        }

        val overrides = parseAttributeOverrides(field)

        for (embedField in typeClass.fields) {
            if (isTransient(embedField)) continue
            val baseModel = analyzeField(embedField) ?: continue

            val compositeName = "$prefix.${baseModel.name}"
            val overriddenCol = overrides[baseModel.name] ?: baseModel.columnName

            result.add(
                baseModel.copy(
                    name = compositeName,
                    columnName = overriddenCol,
                    isPrimaryKey = isPk || baseModel.isPrimaryKey
                )
            )
        }

        return result
    }

    fun analyzeField(field: PsiField): FieldModel? {
        if (isTransient(field)) {
            return null
        }

        val fieldName = field.name
        val fieldType = field.type.presentableText
        val isPk = field.annotations.any { annotationMatches(it, PRIMARY_KEY_ANNOTATIONS) }

        val columnAnnotation = field.annotations.firstOrNull { annotationMatches(it, COLUMN_ANNOTATIONS) }

        var columnName: String? = null
        var nullable = true
        var unique = false

        if (columnAnnotation != null) {
            columnName = getStringAttribute(columnAnnotation, "name")
            nullable = getBooleanAttribute(columnAnnotation, "nullable", defaultValue = true)
            unique = getBooleanAttribute(columnAnnotation, "unique", defaultValue = false)
        }

        return FieldModel(
            name = fieldName,
            type = fieldType,
            columnName = if (!columnName.isNullOrBlank()) columnName else fieldName,
            isPrimaryKey = isPk,
            nullable = nullable,
            unique = unique
        )
    }

    private fun parseAttributeOverrides(field: PsiField): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (annotation in field.annotations) {
            val shortName = annotation.nameReferenceElement?.referenceName
            if (shortName == "AttributeOverride" || annotation.qualifiedName?.endsWith("AttributeOverride") == true) {
                parseSingleAttributeOverride(annotation, map)
            } else if (shortName == "AttributeOverrides" || annotation.qualifiedName?.endsWith("AttributeOverrides") == true) {
                val value = annotation.findAttributeValue("value")
                if (value is PsiArrayInitializerMemberValue) {
                    for (initializer in value.initializers) {
                        if (initializer is PsiAnnotation) {
                            parseSingleAttributeOverride(initializer, map)
                        }
                    }
                }
            }
        }
        return map
    }

    private fun parseSingleAttributeOverride(annotation: PsiAnnotation, map: MutableMap<String, String>) {
        val attrName = getStringAttribute(annotation, "name") ?: return
        val colVal = annotation.findAttributeValue("column")
        if (colVal is PsiAnnotation) {
            val colName = getStringAttribute(colVal, "name")
            if (colName != null && colName.isNotBlank()) {
                map[attrName] = colName
            }
        }
    }

    private fun String?.isNullOrBlank(): Boolean = this == null || this.trim().isEmpty()

    private fun annotationMatches(annotation: PsiAnnotation, targetSet: Set<String>): Boolean {
        val qualifiedName = annotation.qualifiedName
        if (qualifiedName != null && targetSet.contains(qualifiedName)) {
            return true
        }
        val shortName = annotation.nameReferenceElement?.referenceName
        return shortName != null && targetSet.contains(shortName)
    }

    private fun getStringAttribute(annotation: PsiAnnotation, attributeName: String): String? {
        val value = annotation.findAttributeValue(attributeName) ?: return null
        if (value is PsiLiteralExpression) {
            val literalValue = value.value
            if (literalValue is String && literalValue.isNotBlank()) {
                return literalValue
            }
        }
        return null
    }

    private fun getBooleanAttribute(annotation: PsiAnnotation, attributeName: String, defaultValue: Boolean): Boolean {
        val value = annotation.findAttributeValue(attributeName) ?: return defaultValue
        if (value is PsiLiteralExpression) {
            val literalValue = value.value
            if (literalValue is Boolean) {
                return literalValue
            }
        }
        val text = value.text
        if (text == "true") return true
        if (text == "false") return false
        return defaultValue
    }
}

