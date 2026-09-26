package com.github.tlvhoang06.springerdvisualizer.renderer

import com.github.tlvhoang06.springerdvisualizer.layout.ErdLayoutEngine
import com.github.tlvhoang06.springerdvisualizer.model.EntityModel
import com.github.tlvhoang06.springerdvisualizer.model.ErdGraphModel
import com.github.tlvhoang06.springerdvisualizer.model.FieldModel
import com.github.tlvhoang06.springerdvisualizer.model.RelationshipModel
import com.github.tlvhoang06.springerdvisualizer.model.RelationshipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErdCanvasPanelTest {

    @Test
    fun testErdLayoutEngineCalculation() {
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

        val layout = ErdLayoutEngine.layoutGraph(graph)

        assertEquals(2, layout.size)
        assertTrue(layout.containsKey("User"))
        assertTrue(layout.containsKey("Order"))

        val userBounds = layout["User"]!!.bounds
        val orderBounds = layout["Order"]!!.bounds

        assertEquals(ErdLayoutEngine.calculateCardWidth(userEntity), userBounds.width)
        assertTrue(userBounds.height > ErdLayoutEngine.MIN_CARD_HEIGHT)
    }

    @Test
    fun testErdCanvasPanelControls() {
        val canvas = ErdCanvasPanel()
        val userEntity = EntityModel("User", "users")
        val graph = ErdGraphModel(listOf(userEntity))

        canvas.setGraph(graph)
        assertEquals(graph, canvas.graphModel)

        canvas.zoomIn()
        canvas.zoomOut()
        canvas.resetView()
        canvas.fitToScreen()
        assertNotNull(canvas)
    }
}
