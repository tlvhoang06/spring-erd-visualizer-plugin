package com.github.tlvhoang06.springerdvisualizer.analyzer

import com.github.tlvhoang06.springerdvisualizer.model.RelationshipModel
import com.github.tlvhoang06.springerdvisualizer.model.RelationshipType
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiClassObjectAccessExpression

object RelationshipAnalyzer {

    private val ONE_TO_ONE = setOf("javax.persistence.OneToOne", "jakarta.persistence.OneToOne", "OneToOne")
    private val ONE_TO_MANY = setOf("javax.persistence.OneToMany", "jakarta.persistence.OneToMany", "OneToMany")
    private val MANY_TO_ONE = setOf("javax.persistence.ManyToOne", "jakarta.persistence.ManyToOne", "ManyToOne")
    private val MANY_TO_MANY = setOf("javax.persistence.ManyToMany", "jakarta.persistence.ManyToMany", "ManyToMany")

    private val JOIN_COLUMN = setOf("javax.persistence.JoinColumn", "jakarta.persistence.JoinColumn", "JoinColumn")
    private val JOIN_TABLE = setOf("javax.persistence.JoinTable", "jakarta.persistence.JoinTable", "JoinTable")

    fun analyzeRelationship(sourceEntityName: String, field: PsiField): RelationshipModel? {
        val relAnno = field.annotations.firstOrNull { isRelationshipAnnotation(it) } ?: return null
        val relType = getRelationshipType(relAnno) ?: return null

        val targetEntityName = resolveTargetEntity(field, relAnno) ?: return null
        val mappedBy = getStringAttribute(relAnno, "mappedBy")
        val joinColumn = resolveJoinColumnName(field)

        return RelationshipModel(
            sourceEntity = sourceEntityName,
            targetEntity = targetEntityName,
            type = relType,
            mappedBy = mappedBy,
            joinColumn = joinColumn
        )
    }

    private fun isRelationshipAnnotation(annotation: PsiAnnotation): Boolean {
        return getRelationshipType(annotation) != null
    }

    private fun getRelationshipType(annotation: PsiAnnotation): RelationshipType? {
        return when {
            annotationMatches(annotation, ONE_TO_ONE) -> RelationshipType.ONE_TO_ONE
            annotationMatches(annotation, ONE_TO_MANY) -> RelationshipType.ONE_TO_MANY
            annotationMatches(annotation, MANY_TO_ONE) -> RelationshipType.MANY_TO_ONE
            annotationMatches(annotation, MANY_TO_MANY) -> RelationshipType.MANY_TO_MANY
            else -> null
        }
    }

    private fun resolveTargetEntity(field: PsiField, relAnno: PsiAnnotation): String? {
        // Check targetEntity attribute in annotation (e.g. @OneToMany(targetEntity = Order.class))
        val targetEntityAttr = relAnno.findAttributeValue("targetEntity")
        if (targetEntityAttr is PsiClassObjectAccessExpression) {
            val typeText = targetEntityAttr.operand.type.presentableText
            if (typeText.isNotBlank() && typeText != "void") {
                return typeText
            }
        }

        // Infer from PsiType
        val type = field.type
        if (type is PsiClassType) {
            // Check if generic collection (e.g. List<Order>, Set<Order>)
            val parameters = type.parameters
            if (parameters.isNotEmpty()) {
                val genericType = parameters.first().presentableText
                if (genericType.isNotBlank()) {
                    return extractShortClassName(genericType)
                }
            }
            return extractShortClassName(type.presentableText)
        }

        return null
    }

    private fun extractShortClassName(rawType: String): String {
        return rawType.substringBefore('<').substringAfterLast('.').trim()
    }

    private fun resolveJoinColumnName(field: PsiField): String? {
        val joinColumnAnno = field.annotations.firstOrNull { annotationMatches(it, JOIN_COLUMN) }
        if (joinColumnAnno != null) {
            val name = getStringAttribute(joinColumnAnno, "name")
            if (!name.isNullOrBlank()) return name
        }

        val joinTableAnno = field.annotations.firstOrNull { annotationMatches(it, JOIN_TABLE) }
        if (joinTableAnno != null) {
            val name = getStringAttribute(joinTableAnno, "name")
            if (!name.isNullOrBlank()) return name
        }

        return null
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

    private fun annotationMatches(annotation: PsiAnnotation, targetSet: Set<String>): Boolean {
        val qualifiedName = annotation.qualifiedName
        if (qualifiedName != null && targetSet.contains(qualifiedName)) {
            return true
        }
        val shortName = annotation.nameReferenceElement?.referenceName
        return shortName != null && targetSet.contains(shortName)
    }
}
