package com.github.tlvhoang06.springerdvisualizer.analyzer

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EntityScannerTest {

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
    fun testIsEntityWithValidEntityAnnotation() {
        val psiClass = mock<PsiClass>()
        val entityAnno = createMockAnnotation("jakarta.persistence.Entity")
        whenever(psiClass.annotations).thenReturn(arrayOf(entityAnno))

        assertTrue(EntityScanner.isEntity(psiClass))
    }

    @Test
    fun testIsEntityReturnsFalseForInterfaceOrEnum() {
        val psiInterface = mock<PsiClass>()
        whenever(psiInterface.isInterface).thenReturn(true)
        assertFalse(EntityScanner.isEntity(psiInterface))
    }

    @Test
    fun testScanEntityWithTableAndFields() {
        val psiClass = mock<PsiClass>()
        whenever(psiClass.name).thenReturn("User")
        val entityAnno = createMockAnnotation("jakarta.persistence.Entity")
        val tableAnno = createMockAnnotation("jakarta.persistence.Table", mapOf("name" to "users"))
        whenever(psiClass.annotations).thenReturn(arrayOf(entityAnno, tableAnno))

        // Id field
        val idField = mock<PsiField>()
        whenever(idField.name).thenReturn("id")
        val idType = mock<PsiType>()
        whenever(idType.presentableText).thenReturn("UUID")
        whenever(idField.type).thenReturn(idType)
        val idAnno = createMockAnnotation("jakarta.persistence.Id")
        whenever(idField.annotations).thenReturn(arrayOf(idAnno))

        // Username field
        val usernameField = mock<PsiField>()
        whenever(usernameField.name).thenReturn("username")
        val stringType = mock<PsiType>()
        whenever(stringType.presentableText).thenReturn("String")
        whenever(usernameField.type).thenReturn(stringType)
        val columnAnno = createMockAnnotation("jakarta.persistence.Column", mapOf("name" to "user_name", "nullable" to false, "unique" to true))
        whenever(usernameField.annotations).thenReturn(arrayOf(columnAnno))

        // Transient field
        val transientField = mock<PsiField>()
        whenever(transientField.name).thenReturn("tempToken")
        whenever(transientField.type).thenReturn(stringType)
        whenever(transientField.hasModifierProperty(PsiModifier.TRANSIENT)).thenReturn(true)
        whenever(transientField.annotations).thenReturn(emptyArray())

        whenever(psiClass.fields).thenReturn(arrayOf(idField, usernameField, transientField))

        val result = EntityScanner.scanEntity(psiClass)

        assertNotNull(result)
        assertEquals("User", result!!.name)
        assertEquals("users", result.tableName)
        assertEquals(2, result.fields.size)

        val idModel = result.fields.first { it.name == "id" }
        assertEquals("UUID", idModel.type)
        assertTrue(idModel.isPrimaryKey)

        val usernameModel = result.fields.first { it.name == "username" }
        assertEquals("String", usernameModel.type)
        assertEquals("user_name", usernameModel.columnName)
        assertFalse(usernameModel.isPrimaryKey)
        assertFalse(usernameModel.nullable)
        assertTrue(usernameModel.unique)
    }

    @Test
    fun testScanEntityDefaultTableName() {
        val psiClass = mock<PsiClass>()
        whenever(psiClass.name).thenReturn("UserProfile")
        val entityAnno = createMockAnnotation("javax.persistence.Entity")
        whenever(psiClass.annotations).thenReturn(arrayOf(entityAnno))
        whenever(psiClass.fields).thenReturn(emptyArray())

        val result = EntityScanner.scanEntity(psiClass)
        assertNotNull(result)
        assertEquals("UserProfile", result!!.name)
        assertEquals("user_profile", result.tableName)
    }
}
