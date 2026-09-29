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

    const val MIN_CARD_WIDTH = 230
    const val MAX_CARD_WIDTH = 380
    const val HEADER_HEIGHT = 46
    const val ROW_HEIGHT = 24
    const val MIN_CARD_HEIGHT = 70
    const val H_GAP = 160
    const val V_GAP = 120

    fun calculateCardWidth(entity: EntityModel): Int {
        var maxLen = entity.name.length + 4
        if (!entity.tableName.isNullOrBlank()) {
            maxLen = Math.max(maxLen, entity.tableName.length + 8)
        }
        for (field in entity.fields) {
            val lineLen = field.name.length + field.type.length + 8
            maxLen = Math.max(maxLen, lineLen)
        }
        val computedWidth = maxLen * 8 + 44
        return computedWidth.coerceIn(MIN_CARD_WIDTH, MAX_CARD_WIDTH)
    }

    fun calculateCardHeight(entity: EntityModel): Int {
        val totalFields = entity.fields.size
        val rowsCount = if (totalFields > 0) totalFields else 1
        return HEADER_HEIGHT + rowsCount * ROW_HEIGHT
    }

    fun layoutGraph(graph: ErdGraphModel): Map<String, NodeBounds> {
        val result = mutableMapOf<String, NodeBounds>()
        if (graph.entities.isEmpty()) return result

        // 1. Group entities by Package / Module
        val modules = graph.entities.groupBy { it.packageName.ifBlank { "default" } }

        // Degree map to detect hub nodes (high-degree entities)
        val degreeMap = mutableMapOf<String, Int>()
        for (rel in graph.deduplicatedRelationships()) {
            degreeMap[rel.sourceEntity] = (degreeMap[rel.sourceEntity] ?: 0) + 1
            degreeMap[rel.targetEntity] = (degreeMap[rel.targetEntity] ?: 0) + 1
        }

        // Create initial node bounds map
        val nodeMap = mutableMapOf<String, NodeBounds>()
        val nodePos = mutableMapOf<String, Point2D>()

        var clusterOffsetX = 60.0
        var clusterOffsetY = 60.0
        var maxClusterHeight = 0.0

        // 2. Initial placement by module clusters
        val moduleKeys = modules.keys.sorted()
        val colsPerGrid = Math.max(2, Math.ceil(Math.sqrt(moduleKeys.size.toDouble())).toInt())

        for ((mIndex, pkg) in moduleKeys.withIndex()) {
            val clusterEntities = modules[pkg] ?: continue
            val clusterCols = Math.max(1, Math.ceil(Math.sqrt(clusterEntities.size.toDouble())).toInt())

            var localX = clusterOffsetX
            var localY = clusterOffsetY
            var rowMaxH = 0.0

            for ((eIndex, entity) in clusterEntities.withIndex()) {
                val w = calculateCardWidth(entity)
                val h = calculateCardHeight(entity)
                val rect = Rectangle(localX.toInt(), localY.toInt(), w, h)

                val node = NodeBounds(entity, rect)
                nodeMap[entity.name] = node
                nodePos[entity.name] = Point2D(localX + w / 2.0, localY + h / 2.0)

                rowMaxH = Math.max(rowMaxH, h.toDouble())

                if ((eIndex + 1) % clusterCols == 0) {
                    localX = clusterOffsetX
                    localY += rowMaxH + V_GAP
                    rowMaxH = 0.0
                } else {
                    localX += w + H_GAP
                }
            }

            val clusterH = (localY + rowMaxH) - clusterOffsetY
            maxClusterHeight = Math.max(maxClusterHeight, clusterH)

            if ((mIndex + 1) % colsPerGrid == 0) {
                clusterOffsetX = 60.0
                clusterOffsetY += maxClusterHeight + 180.0
                maxClusterHeight = 0.0
            } else {
                clusterOffsetX += 550.0
            }
        }

        // 3. Force-Directed & Overlap Removal Iterations
        val iterations = 140
        val rels = graph.deduplicatedRelationships()

        for (iter in 0 until iterations) {
            // A. Calculate Module Centroids
            val moduleCentroids = mutableMapOf<String, Point2D>()
            for ((pkg, entityList) in modules) {
                var sumX = 0.0
                var sumY = 0.0
                for (e in entityList) {
                    val p = nodePos[e.name] ?: continue
                    sumX += p.x
                    sumY += p.y
                }
                moduleCentroids[pkg] = Point2D(sumX / entityList.size, sumY / entityList.size)
            }

            val forces = mutableMapOf<String, Point2D>()
            for (e in graph.entities) {
                forces[e.name] = Point2D(0.0, 0.0)
            }

            // B. Spring Attraction along relationships (Rest length 320px)
            for (rel in rels) {
                val p1 = nodePos[rel.sourceEntity] ?: continue
                val p2 = nodePos[rel.targetEntity] ?: continue

                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val dist = Math.hypot(dx, dy).coerceAtLeast(1.0)

                // Extra clearance force for Hub nodes (degree > 4)
                val srcDegree = degreeMap[rel.sourceEntity] ?: 1
                val tgtDegree = degreeMap[rel.targetEntity] ?: 1
                val isHubRel = srcDegree > 4 || tgtDegree > 4
                val targetRestLength = if (isHubRel) 380.0 else 320.0

                val force = (dist - targetRestLength) * 0.025

                val fx = (dx / dist) * force
                val fy = (dy / dist) * force

                val f1 = forces[rel.sourceEntity]!!
                f1.x += fx
                f1.y += fy

                val f2 = forces[rel.targetEntity]!!
                f2.x -= fx
                f2.y -= fy
            }

            // C. Module Cohesion Gravity (pull towards module centroid)
            for (e in graph.entities) {
                val pkg = e.packageName.ifBlank { "default" }
                val centroid = moduleCentroids[pkg] ?: continue
                val p = nodePos[e.name] ?: continue

                val dx = centroid.x - p.x
                val dy = centroid.y - p.y
                val f = forces[e.name]!!
                f.x += dx * 0.04
                f.y += dy * 0.04
            }

            // Apply forces
            val damping = (1.0 - iter.toDouble() / iterations).coerceIn(0.1, 0.8)
            for (e in graph.entities) {
                val p = nodePos[e.name]!!
                val f = forces[e.name]!!
                p.x += f.x * damping
                p.y += f.y * damping
            }

            // D. Strict Overlap & Minimum Gap Enforcement
            for (i in 0 until graph.entities.size) {
                val e1 = graph.entities[i]
                val n1 = nodeMap[e1.name]!!
                val p1 = nodePos[e1.name]!!

                for (j in i + 1 until graph.entities.size) {
                    val e2 = graph.entities[j]
                    val n2 = nodeMap[e2.name]!!
                    val p2 = nodePos[e2.name]!!

                    val minDistX = (n1.bounds.width + n2.bounds.width) / 2.0 + H_GAP
                    val minDistY = (n1.bounds.height + n2.bounds.height) / 2.0 + V_GAP

                    val dx = p2.x - p1.x
                    val dy = p2.y - p1.y

                    val overlapX = minDistX - Math.abs(dx)
                    val overlapY = minDistY - Math.abs(dy)

                    if (overlapX > 0 && overlapY > 0) {
                        // Push apart along axis of smaller overlap
                        if (overlapX < overlapY) {
                            val sign = if (dx >= 0) 1.0 else -1.0
                            val shift = (overlapX / 2.0) * sign
                            p1.x -= shift
                            p2.x += shift
                        } else {
                            val sign = if (dy >= 0) 1.0 else -1.0
                            val shift = (overlapY / 2.0) * sign
                            p1.y -= shift
                            p2.y += shift
                        }
                    }
                }
            }

            // Update rectangle bounds
            for (e in graph.entities) {
                val node = nodeMap[e.name]!!
                val p = nodePos[e.name]!!
                node.bounds.x = (p.x - node.bounds.width / 2.0).toInt()
                node.bounds.y = (p.y - node.bounds.height / 2.0).toInt()
            }
        }

        // 4. Normalize canvas starting top-left at (60, 60)
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        for (node in nodeMap.values) {
            minX = Math.min(minX, node.bounds.x)
            minY = Math.min(minY, node.bounds.y)
        }

        val shiftX = 60 - minX
        val shiftY = 60 - minY
        for (node in nodeMap.values) {
            node.bounds.x += shiftX
            node.bounds.y += shiftY
            // Snap to 10px grid
            node.bounds.x = (node.bounds.x / 10) * 10
            node.bounds.y = (node.bounds.y / 10) * 10
            result[node.entity.name] = node
        }

        return result
    }

    private data class Point2D(var x: Double, var y: Double)
}
