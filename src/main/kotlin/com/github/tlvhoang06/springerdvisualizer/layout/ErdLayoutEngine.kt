package com.github.tlvhoang06.springerdvisualizer.layout

import com.github.tlvhoang06.springerdvisualizer.model.EntityModel
import com.github.tlvhoang06.springerdvisualizer.model.ErdGraphModel
import java.awt.Point
import java.awt.Rectangle

data class NodeBounds(
    val entity: EntityModel,
    var bounds: Rectangle
)

object ErdLayoutEngine {

    const val CARD_WIDTH = 220
    const val HEADER_HEIGHT = 40
    const val ROW_HEIGHT = 22
    const val MIN_CARD_HEIGHT = 70
    const val H_GAP = 60
    const val V_GAP = 60

    fun calculateCardHeight(entity: EntityModel): Int {
        val fieldCount = entity.fields.size
        return HEADER_HEIGHT + Math.max(1, fieldCount) * ROW_HEIGHT + 15
    }

    fun layoutGraph(graph: ErdGraphModel): Map<String, NodeBounds> {
        val result = mutableMapOf<String, NodeBounds>()
        val entities = graph.entities
        if (entities.isEmpty()) return result

        val cols = Math.max(1, Math.ceil(Math.sqrt(entities.size.toDouble())).toInt())
        var currentX = 40
        var currentY = 40
        var maxRowHeight = 0

        for ((index, entity) in entities.withIndex()) {
            val height = calculateCardHeight(entity)
            val rect = Rectangle(currentX, currentY, CARD_WIDTH, height)
            result[entity.name] = NodeBounds(entity, rect)

            maxRowHeight = Math.max(maxRowHeight, height)

            if ((index + 1) % cols == 0) {
                currentX = 40
                currentY += maxRowHeight + V_GAP
                maxRowHeight = 0
            } else {
                currentX += CARD_WIDTH + H_GAP
            }
        }

        return result
    }
}
