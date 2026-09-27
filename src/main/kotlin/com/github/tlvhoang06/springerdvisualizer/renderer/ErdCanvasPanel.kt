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
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
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

    var filterQuery: String = ""
        set(value) {
            field = value.trim().lowercase()
            repaint()
        }

    // High-contrast Pure Dark color palette
    private val canvasBgColor = JBColor(Color(248, 250, 252), Color(18, 18, 18)) // Pure Dark Black (#121212)
    private val cardBgColor = JBColor(Color(255, 255, 255), Color(28, 30, 36))  // Pure White / Deep Slate Card (#1C1E24)
    private val headerBgColor = JBColor(Color(241, 245, 249), Color(20, 22, 28)) // Header box (#14161C)
    private val borderColor = JBColor(Color(203, 213, 225), Color(55, 60, 72)) // Subtle Card Border (#373C48)
    private val selectedBorderColor = JBColor(Color(37, 99, 235), Color(56, 189, 248)) // Electric Blue / Sky Blue 400
    private val separatorColor = JBColor(Color(226, 232, 240), Color(45, 49, 60)) // Row divider
    private val pkBoundarySeparatorColor = JBColor(Color(245, 158, 11), Color(217, 119, 6)) // Amber separator line

    private val primaryTextColor = JBColor(Color(15, 23, 42), Color(248, 250, 252)) // Crisp Slate 900 / Crisp White 50
    private val secondaryTextColor = JBColor(Color(100, 116, 139), Color(148, 163, 184)) // Slate 500 / Slate 400
    private val typeTextColor = JBColor(Color(13, 148, 136), Color(56, 189, 248)) // Teal 600 / Sky Blue 400 (Vibrant Type Syntax)
    private val dimmedTextColor = JBColor(Color(148, 163, 184), Color(100, 116, 139)) // Dimmed Slate

    // PK Badge Pill (Vibrant Gold/Amber)
    private val pkBadgeBg = JBColor(Color(254, 243, 199), Color(217, 119, 6))
    private val pkBadgeText = JBColor(Color(180, 83, 9), Color(255, 251, 235))

    // Multi-color header accent bar palette for visual entity identification
    private val headerAccentPalette = listOf(
        JBColor(Color(37, 99, 235), Color(59, 130, 246)),  // Electric Blue
        JBColor(Color(16, 185, 129), Color(52, 211, 153)), // Emerald Green
        JBColor(Color(139, 92, 246), Color(167, 139, 250)),// Violet Purple
        JBColor(Color(249, 115, 22), Color(251, 146, 60)), // Coral Orange
        JBColor(Color(6, 182, 212), Color(45, 212, 191)),  // Cyan Teal
        JBColor(Color(236, 72, 153), Color(244, 114, 182))  // Pink Rose
    )

    private val lineNeutralColor = JBColor(Color(148, 163, 184), Color(100, 116, 139))
    private val lineHighlightColor = JBColor(Color(37, 99, 235), Color(56, 189, 248))

    // Cardinality Badges (Vibrant Solid Pill Circles)
    private val badge1BgColor = JBColor(Color(37, 99, 235), Color(37, 99, 235))
    private val badge1BorderColor = JBColor(Color(29, 78, 216), Color(96, 165, 250))
    private val badgeManyBgColor = JBColor(Color(124, 58, 237), Color(139, 92, 246))
    private val badgeManyBorderColor = JBColor(Color(109, 40, 217), Color(192, 132, 252))

    init {
        isFocusable = true
        background = canvasBgColor

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

    fun renderCanvas(g2: Graphics2D) {
        paintComponent(g2)
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
        val query = filterQuery
        val isFiltering = query.isNotEmpty()

        // 1. Draw relationships
        val deduplicatedRels = graphModel.deduplicatedRelationships()
        for (rel in deduplicatedRels) {
            val isHighlighted = activeEntity != null && (rel.sourceEntity == activeEntity || rel.targetEntity == activeEntity)
            drawRelationship(g2, rel, isHighlighted, activeEntity != null && !isHighlighted)
        }

        // 2. Draw entity nodes
        for (node in nodes.values) {
            val matchesFilter = !isFiltering || node.entity.name.lowercase().contains(query) ||
                    (!node.entity.tableName.isNullOrBlank() && node.entity.tableName!!.lowercase().contains(query)) ||
                    node.entity.fields.any { it.name.lowercase().contains(query) || it.type.lowercase().contains(query) }

            val isSelected = node.entity.name == selectedNodeName
            val isHovered = node.entity.name == hoveredNodeName
            val isConnected = activeEntity != null && deduplicatedRels.any {
                (it.sourceEntity == activeEntity && it.targetEntity == node.entity.name) ||
                (it.targetEntity == activeEntity && it.sourceEntity == node.entity.name)
            }
            val isDimmed = (activeEntity != null && !isSelected && !isHovered && !isConnected) || (isFiltering && !matchesFilter)

            drawEntityCard(g2, node, isSelected || isHovered || (isFiltering && matchesFilter), isDimmed)
        }
    }

    private fun drawGridBackground(g2: Graphics2D) {
        g2.color = canvasBgColor
        g2.fillRect(0, 0, width, height)

        val dotColor = JBColor(Color(203, 213, 225), Color(40, 40, 40))
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
        g2.color = secondaryTextColor
        g2.font = Font("Dialog", Font.BOLD, 14)
        g2.drawString("No JPA entities detected. Open a Spring Boot/JPA project and click 'Generate ERD'.", 40, 80)
    }

    private fun drawEntityCard(g2: Graphics2D, node: NodeBounds, isHighlighted: Boolean, isDimmed: Boolean) {
        val r = node.bounds
        val entity = node.entity

        val currentBorderColor = if (isHighlighted) selectedBorderColor else borderColor
        val currentTextColor = if (isDimmed) dimmedTextColor else primaryTextColor
        val currentSecondaryTextColor = if (isDimmed) dimmedTextColor else secondaryTextColor
        val cornerRadius = 8.0f

        val originalComposite = g2.composite
        if (isDimmed) {
            g2.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.45f)
        }

        try {
            // 0. Card drop shadow
            g2.color = JBColor(Color(0, 0, 0, 15), Color(0, 0, 0, 90))
            g2.fill(RoundRectangle2D.Float(r.x.toFloat() + 2f, r.y.toFloat() + 2f, r.width.toFloat(), r.height.toFloat(), cornerRadius, cornerRadius))

            // 1. Card body background fill
            g2.color = cardBgColor
            g2.fill(RoundRectangle2D.Float(r.x.toFloat(), r.y.toFloat(), r.width.toFloat(), r.height.toFloat(), cornerRadius, cornerRadius))

            // 2. Header background box fill
            val headerArea = Area(RoundRectangle2D.Float(r.x.toFloat(), r.y.toFloat(), r.width.toFloat(), r.height.toFloat(), cornerRadius, cornerRadius))
            val headerClip = Rectangle2D.Float(r.x.toFloat(), r.y.toFloat(), r.width.toFloat(), ErdLayoutEngine.HEADER_HEIGHT.toFloat())
            headerArea.intersect(Area(headerClip))

            g2.color = headerBgColor
            g2.fill(headerArea)

            // 3. Header top accent bar (Grouped by Package/Module!)
            val accentArea = Area(RoundRectangle2D.Float(r.x.toFloat(), r.y.toFloat(), r.width.toFloat(), r.height.toFloat(), cornerRadius, cornerRadius))
            val accentClip = Rectangle2D.Float(r.x.toFloat(), r.y.toFloat(), r.width.toFloat(), 4.0f)
            accentArea.intersect(Area(accentClip))

            val groupKey = if (entity.packageName.isNotBlank()) entity.packageName else entity.name
            val accentIndex = Math.abs(groupKey.hashCode()) % headerAccentPalette.size
            val entityAccentColor = if (isHighlighted) selectedBorderColor else headerAccentPalette[accentIndex]

            g2.color = entityAccentColor
            g2.fill(accentArea)

            // 4. Header bottom separator line
            g2.color = separatorColor
            g2.stroke = BasicStroke(1.0f)
            val headerSepY = r.y + ErdLayoutEngine.HEADER_HEIGHT
            g2.drawLine(r.x, headerSepY, r.x + r.width, headerSepY)

            // 5. Entity Name & Table Name inside header
            g2.color = currentTextColor
            g2.font = Font("Dialog", Font.BOLD, 13)
            g2.drawString(entity.name, r.x + 12, r.y + 20)

            if (!entity.tableName.isNullOrBlank()) {
                g2.color = currentSecondaryTextColor
                g2.font = Font("Dialog", Font.ITALIC, 10)
                g2.drawString("(${entity.tableName})", r.x + 12, r.y + 36)
            }

            // 6. Draw field rows with per-row horizontal separator lines
            val pkFields = entity.fields.filter { it.isPrimaryKey }
            val normalFields = entity.fields.filter { !it.isPrimaryKey }
            val allFields = pkFields + normalFields

            for (i in allFields.indices) {
                val field = allFields[i]
                val rowTop = headerSepY + i * ErdLayoutEngine.ROW_HEIGHT
                val yText = rowTop + 16

                drawFieldRow(g2, r, field, yText, isDimmed)

                // Horizontal divider line below EVERY field row (except last)
                val isLastField = (i == allFields.size - 1)
                if (!isLastField) {
                    val lineY = rowTop + ErdLayoutEngine.ROW_HEIGHT
                    val isPkBoundary = field.isPrimaryKey && (i + 1 < allFields.size && !allFields[i + 1].isPrimaryKey)
                    g2.color = if (isPkBoundary) pkBoundarySeparatorColor else separatorColor
                    g2.stroke = BasicStroke(if (isPkBoundary) 1.2f else 1.0f)
                    g2.drawLine(r.x, lineY, r.x + r.width, lineY)
                }
            }

            // 7. Outer card border
            g2.color = currentBorderColor
            g2.stroke = BasicStroke(if (isHighlighted) 2.0f else 1.0f)
            g2.draw(RoundRectangle2D.Float(r.x.toFloat(), r.y.toFloat(), r.width.toFloat(), r.height.toFloat(), cornerRadius, cornerRadius))
        } finally {
            g2.composite = originalComposite
        }
    }

    private fun drawFieldRow(g2: Graphics2D, r: Rectangle, field: FieldModel, yOffset: Int, isDimmed: Boolean) {
        val fieldTextColor = if (isDimmed) dimmedTextColor else primaryTextColor
        val fieldTypeColor = if (isDimmed) dimmedTextColor else typeTextColor

        if (field.isPrimaryKey) {
            // Draw Gold Pill Badge for PK
            val badgeW = 20f
            val badgeH = 13f
            val badgeX = r.x + 8f
            val badgeY = (yOffset - 10).toFloat()

            g2.color = if (isDimmed) dimmedTextColor else pkBadgeBg
            g2.fill(RoundRectangle2D.Float(badgeX, badgeY, badgeW, badgeH, 4f, 4f))

            g2.font = Font("Dialog", Font.BOLD, 9)
            g2.color = if (isDimmed) dimmedTextColor else pkBadgeText
            val fmPk = g2.fontMetrics
            val pkW = fmPk.stringWidth("PK")
            g2.drawString("PK", (badgeX + (badgeW - pkW) / 2).toInt(), yOffset)

            g2.font = Font("Dialog", Font.BOLD, 11)
            g2.color = fieldTextColor
            g2.drawString(field.name, r.x + 34, yOffset)
        } else {
            g2.font = Font("Dialog", Font.PLAIN, 11)
            g2.color = fieldTextColor
            g2.drawString(field.name, r.x + 14, yOffset)
        }

        // Datatype = Teal / Sky Blue 400, right-aligned
        g2.color = fieldTypeColor
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

        // Determine orthogonal anchor points
        val (p1, p2) = calculateOrthogonalAnchors(sR, tR)

        val strokeWidth = if (isHighlighted) 2.2f else 1.2f
        val lineColor = when {
            isHighlighted -> lineHighlightColor
            isDimmed -> JBColor(Color(80, 85, 95), Color(60, 65, 75))
            else -> lineNeutralColor
        }

        g2.color = lineColor
        g2.stroke = BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

        // Draw orthogonal path
        val path = java.awt.geom.Path2D.Float()
        path.moveTo(p1.x.toDouble(), p1.y.toDouble())

        val midX = (p1.x + p2.x) / 2
        val midY = (p1.y + p2.y) / 2

        val isHorizontal = Math.abs(p1.x - p2.x) >= Math.abs(p1.y - p2.y)

        if (isHorizontal) {
            path.lineTo(midX.toDouble(), p1.y.toDouble())
            path.lineTo(midX.toDouble(), p2.y.toDouble())
            path.lineTo(p2.x.toDouble(), p2.y.toDouble())
        } else {
            path.lineTo(p1.x.toDouble(), midY.toDouble())
            path.lineTo(p2.x.toDouble(), midY.toDouble())
            path.lineTo(p2.x.toDouble(), p2.y.toDouble())
        }

        g2.draw(path)

        // Endpoint Cardinality Badges
        val (srcLabel, tgtLabel) = when (rel.type) {
            RelationshipType.ONE_TO_ONE -> Pair("1", "1")
            RelationshipType.ONE_TO_MANY -> Pair("1", "N")
            RelationshipType.MANY_TO_ONE -> Pair("N", "1")
            RelationshipType.MANY_TO_MANY -> Pair("N", "M")
        }

        val far1 = if (isHorizontal) Point(midX, p1.y) else Point(p1.x, midY)
        val far2 = if (isHorizontal) Point(midX, p2.y) else Point(p2.x, midY)

        drawCardinalityBadge(g2, p1, far1, srcLabel, isHighlighted)
        drawCardinalityBadge(g2, p2, far2, tgtLabel, isHighlighted)
    }

    private fun calculateOrthogonalAnchors(sR: Rectangle, tR: Rectangle): Pair<Point, Point> {
        val sCenter = Point(sR.x + sR.width / 2, sR.y + sR.height / 2)
        val tCenter = Point(tR.x + tR.width / 2, tR.y + tR.height / 2)

        return if (Math.abs(sCenter.x - tCenter.x) >= Math.abs(sCenter.y - tCenter.y)) {
            // Horizontal dominant relation: exit left/right sides
            if (sCenter.x < tCenter.x) {
                Pair(Point(sR.x + sR.width, sCenter.y), Point(tR.x, tCenter.y))
            } else {
                Pair(Point(sR.x, sCenter.y), Point(tR.x + tR.width, tCenter.y))
            }
        } else {
            // Vertical dominant relation: exit top/bottom sides
            if (sCenter.y < tCenter.y) {
                Pair(Point(sCenter.x, sR.y + sR.height), Point(tCenter.x, tR.y))
            } else {
                Pair(Point(sCenter.x, sR.y), Point(tCenter.x, tR.y + tR.height))
            }
        }
    }

    private fun drawCardinalityBadge(g2: Graphics2D, endPoint: Point, farPoint: Point, label: String, isHighlighted: Boolean) {
        val dx = farPoint.x - endPoint.x
        val dy = farPoint.y - endPoint.y
        val dist = Math.hypot(dx.toDouble(), dy.toDouble())
        if (dist <= 0.0) return

        val offset = 26.0
        val badgeX = (endPoint.x + (dx / dist) * offset).toInt()
        val badgeY = (endPoint.y + (dy / dist) * offset).toInt()

        val isOne = (label == "1")
        val bg = if (isHighlighted) {
            lineHighlightColor
        } else if (isOne) {
            badge1BgColor
        } else {
            badgeManyBgColor
        }

        val border = if (isHighlighted) lineHighlightColor else if (isOne) badge1BorderColor else badgeManyBorderColor

        val size = 18
        val radius = size / 2

        // Subtle shadow
        g2.color = JBColor(Color(0, 0, 0, 40), Color(0, 0, 0, 80))
        g2.fillOval(badgeX - radius + 1, badgeY - radius + 1, size, size)

        // Circle fill
        g2.color = bg
        g2.fillOval(badgeX - radius, badgeY - radius, size, size)

        // Circle border
        g2.color = border
        g2.stroke = BasicStroke(1.0f)
        g2.drawOval(badgeX - radius, badgeY - radius, size, size)

        // Text label
        g2.color = Color.WHITE
        g2.font = Font("Dialog", Font.BOLD, 10)
        val fm = g2.fontMetrics
        val lw = fm.stringWidth(label)
        val fontAscent = fm.ascent
        val fontHeight = fm.height
        val textY = badgeY + (fontAscent - fontHeight / 2)
        g2.drawString(label, badgeX - lw / 2, textY)
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
