package com.github.tlvhoang06.springerdvisualizer.renderer

import com.github.tlvhoang06.springerdvisualizer.layout.ErdLayoutEngine
import com.github.tlvhoang06.springerdvisualizer.layout.NodeBounds
import com.github.tlvhoang06.springerdvisualizer.model.ErdGraphModel
import com.github.tlvhoang06.springerdvisualizer.model.RelationshipModel
import com.github.tlvhoang06.springerdvisualizer.model.RelationshipType
import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JPanel

class ErdCanvasPanel : JPanel() {

    var graphModel: ErdGraphModel = ErdGraphModel()
        private set

    private val nodes = mutableMapOf<String, NodeBounds>()

    private var zoomScale = 1.0
    private var panX = 0.0
    private var panY = 0.0

    private var lastMousePoint: Point? = null
    private var draggedNode: NodeBounds? = null
    private var dragOffset: Point = Point(0, 0)

    init {
        isFocusable = true
        background = JBColor.namedColor("Panel.background", Color(43, 43, 43))

        val mouseHandler = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                lastMousePoint = e.point
                val modelPoint = screenToModel(e.point)

                // Check if an entity node was clicked
                draggedNode = nodes.values.firstOrNull { it.bounds.contains(modelPoint) }
                if (draggedNode != null) {
                    dragOffset = Point(modelPoint.x - draggedNode!!.bounds.x, modelPoint.y - draggedNode!!.bounds.y)
                }
            }

            override fun mouseDragged(e: MouseEvent) {
                val currentPoint = e.point
                val lastPoint = lastMousePoint ?: currentPoint

                if (draggedNode != null) {
                    val modelPoint = screenToModel(currentPoint)
                    draggedNode!!.bounds.x = modelPoint.x - dragOffset.x
                    draggedNode!!.bounds.y = modelPoint.y - dragOffset.y
                    repaint()
                } else {
                    val dx = currentPoint.x - lastPoint.x
                    val dy = currentPoint.y - lastPoint.y
                    panX += dx
                    panY += dy
                    repaint()
                }
                lastMousePoint = currentPoint
            }

