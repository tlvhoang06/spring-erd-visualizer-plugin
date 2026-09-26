package com.github.tlvhoang06.springerdvisualizer.renderer

import com.github.tlvhoang06.springerdvisualizer.layout.ErdLayoutEngine
import com.github.tlvhoang06.springerdvisualizer.layout.NodeBounds
import com.github.tlvhoang06.springerdvisualizer.model.EntityModel
import com.github.tlvhoang06.springerdvisualizer.model.ErdGraphModel
import com.github.tlvhoang06.springerdvisualizer.model.FieldModel
import com.github.tlvhoang06.springerdvisualizer.model.RelationshipModel
import com.github.tlvhoang06.springerdvisualizer.model.RelationshipType
import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Color
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
    private var selectedNodeName: String? = null
    private var hoveredNodeName: String? = null

    // Theme-aware color palette
    private val cardBgColor = JBColor.namedColor("Panel.background", JBColor(Color(252, 252, 252), Color(43, 45, 48)))
    private val headerBgColor = JBColor.namedColor("TableHeader.background", JBColor(Color(242, 244, 247), Color(48, 50, 54)))
    private val borderColor = JBColor.namedColor("Component.borderColor", JBColor(Color(205, 210, 215), Color(75, 78, 82)))
    private val selectedBorderColor = JBColor.namedColor("Focus.borderColor", JBColor(Color(50, 130, 230), Color(65, 145, 245)))
    private val separatorColor = JBColor.namedColor("Separator.separatorColor", JBColor(Color(225, 228, 232), Color(62, 65, 68)))

    private val primaryTextColor = JBColor.namedColor("Label.foreground", JBColor(Color(30, 30, 30), Color(225, 225, 225)))
    private val mutedTextColor = JBColor.namedColor("Label.infoForeground", JBColor(Color(120, 120, 120), Color(145, 145, 145)))
    private val pkAccentColor = JBColor(Color(180, 120, 0), Color(240, 180, 40))

    private val lineNeutralColor = JBColor.namedColor("Component.borderColor", JBColor(Color(160, 168, 178), Color(95, 105, 115)))
    private val lineHighlightColor = JBColor.namedColor("Link.foreground", JBColor(Color(45, 120, 230), Color(80, 170, 255)))

    init {
        isFocusable = true
        background = cardBgColor

        val mouseHandler = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                lastMousePoint = e.point
                val modelPoint = screenToModel(e.point)

                val clicked = nodes.values.firstOrNull { it.bounds.contains(modelPoint) }
                if (clicked != null) {
                    draggedNode = clicked
                    selectedNodeName = clicked.entity.name
                    dragOffset = Point(modelPoint.x - clicked.bounds.x, modelPoint.y - clicked.bounds.y)
                } else {
                    selectedNodeName = null
                }
                repaint()
            }

            override fun mouseMoved(e: MouseEvent) {
                val modelPoint = screenToModel(e.point)
                val hovered = nodes.values.firstOrNull { it.bounds.contains(modelPoint) }
                val newHoverName = hovered?.entity?.name
                if (newHoverName != hoveredNodeName) {
                    hoveredNodeName = newHoverName
                    repaint()
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
                val newScale = (zoomScale * factor).coerceIn(0.15, 4.0)

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
        this.selectedNodeName = null
        this.hoveredNodeName = null
        fitToScreen()
    }

    fun zoomIn() {
        zoomScale = (zoomScale * 1.2).coerceAtMost(4.0)
        repaint()
    }

    fun zoomOut() {
        zoomScale = (zoomScale / 1.2).coerceAtLeast(0.15)
        repaint()
    }

    fun resetView() {
        zoomScale = 1.0
        panX = 30.0
        panY = 30.0
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

        val panelW = if (width > 0) width.toDouble() else 800.0
        val panelH = if (height > 0) height.toDouble() else 600.0

        val padding = 50.0
        val scaleX = (panelW - padding * 2) / contentWidth
        val scaleY = (panelH - padding * 2) / contentHeight

        zoomScale = Math.min(scaleX, scaleY).coerceIn(0.2, 1.5)
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

        // Subtle dot grid background
        drawGridBackground(g2)

        g2.translate(panX, panY)
        g2.scale(zoomScale, zoomScale)

        if (nodes.isEmpty()) {
            drawEmptyState(g2)
            return
        }

        val activeEntity = selectedNodeName ?: hoveredNodeName

        // 1. Draw relationships
        val deduplicatedRels = graphModel.deduplicatedRelationships()
        for (rel in deduplicatedRels) {
            val isHighlighted = activeEntity != null && (rel.sourceEntity == activeEntity || rel.targetEntity == activeEntity)
            drawRelationship(g2, rel, isHighlighted, activeEntity != null && !isHighlighted)
        }

        // 2. Draw entity nodes
        for (node in nodes.values) {
            val isSelected = node.entity.name == selectedNodeName
            val isHovered = node.entity.name == hoveredNodeName
            val isConnected = activeEntity != null && deduplicatedRels.any {
                (it.sourceEntity == activeEntity && it.targetEntity == node.entity.name) ||
                (it.targetEntity == activeEntity && it.sourceEntity == node.entity.name)
            }
            val isDimmed = activeEntity != null && !isSelected && !isHovered && !isConnected

            drawEntityCard(g2, node, isSelected || isHovered, isDimmed)
        }
    }

    private fun drawGridBackground(g2: Graphics2D) {
        val dotColor = JBColor.namedColor("Component.borderColor", JBColor(Color(230, 230, 230), Color(60, 60, 60)))
        g2.color = dotColor
        val gridSize = (30 * zoomScale).toInt().coerceAtLeast(15)

        val startX = (panX % gridSize).toInt()
        val startY = (panY % gridSize).toInt()

        var x = startX
        while (x < width) {
            var y = startY
            while (y < height) {
                g2.fillRect(x, y, 2, 2)
                y += gridSize
            }
            x += gridSize
        }
    }

    private fun drawEmptyState(g2: Graphics2D) {
        g2.color = mutedTextColor
        g2.font = Font("Dialog", Font.BOLD, 14)
        g2.drawString("No JPA entities detected. Open a Spring Boot/JPA project and click 'Generate ERD'.", 40, 80)
    }

    private fun drawEntityCard(g2: Graphics2D, node: NodeBounds, isHighlighted: Boolean, isDimmed: Boolean) {
        val r = node.bounds
        val entity = node.entity

        val currentBorderColor = if (isHighlighted) selectedBorderColor else borderColor
        val currentTextColor = if (isDimmed) mutedTextColor else primaryTextColor

        // Card body background
        g2.color = cardBgColor
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6)

        // Header background fill
        g2.color = headerBgColor
        g2.fillRoundRect(r.x, r.y, r.width, ErdLayoutEngine.HEADER_HEIGHT, 6, 6)
        g2.fillRect(r.x, r.y + ErdLayoutEngine.HEADER_HEIGHT - 6, r.width, 6)

        // Card outer border
        g2.color = currentBorderColor
        g2.stroke = BasicStroke(if (isHighlighted) 2.0f else 1.0f)
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 6, 6)

        // Entity Name (Primary visual element inside header)
        g2.color = currentTextColor
        g2.font = Font("Dialog", Font.BOLD, 12)
        g2.drawString(entity.name, r.x + 12, r.y + 18)

        // Table Name (Secondary muted element inside header)
        if (!entity.tableName.isNullOrBlank()) {
            g2.color = mutedTextColor
            g2.font = Font("Dialog", Font.PLAIN, 10)
            g2.drawString(entity.tableName, r.x + 12, r.y + 33)
        }

        // Section Separator 1 (Between Header and Attributes)
        g2.color = separatorColor
        g2.stroke = BasicStroke(1.0f)
        val headerSepY = r.y + ErdLayoutEngine.HEADER_HEIGHT
        g2.drawLine(r.x, headerSepY, r.x + r.width, headerSepY)

        val pkFields = entity.fields.filter { it.isPrimaryKey }
        val normalFields = entity.fields.filter { !it.isPrimaryKey }

        var currentY = headerSepY + 16

        // PK Section
        if (pkFields.isNotEmpty()) {
            for (field in pkFields) {
                drawFieldRow(g2, r, field, currentY, isPk = true, isDimmed)
                currentY += ErdLayoutEngine.ROW_HEIGHT
            }
        }

        // Section Separator 2 (Between PK section and Normal fields)
        if (pkFields.isNotEmpty() && normalFields.isNotEmpty()) {
            val pkSepY = currentY - 4
            g2.color = separatorColor
            g2.stroke = BasicStroke(1.0f)
            g2.drawLine(r.x, pkSepY, r.x + r.width, pkSepY)
            currentY += ErdLayoutEngine.SEPARATOR_GAP
        }

        // Normal Fields Section
        for (field in normalFields) {
            drawFieldRow(g2, r, field, currentY, isPk = false, isDimmed)
            currentY += ErdLayoutEngine.ROW_HEIGHT
        }
    }

    private fun drawFieldRow(g2: Graphics2D, r: Rectangle, field: FieldModel, yOffset: Int, isPk: Boolean, isDimmed: Boolean) {
        val fieldTextColor = if (isDimmed) mutedTextColor else primaryTextColor

        if (isPk) {
            // Restrained PK badge/text
            g2.color = pkAccentColor
            g2.font = Font("Dialog", Font.BOLD, 9)
            g2.drawString("PK", r.x + 10, yOffset)

            g2.font = Font("Dialog", Font.BOLD, 11)
            g2.color = fieldTextColor
        } else {
            g2.font = Font("Dialog", Font.PLAIN, 11)
            g2.color = fieldTextColor
        }

        g2.drawString(field.name, r.x + 34, yOffset)

        // Datatype = muted secondary text, right-aligned
        g2.color = mutedTextColor
        g2.font = Font("Dialog", Font.PLAIN, 11)
        val typeStr = field.type
        val fm = g2.fontMetrics
        val typeWidth = fm.stringWidth(typeStr)
        g2.drawString(typeStr, r.x + r.width - typeWidth - 12, yOffset)
    }

    private fun drawRelationship(g2: Graphics2D, rel: RelationshipModel, isHighlighted: Boolean, isDimmed: Boolean) {
        val sourceNode = nodes[rel.sourceEntity] ?: return
        val targetNode = nodes[rel.targetEntity] ?: return

        val sR = sourceNode.bounds
        val tR = targetNode.bounds

        // Self-referencing loop
        if (rel.sourceEntity == rel.targetEntity) {
            val arcX = sR.x + sR.width - 20
            val arcY = sR.y - 20
            g2.color = if (isHighlighted) lineHighlightColor else lineNeutralColor
            g2.stroke = BasicStroke(if (isHighlighted) 2.0f else 1.2f)
            g2.drawArc(arcX, arcY, 40, 40, 0, 270)
            return
        }

        val c1 = Point(sR.x + sR.width / 2, sR.y + sR.height / 2)
        val c2 = Point(tR.x + tR.width / 2, tR.y + tR.height / 2)

        // Calculate boundary intersection anchor points
        val p1 = getPerimeterIntersection(sR, c2)
        val p2 = getPerimeterIntersection(tR, c1)

        val strokeWidth = if (isHighlighted) 2.2f else 1.2f
        val lineColor = when {
            isHighlighted -> lineHighlightColor
            isDimmed -> JBColor.namedColor("Component.borderColor", Color(100, 100, 100))
            else -> lineNeutralColor
        }

        g2.color = lineColor
        g2.stroke = BasicStroke(strokeWidth)
        g2.drawLine(p1.x, p1.y, p2.x, p2.y)

        // Endpoint Cardinality Badges
        val (srcLabel, tgtLabel) = when (rel.type) {
            RelationshipType.ONE_TO_ONE -> Pair("1", "1")
            RelationshipType.ONE_TO_MANY -> Pair("1", "N")
            RelationshipType.MANY_TO_ONE -> Pair("N", "1")
            RelationshipType.MANY_TO_MANY -> Pair("N", "M")
        }

        drawCardinalityBadge(g2, p1, p2, srcLabel, isHighlighted)
        drawCardinalityBadge(g2, p2, p1, tgtLabel, isHighlighted)
    }

    private fun drawCardinalityBadge(g2: Graphics2D, endPoint: Point, farPoint: Point, label: String, isHighlighted: Boolean) {
        val dx = farPoint.x - endPoint.x
        val dy = farPoint.y - endPoint.y
        val dist = Math.hypot(dx.toDouble(), dy.toDouble())
        if (dist <= 0.0) return

        val offset = 18.0
        val badgeX = (endPoint.x + (dx / dist) * offset).toInt()
        val badgeY = (endPoint.y + (dy / dist) * offset).toInt()

        val badgeBg = if (isHighlighted) {
            lineHighlightColor
        } else {
            cardBgColor
        }

        val badgeBorder = if (isHighlighted) lineHighlightColor else borderColor
        val badgeTextColor = if (isHighlighted) Color.WHITE else primaryTextColor

        g2.color = badgeBg
        g2.fillOval(badgeX - 8, badgeY - 8, 16, 16)

        g2.color = badgeBorder
        g2.stroke = BasicStroke(1.0f)
        g2.drawOval(badgeX - 8, badgeY - 8, 16, 16)

        g2.color = badgeTextColor
        g2.font = Font("Dialog", Font.BOLD, 9)
        val fm = g2.fontMetrics
        val lw = fm.stringWidth(label)
        g2.drawString(label, badgeX - lw / 2, badgeY + 4)
    }

    private fun getPerimeterIntersection(rect: Rectangle, targetPoint: Point): Point {
        val cx = rect.x + rect.width / 2.0
        val cy = rect.y + rect.height / 2.0
        val dx = targetPoint.x - cx
        val dy = targetPoint.y - cy

        if (dx == 0.0 && dy == 0.0) return Point(cx.toInt(), cy.toInt())

        val scaleX = (rect.width / 2.0) / Math.abs(dx)
        val scaleY = (rect.height / 2.0) / Math.abs(dy)
        val scale = Math.min(scaleX, scaleY)

        return Point((cx + dx * scale).toInt(), (cy + dy * scale).toInt())
    }
}
