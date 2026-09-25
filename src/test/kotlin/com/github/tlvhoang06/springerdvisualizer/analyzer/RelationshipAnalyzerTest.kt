package com.github.tlvhoang06.springerdvisualizer.analyzer

import com.github.tlvhoang06.springerdvisualizer.model.RelationshipType
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RelationshipAnalyzerTest {

    private fun createMockAnnotation(qualifiedName: String, attributes: Map<String, Any> = emptyMap()): PsiAnnotation {
        val annotation = mock<PsiAnnotation>()
        whenever(annotation.qualifiedName).thenReturn(qualifiedName)

        for ((attrName, attrVal) in attributes) {
            val literal = mock<PsiLiteralExpression>()
            whenever(literal.value).thenReturn(attrVal)
            whenever(literal.text).thenReturn(attrVal.toString())
            whenever(annotation.findAttributeValue(attrName)).thenReturn(literal)
        }
        return annotation
    }

    @Test
    fun testOneToManyRelationshipWithMappedBy() {
        val field = mock<PsiField>()
        whenever(field.name).thenReturn("orders")

        val listType = mock<PsiClassType>()
        val orderType = mock<PsiType>()
        whenever(orderType.presentableText).thenReturn("Order")
        whenever(listType.parameters).thenReturn(arrayOf(orderType))
        whenever(listType.presentableText).thenReturn("List<Order>")
        whenever(field.type).thenReturn(listType)

        val oneToManyAnno = createMockAnnotation("jakarta.persistence.OneToMany", mapOf("mappedBy" to "user"))
        whenever(field.annotations).thenReturn(arrayOf(oneToManyAnno))

        val result = RelationshipAnalyzer.analyzeRelationship("User", field)

        assertNotNull(result)
        assertEquals("User", result!!.sourceEntity)
        assertEquals("Order", result.targetEntity)
        assertEquals(RelationshipType.ONE_TO_MANY, result.type)
        assertEquals("user", result.mappedBy)
    }

    @Test
    fun testManyToOneRelationshipWithJoinColumn() {
        val field = mock<PsiField>()
        whenever(field.name).thenReturn("user")

        val userType = mock<PsiClassType>()
        whenever(userType.parameters).thenReturn(emptyArray())
        whenever(userType.presentableText).thenReturn("User")
        whenever(field.type).thenReturn(userType)

        val manyToOneAnno = createMockAnnotation("jakarta.persistence.ManyToOne")
        val joinColumnAnno = createMockAnnotation("jakarta.persistence.JoinColumn", mapOf("name" to "user_id"))
        whenever(field.annotations).thenReturn(arrayOf(manyToOneAnno, joinColumnAnno))

        val result = RelationshipAnalyzer.analyzeRelationship("Order", field)

        assertNotNull(result)
        assertEquals("Order", result!!.sourceEntity)
        assertEquals("User", result.targetEntity)
        assertEquals(RelationshipType.MANY_TO_ONE, result.type)
        assertEquals("user_id", result.joinColumn)
    }

    @Test
    fun testManyToManyRelationship() {
        val field = mock<PsiField>()
        whenever(field.name).thenReturn("courses")

        val setType = mock<PsiClassType>()
        val courseType = mock<PsiType>()
        whenever(courseType.presentableText).thenReturn("Course")
        whenever(setType.parameters).thenReturn(arrayOf(courseType))
        whenever(setType.presentableText).thenReturn("Set<Course>")
        whenever(field.type).thenReturn(setType)

        val manyToManyAnno = createMockAnnotation("jakarta.persistence.ManyToMany")
        whenever(field.annotations).thenReturn(arrayOf(manyToManyAnno))

        val result = RelationshipAnalyzer.analyzeRelationship("Student", field)

        assertNotNull(result)
        assertEquals("Student", result!!.sourceEntity)
        assertEquals("Course", result.targetEntity)
        assertEquals(RelationshipType.MANY_TO_MANY, result.type)
    }

    @Test
    fun testOneToOneRelationship() {
        val field = mock<PsiField>()
        whenever(field.name).thenReturn("profile")

        val profileType = mock<PsiClassType>()
        whenever(profileType.parameters).thenReturn(emptyArray())
        whenever(profileType.presentableText).thenReturn("Profile")
        whenever(field.type).thenReturn(profileType)

        val oneToOneAnno = createMockAnnotation("jakarta.persistence.OneToOne")
        whenever(field.annotations).thenReturn(arrayOf(oneToOneAnno))

        val result = RelationshipAnalyzer.analyzeRelationship("User", field)

        assertNotNull(result)
        assertEquals("User", result!!.sourceEntity)
        assertEquals("Profile", result.targetEntity)
        assertEquals(RelationshipType.ONE_TO_ONE, result.type)
    }
}
