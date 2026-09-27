package com.github.tlvhoang06.springerdvisualizer.exporter

import com.github.tlvhoang06.springerdvisualizer.model.EntityModel
import com.github.tlvhoang06.springerdvisualizer.model.ErdGraphModel
import com.github.tlvhoang06.springerdvisualizer.model.FieldModel
import com.github.tlvhoang06.springerdvisualizer.model.RelationshipModel
import com.github.tlvhoang06.springerdvisualizer.model.RelationshipType
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidExporterTest {

    @Test
    fun testMermaidExportFormat() {
        val userEntity = EntityModel(
            name = "User",
            tableName = "users",
            fields = listOf(
                FieldModel("id", "UUID", "id", isPrimaryKey = true),
                FieldModel("username", "String", "username")
            )
        )

        val orderEntity = EntityModel(
            name = "Order",
            tableName = "orders",
            fields = listOf(
                FieldModel("id", "UUID", "id", isPrimaryKey = true)
            )
        )

        val rel = RelationshipModel("User", "Order", RelationshipType.ONE_TO_MANY, mappedBy = "user")
        val graph = ErdGraphModel(listOf(userEntity, orderEntity), listOf(rel))

        val mermaidText = MermaidExporter.export(graph)

        assertTrue(mermaidText.startsWith("erDiagram"))
        assertTrue(mermaidText.contains("User ||--|{ Order : \"\""))
        assertTrue(mermaidText.contains("User {"))
        assertTrue(mermaidText.contains("UUID id PK"))
        assertTrue(mermaidText.contains("String username"))
        assertTrue(mermaidText.contains("Order {"))
    }
}
