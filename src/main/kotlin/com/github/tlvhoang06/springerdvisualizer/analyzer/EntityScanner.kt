package com.github.tlvhoang06.springerdvisualizer.analyzer

import com.github.tlvhoang06.springerdvisualizer.model.EntityModel
import com.github.tlvhoang06.springerdvisualizer.model.FieldModel
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiLiteralExpression

object EntityScanner {

    private val ENTITY_ANNOTATIONS = setOf(
        "javax.persistence.Entity",
        "jakarta.persistence.Entity",
        "Entity"
    )

    private val TABLE_ANNOTATIONS = setOf(
        "javax.persistence.Table",
        "jakarta.persistence.Table",
        "Table"
    )

    fun isEntity(psiClass: PsiClass): Boolean {
        if (psiClass.isInterface || psiClass.isEnum || psiClass.isAnnotationType) {
            return false
        }
        return psiClass.annotations.any { annotationMatches(it, ENTITY_ANNOTATIONS) }
    }

    fun scanEntity(psiClass: PsiClass): EntityModel? {
        if (!isEntity(psiClass)) {
            return null
        }

        val entityName = psiClass.name ?: return null
        val tableName = resolveTableName(psiClass, entityName)

        val fields = mutableListOf<FieldModel>()
        val relationships = mutableListOf<com.github.tlvhoang06.springerdvisualizer.model.RelationshipModel>()

        for (field in psiClass.fields) {
            if (FieldAnalyzer.isRelationshipField(field)) {
                val relModel = RelationshipAnalyzer.analyzeRelationship(entityName, field)
                if (relModel != null) {
                    relationships.add(relModel)
                }
                continue
            }
            if (FieldAnalyzer.isEmbeddedField(field)) {
                val embeddedFields = FieldAnalyzer.analyzeEmbeddedField(field)
                if (embeddedFields.isNotEmpty()) {
                    fields.addAll(embeddedFields)
                    continue
                }
            }
            val fieldModel = FieldAnalyzer.analyzeField(field)
            if (fieldModel != null) {
                fields.add(fieldModel)
            }
        }

        val pkgName = psiClass.qualifiedName?.let {
            val lastDot = it.lastIndexOf('.')
            if (lastDot > 0) it.substring(0, lastDot) else ""
        } ?: ""

        return EntityModel(
            name = entityName,
            tableName = tableName,
            packageName = pkgName,
            fields = fields,
            relationships = relationships
        )
    }

    private fun resolveTableName(psiClass: PsiClass, defaultName: String): String {
        val tableAnnotation = psiClass.annotations.firstOrNull { annotationMatches(it, TABLE_ANNOTATIONS) }
        if (tableAnnotation != null) {
            val nameValue = tableAnnotation.findAttributeValue("name")
            if (nameValue is PsiLiteralExpression) {
                val valStr = nameValue.value
                if (valStr is String && valStr.isNotBlank()) {
                    return valStr
                }
            }
        }
        return deriveDefaultTableName(defaultName)
    }

    private fun deriveDefaultTableName(entityName: String): String {
        // Convert camelCase to snake_case lowercased, e.g. UserOrder -> user_orders
        val snakeCase = entityName.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
        return snakeCase
    }

    private fun annotationMatches(annotation: PsiAnnotation, targetSet: Set<String>): Boolean {
        val qualifiedName = annotation.qualifiedName
        if (qualifiedName != null && targetSet.contains(qualifiedName)) {
            return true
        }
        val shortName = annotation.nameReferenceElement?.referenceName
        return shortName != null && targetSet.contains(shortName)
    }
}