            override fun mouseReleased(e: MouseEvent) {
                draggedNode = null
                lastMousePoint = null
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                val factor = if (e.wheelRotation < 0) 1.1 else 0.9
                val newScale = (zoomScale * factor).coerceIn(0.2, 4.0)

                val mouse = e.point
                panX = mouse.x - (mouse.x - panX) * (newScale / zoomScale)
                panY = mouse.y - (mouse.y - panY) * (newScale / zoomScale)
                zoomScale = newScale

                repaint()
            }
        }

        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
        addMouseWheelListener(mouseHandler)
    }

    fun setGraph(graph: ErdGraphModel) {
        this.graphModel = graph
        this.nodes.clear()
        this.nodes.putAll(ErdLayoutEngine.layoutGraph(graph))
        resetView()
        repaint()
    }

    fun zoomIn() {
        zoomScale = (zoomScale * 1.2).coerceAtMost(4.0)
        repaint()
    }

    fun zoomOut() {
        zoomScale = (zoomScale / 1.2).coerceAtLeast(0.2)
        repaint()
    }

    fun resetView() {
        zoomScale = 1.0
        panX = 20.0
        panY = 20.0
        repaint()
    }

    fun fitToScreen() {
        if (nodes.isEmpty()) {
            resetView()
            return
        }

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE

        for (node in nodes.values) {
            minX = Math.min(minX, node.bounds.x)
            minY = Math.min(minY, node.bounds.y)
            maxX = Math.max(maxX, node.bounds.x + node.bounds.width)
            maxY = Math.max(maxY, node.bounds.y + node.bounds.height)
        }

        val contentWidth = (maxX - minX).toDouble()
        val contentHeight = (maxY - minY).toDouble()

        if (contentWidth <= 0 || contentHeight <= 0) {
            resetView()
            return
        }

        val padding = 40.0
        val scaleX = (width - padding * 2) / contentWidth
        val scaleY = (height - padding * 2) / contentHeight

        zoomScale = Math.min(scaleX, scaleY).coerceIn(0.3, 2.0)
        panX = padding - minX * zoomScale
        panY = padding - minY * zoomScale

        repaint()
    }

    private fun screenToModel(p: Point): Point {
        val mx = ((p.x - panX) / zoomScale).toInt()
        val my = ((p.y - panY) / zoomScale).toInt()
        return Point(mx, my)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g2.translate(panX, panY)
        g2.scale(zoomScale, zoomScale)

        if (nodes.isEmpty()) {
            drawEmptyState(g2)
            return
        }

        // 1. Draw relationships
        for (rel in graphModel.relationships) {
            drawRelationship(g2, rel)
        }

        // 2. Draw entity nodes
        for (node in nodes.values) {
            drawEntityCard(g2, node)
        }
    }

    private fun drawEmptyState(g2: Graphics2D) {
        g2.color = JBColor.GRAY
        g2.font = Font("Dialog", Font.BOLD, 16)
        g2.drawString("No JPA entities found. Open a Spring/JPA project and click 'Generate ERD'.", 50, 100)
    }

    private fun drawEntityCard(g2: Graphics2D, node: NodeBounds) {
        val r = node.bounds
        val entity = node.entity

        val headerBg = JBColor.namedColor("TableHeader.background", Color(53, 116, 240))
        val cardBg = JBColor.namedColor("Panel.background", Color(60, 63, 65))
        val borderColor = JBColor.namedColor("Component.borderColor", Color(85, 85, 85))
        val textColor = JBColor.namedColor("Label.foreground", Color(220, 220, 220))
        val pkColor = JBColor.namedColor("Link.foreground", Color(255, 170, 0))

        // Card background & shadow outline
        g2.color = cardBg
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10)
        g2.color = borderColor
        g2.stroke = BasicStroke(1.5f)
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10)

        // Header
        g2.color = headerBg
        g2.fillRoundRect(r.x, r.y, r.width, ErdLayoutEngine.HEADER_HEIGHT, 10, 10)
        g2.fillRect(r.x, r.y + ErdLayoutEngine.HEADER_HEIGHT - 5, r.width, 5)

        // Entity Title & Table Name
        g2.color = Color.WHITE
        g2.font = Font("Dialog", Font.BOLD, 13)
        g2.drawString(entity.name, r.x + 12, r.y + 18)

        g2.font = Font("Dialog", Font.ITALIC, 10)
        val tableText = if (entity.tableName.isNotBlank()) "table: ${entity.tableName}" else ""
        g2.drawString(tableText, r.x + 12, r.y + 32)

        // Fields
        var yOffset = r.y + ErdLayoutEngine.HEADER_HEIGHT + 18
        g2.font = Font("Dialog", Font.PLAIN, 11)

        for (field in entity.fields) {
            if (field.isPrimaryKey) {
                g2.color = pkColor
                g2.font = Font("Dialog", Font.BOLD, 10)
                g2.drawString("PK", r.x + 10, yOffset)
                g2.font = Font("Dialog", Font.BOLD, 11)
                g2.color = textColor
            } else {
                g2.font = Font("Dialog", Font.PLAIN, 11)
                g2.color = textColor
            }

            g2.drawString(field.name, r.x + 35, yOffset)

            // Field type right-aligned
            g2.color = JBColor.GRAY
            val typeStr = field.type
            val fm = g2.fontMetrics
            val typeWidth = fm.stringWidth(typeStr)
            g2.drawString(typeStr, r.x + r.width - typeWidth - 10, yOffset)

            yOffset += ErdLayoutEngine.ROW_HEIGHT
        }
    }

    private fun drawRelationship(g2: Graphics2D, rel: RelationshipModel) {
        val sourceNode = nodes[rel.sourceEntity] ?: return
        val targetNode = nodes[rel.targetEntity] ?: return

        val sR = sourceNode.bounds
        val tR = targetNode.bounds

        val p1 = Point(sR.x + sR.width / 2, sR.y + sR.height / 2)
        val p2 = Point(tR.x + tR.width / 2, tR.y + tR.height / 2)

        g2.color = JBColor.namedColor("Link.foreground", Color(100, 180, 255))
        g2.stroke = BasicStroke(2.0f)
        g2.drawLine(p1.x, p1.y, p2.x, p2.y)

        // Cardinality label
        val midX = (p1.x + p2.x) / 2
        val midY = (p1.y + p2.y) / 2

        val label = when (rel.type) {
            RelationshipType.ONE_TO_ONE -> "1 : 1"
            RelationshipType.ONE_TO_MANY -> "1 : N"
            RelationshipType.MANY_TO_ONE -> "N : 1"
            RelationshipType.MANY_TO_MANY -> "N : M"
        }

        g2.color = JBColor.namedColor("Panel.background", Color(40, 40, 40))
        g2.fillRect(midX - 18, midY - 10, 36, 18)
        g2.color = JBColor.LIGHT_GRAY
        g2.font = Font("Dialog", Font.BOLD, 10)
        g2.drawString(label, midX - 14, midY + 3)
    }
}
