package com.github.tlvhoang06.springerdvisualizer.layout

import com.github.tlvhoang06.springerdvisualizer.model.EntityModel
import com.github.tlvhoang06.springerdvisualizer.model.ErdGraphModel
import java.awt.Rectangle

data class NodeBounds(
    val entity: EntityModel,
    var bounds: Rectangle
)

object ErdLayoutEngine {

    const val MIN_CARD_WIDTH = 220
    const val MAX_CARD_WIDTH = 360
    const val HEADER_HEIGHT = 44
    const val ROW_HEIGHT = 20
    const val SEPARATOR_GAP = 6
    const val MIN_CARD_HEIGHT = 70
    const val H_GAP = 80
    const val V_GAP = 70

    fun calculateCardWidth(entity: EntityModel): Int {
        var maxLen = entity.name.length + 4
        if (!entity.tableName.isNullOrBlank()) {
            maxLen = Math.max(maxLen, entity.tableName.length + 8)
        }
        for (field in entity.fields) {
            val lineLen = field.name.length + field.type.length + 6
            maxLen = Math.max(maxLen, lineLen)
        }
        val computedWidth = maxLen * 8 + 40
        return computedWidth.coerceIn(MIN_CARD_WIDTH, MAX_CARD_WIDTH)
    }

    fun calculateCardHeight(entity: EntityModel): Int {
        val pkCount = entity.fields.count { it.isPrimaryKey }
        val normalCount = entity.fields.size - pkCount
        var height = HEADER_HEIGHT

        if (pkCount > 0) {
            height += SEPARATOR_GAP + pkCount * ROW_HEIGHT
        }
        if (normalCount > 0) {
            height += SEPARATOR_GAP + normalCount * ROW_HEIGHT
        }
        if (pkCount == 0 && normalCount == 0) {
            height += ROW_HEIGHT
        }

        return height + 10
    }

    fun layoutGraph(graph: ErdGraphModel): Map<String, NodeBounds> {
        val result = mutableMapOf<String, NodeBounds>()
        val entities = sortEntitiesByConnectivity(graph)
        if (entities.isEmpty()) return result

        val count = entities.size
        val cols = Math.max(2, Math.ceil(Math.sqrt(count.toDouble()) * 1.25).toInt())

        var currentX = 50
        var currentY = 50
        var maxRowHeight = 0

        for ((index, entity) in entities.withIndex()) {
            val width = calculateCardWidth(entity)
            val height = calculateCardHeight(entity)
            val rect = Rectangle(currentX, currentY, width, height)
            result[entity.name] = NodeBounds(entity, rect)

            maxRowHeight = Math.max(maxRowHeight, height)

            if ((index + 1) % cols == 0) {
                currentX = 50
                currentY += maxRowHeight + V_GAP
                maxRowHeight = 0
            } else {
                currentX += width + H_GAP
            }
        }

        return result
    }

    private fun sortEntitiesByConnectivity(graph: ErdGraphModel): List<EntityModel> {
        val degreeMap = mutableMapOf<String, Int>()

        for (rel in graph.deduplicatedRelationships()) {
            degreeMap[rel.sourceEntity] = (degreeMap[rel.sourceEntity] ?: 0) + 1
            degreeMap[rel.targetEntity] = (degreeMap[rel.targetEntity] ?: 0) + 1
        }

        return graph.entities.sortedByDescending { degreeMap[it.name] ?: 0 }
    }
}
