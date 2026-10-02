package com.github.tlvhoang06.springerdvisualizer.renderer

import com.github.tlvhoang06.springerdvisualizer.layout.ErdLayoutEngine
import com.github.tlvhoang06.springerdvisualizer.layout.NodeBounds
import com.github.tlvhoang06.springerdvisualizer.model.EntityModel
import com.github.tlvhoang06.springerdvisualizer.model.ErdGraphModel
import com.github.tlvhoang06.springerdvisualizer.model.FieldModel
import com.github.tlvhoang06.springerdvisualizer.model.RelationshipModel
import com.github.tlvhoang06.springerdvisualizer.model.RelationshipType
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
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
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities

class ErdCanvasPanel : JPanel() {

    var project: Project? = null

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

    val hiddenEntities = mutableSetOf<String>()

    fun resetHiddenEntities() {
        hiddenEntities.clear()
        repaint()
    }

    fun navigateToSource(entity: EntityModel, fieldName: String? = null) {
        val currentProject = project ?: return
        val fqName = if (entity.packageName.isNotBlank()) "${entity.packageName}.${entity.name}" else entity.name

        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runReadAction {
                val javaPsiFacade = JavaPsiFacade.getInstance(currentProject)
                val scope = GlobalSearchScope.projectScope(currentProject)
                val psiClass = javaPsiFacade.findClass(fqName, scope)
                    ?: javaPsiFacade.findClasses(entity.name, scope).firstOrNull()

                if (psiClass != null) {
                    ApplicationManager.getApplication().invokeLater {
                        if (fieldName != null) {
                            val field = psiClass.findFieldByName(fieldName, false)
                            field?.navigate(true) ?: psiClass.navigate(true)
                        } else {
                            psiClass.navigate(true)
                        }
                    }
                }
            }
        }
    }

    var showLegend: Boolean = true
        set(value) {
            field = value
            repaint()
        }

    private var hoveredLegendModule: String? = null
    private var selectedLegendModule: String? = null
    private var legendBounds: Rectangle? = null
    private val legendItemBounds = mutableMapOf<String, Rectangle>()

    var filterQuery: String = ""
        set(value) {
            field = value.trim().lowercase()
            repaint()
        }

    private val moduleColors = mutableMapOf<String, Color>()

    fun updateModuleColors() {
        moduleColors.clear()
        val sortedPkgs = graphModel.entities
            .map { it.packageName.ifBlank { "default" } }
            .distinct()
            .sorted()

        for ((index, pkg) in sortedPkgs.withIndex()) {
            val hue = ((index * 137.508f) % 360f) / 360f
            val color = Color.getHSBColor(hue, 0.75f, 0.90f)
            moduleColors[pkg] = color
        }
    }

    fun getModuleColor(pkgName: String): Color {
        val groupKey = pkgName.ifBlank { "default" }
        return moduleColors[groupKey] ?: Color.getHSBColor(0f, 0.75f, 0.85f)
    }

    fun formatPackageDisplayName(pkg: String): String {
        if (pkg.isBlank() || pkg == "default") return "default"
        val parts = pkg.split('.').filter { it.isNotBlank() }
        if (parts.isEmpty()) return pkg
        if (parts.size == 1) return parts[0]

        val genericNames = setOf("entity", "entities", "model", "domain", "persistence", "dao", "dto")
        val last = parts.last()
        val secondLast = parts[parts.size - 2]

        return if (genericNames.contains(last.lowercase()) && parts.size >= 2) {
            "$secondLast.$last"
        } else {
            last
        }
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

    private fun showContextMenu(screenPoint: Point, modelPoint: Point) {
        val targetNode = nodes.values.firstOrNull { !hiddenEntities.contains(it.entity.name) && it.bounds.contains(modelPoint) } ?: return
        val entity = targetNode.entity

        val r = targetNode.bounds
        val relY = modelPoint.y - r.y - ErdLayoutEngine.HEADER_HEIGHT
        val fieldIndex = if (relY >= 0) relY / ErdLayoutEngine.ROW_HEIGHT else -1
        val visibleFields = entity.fields
        val pkFields = visibleFields.filter { it.isPrimaryKey }
        val normalFields = visibleFields.filter { !it.isPrimaryKey }
        val allFields = pkFields + normalFields
        val targetField = if (fieldIndex in 0 until allFields.size) allFields[fieldIndex] else null

        val popup = JPopupMenu()

        // 1. Go to source
        val navText = if (targetField != null) "Go to Source (${targetField.name})" else "Go to Class (${entity.name})"
        val gotoItem = JMenuItem(navText, AllIcons.Nodes.Class)
        gotoItem.addActionListener {
            navigateToSource(entity, targetField?.name)
        }
        popup.add(gotoItem)
        popup.addSeparator()

        // 2. Focus connected entities
        val focusItem = JMenuItem("Focus Connected Entities", AllIcons.General.Filter)
        focusItem.addActionListener {
            selectedNodeName = entity.name
            repaint()
        }
        popup.add(focusItem)

        // 3. Hide entity
        val hideItem = JMenuItem("Hide Entity from Diagram", AllIcons.Actions.Cancel)
        hideItem.addActionListener {
            hiddenEntities.add(entity.name)
            repaint()
        }
        popup.add(hideItem)

        popup.addSeparator()

        // 4. Copy Entity Class Name
        val copyClassItem = JMenuItem("Copy Class Name (${entity.name})", AllIcons.Actions.Copy)
        copyClassItem.addActionListener {
            CopyPasteManager.getInstance().setContents(java.awt.datatransfer.StringSelection(entity.name))
        }
        popup.add(copyClassItem)

        // 5. Copy Table Name
        if (!entity.tableName.isNullOrBlank()) {
            val copyTableItem = JMenuItem("Copy Table Name (${entity.tableName})", AllIcons.Actions.Copy)
            copyTableItem.addActionListener {
                CopyPasteManager.getInstance().setContents(java.awt.datatransfer.StringSelection(entity.tableName))
            }
            popup.add(copyTableItem)
        }

        popup.show(this, screenPoint.x, screenPoint.y)
    }

    init {
        isFocusable = true
        background = canvasBgColor

        val mouseHandler = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    val modelPoint = screenToModel(e.point)
                    val clicked = nodes.values.firstOrNull { !hiddenEntities.contains(it.entity.name) && it.bounds.contains(modelPoint) }
                    if (clicked != null) {
                        val entity = clicked.entity
                        val r = clicked.bounds
                        val relY = modelPoint.y - r.y - ErdLayoutEngine.HEADER_HEIGHT
                        val fieldIndex = if (relY >= 0) relY / ErdLayoutEngine.ROW_HEIGHT else -1
                        val visibleFields = entity.fields
                        val pkFields = visibleFields.filter { it.isPrimaryKey }
                        val normalFields = visibleFields.filter { !it.isPrimaryKey }
                        val allFields = pkFields + normalFields
                        val targetField = if (fieldIndex in 0 until allFields.size) allFields[fieldIndex] else null

                        navigateToSource(entity, targetField?.name)
                    }
                }
            }

            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    val modelPoint = screenToModel(e.point)
                    showContextMenu(e.point, modelPoint)
                    return
                }

                lastMousePoint = e.point
                val screenPt = e.point

                if (showLegend && legendBounds?.contains(screenPt) == true) {
                    val clickedPkg = legendItemBounds.entries.firstOrNull { it.value.contains(screenPt) }?.key
                    if (clickedPkg != null) {
                        selectedLegendModule = if (selectedLegendModule == clickedPkg) null else clickedPkg
                        repaint()
                        return
                    }
                }

                val modelPoint = screenToModel(screenPt)
                val clicked = nodes.values.firstOrNull { !hiddenEntities.contains(it.entity.name) && it.bounds.contains(modelPoint) }
                if (clicked != null) {
                    draggedNode = clicked
                    selectedNodeName = clicked.entity.name
                    dragOffset = Point(modelPoint.x - clicked.bounds.x, modelPoint.y - clicked.bounds.y)
                } else {
                    selectedNodeName = null
                    selectedLegendModule = null
                }
                repaint()
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    val modelPoint = screenToModel(e.point)
                    showContextMenu(e.point, modelPoint)
                    return
                }
                draggedNode = null
                lastMousePoint = null
            }

            override fun mouseMoved(e: MouseEvent) {
                val screenPt = e.point

                if (showLegend && legendBounds?.contains(screenPt) == true) {
                    val hoveredPkg = legendItemBounds.entries.firstOrNull { it.value.contains(screenPt) }?.key
                    if (hoveredPkg != hoveredLegendModule) {
                        hoveredLegendModule = hoveredPkg
                        repaint()
                    }
                    return
                } else if (hoveredLegendModule != null) {
                    hoveredLegendModule = null
                    repaint()
                }

                val modelPoint = screenToModel(screenPt)
                val hovered = nodes.values.firstOrNull { !hiddenEntities.contains(it.entity.name) && it.bounds.contains(modelPoint) }
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

    fun registerKeyboardShortcuts(onRefresh: () -> Unit, onSearchFocus: () -> Unit) {
        val im = getInputMap(WHEN_IN_FOCUSED_WINDOW)
        val am = actionMap

        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0), "refreshERD")
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_DOWN_MASK), "refreshERD")
        am.put("refreshERD", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                onRefresh()
            }
        })

        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, 0), "fitScreen")
        am.put("fitScreen", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                fitToScreen()
            }
        })

        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0, java.awt.event.InputEvent.CTRL_DOWN_MASK), "resetView")
        am.put("resetView", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                resetView()
            }
        })

        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK), "focusSearch")
        am.put("focusSearch", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                onSearchFocus()
            }
        })

        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "clearSelection")
        am.put("clearSelection", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                selectedNodeName = null
                selectedLegendModule = null
                filterQuery = ""
                repaint()
            }
        })
    }

    fun setGraph(graph: ErdGraphModel) {
        this.graphModel = graph
        this.updateModuleColors()
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
        val activeModule = selectedLegendModule ?: hoveredLegendModule
        val query = filterQuery
        val isFiltering = query.isNotEmpty()

        // Pass 1: Draw relationship lines
        val deduplicatedRels = graphModel.deduplicatedRelationships()
        val badgeQueue = mutableListOf<Triple<Point, Point, String>>()
        val badgeHighlightQueue = mutableListOf<Boolean>()

        for ((rIndex, rel) in deduplicatedRels.withIndex()) {
            val isHighlighted = activeEntity != null && (rel.sourceEntity == activeEntity || rel.targetEntity == activeEntity)
            val isDimmed = activeEntity != null && !isHighlighted
            val endpoints = drawRelationshipLine(g2, rel, rIndex, deduplicatedRels, isHighlighted, isDimmed)
            if (endpoints != null) {
                val (srcLabel, tgtLabel) = when (rel.type) {
                    RelationshipType.ONE_TO_ONE -> Pair("1", "1")
                    RelationshipType.ONE_TO_MANY -> Pair("1", "N")
                    RelationshipType.MANY_TO_ONE -> Pair("N", "1")
                    RelationshipType.MANY_TO_MANY -> Pair("N", "M")
                }
                badgeQueue.add(Triple(endpoints.first.first, endpoints.first.second, srcLabel))
                badgeHighlightQueue.add(isHighlighted)
                badgeQueue.add(Triple(endpoints.second.first, endpoints.second.second, tgtLabel))
                badgeHighlightQueue.add(isHighlighted)
            }
        }

        val matchedEntityNames = if (isFiltering) {
            nodes.values.filter { n ->
                n.entity.name.lowercase().contains(query) ||
                (!n.entity.tableName.isNullOrBlank() && n.entity.tableName!!.lowercase().contains(query)) ||
                n.entity.fields.any { it.name.lowercase().contains(query) || it.type.lowercase().contains(query) }
            }.map { it.entity.name }.toSet()
        } else emptySet()

        // Pass 2: Draw entity nodes (cards)
        for (node in nodes.values) {
            val matchesFilter = !isFiltering || matchedEntityNames.contains(node.entity.name)
            val isConnectedToSearchMatch = isFiltering && deduplicatedRels.any { rel ->
                (matchedEntityNames.contains(rel.sourceEntity) && rel.targetEntity == node.entity.name) ||
                (matchedEntityNames.contains(rel.targetEntity) && rel.sourceEntity == node.entity.name)
            }

            val isSelected = node.entity.name == selectedNodeName
            val isHovered = node.entity.name == hoveredNodeName
            val isConnected = activeEntity != null && deduplicatedRels.any {
                (it.sourceEntity == activeEntity && it.targetEntity == node.entity.name) ||
                (it.targetEntity == activeEntity && it.sourceEntity == node.entity.name)
            }
            val isModuleMatch = activeModule != null && (node.entity.packageName.ifBlank { "default" } == activeModule)
            val isModuleDimmed = activeModule != null && !isModuleMatch

            val isHighlightedCard = isSelected || isHovered || isModuleMatch || (isFiltering && matchesFilter)
            val isDimmedCard = (activeEntity != null && !isSelected && !isHovered && !isConnected) ||
                    (isFiltering && !matchesFilter && !isConnectedToSearchMatch) || isModuleDimmed

            drawEntityCard(g2, node, isHighlightedCard, isDimmedCard)
        }

        // Pass 3: Draw Cardinality Badges ON TOP of all lines and cards
        for (i in badgeQueue.indices) {
            val (endPt, farPt, label) = badgeQueue[i]
            val isHL = badgeHighlightQueue[i]
            drawCardinalityBadge(g2, endPt, farPt, label, isHL)
        }

        // Pass 4: Draw Module Color Legend Overlay (screen space overlay)
        drawModuleLegend(g2)
    }

    private fun drawModuleLegend(g2: Graphics2D) {
        if (!showLegend || graphModel.entities.isEmpty()) return

        val modules = graphModel.entities.groupBy { it.packageName.ifBlank { "default" } }
        if (modules.isEmpty()) return

        val originalTransform = g2.transform

        // Reset transform to screen space for fixed overlay rendering
        g2.transform = java.awt.geom.AffineTransform()

        val padding = 12
        val itemHeight = 24
        val headerHeight = 32
        val boxWidth = 250
        val boxHeight = headerHeight + modules.size * itemHeight + padding

        val boxX = 16
        val boxY = 16

        legendBounds = Rectangle(boxX, boxY, boxWidth, boxHeight)

        val cornerRadius = 10f
        // Glassmorphic background matching pure dark theme
        g2.color = JBColor(Color(255, 255, 255, 235), Color(24, 26, 32, 235))
        g2.fill(RoundRectangle2D.Float(boxX.toFloat(), boxY.toFloat(), boxWidth.toFloat(), boxHeight.toFloat(), cornerRadius, cornerRadius))

        // Shadow
        g2.color = JBColor(Color(0, 0, 0, 20), Color(0, 0, 0, 90))
        g2.fill(RoundRectangle2D.Float(boxX.toFloat() + 2f, boxY.toFloat() + 2f, boxWidth.toFloat(), boxHeight.toFloat(), cornerRadius, cornerRadius))

        // Border
        g2.color = JBColor(Color(203, 213, 225, 200), Color(55, 60, 72, 200))
        g2.stroke = BasicStroke(1.0f)
        g2.draw(RoundRectangle2D.Float(boxX.toFloat(), boxY.toFloat(), boxWidth.toFloat(), boxHeight.toFloat(), cornerRadius, cornerRadius))

        // Title Header
        g2.color = primaryTextColor
        g2.font = Font("Dialog", Font.BOLD, 11)
        g2.drawString("MODULE COLOR LEGEND", boxX + 12, boxY + 20)

        // Header Divider
        g2.color = separatorColor
        g2.drawLine(boxX, boxY + headerHeight - 4, boxX + boxWidth, boxY + headerHeight - 4)

        // Module Items
        var yOffset = boxY + headerHeight + 14

        val moduleKeys = modules.keys.sorted()
        legendItemBounds.clear()

        for (pkg in moduleKeys) {
            val entityList = modules[pkg] ?: emptyList()
            val color = getModuleColor(pkg)

            // Full row hitbox covering swatch, label, and entity count for smooth hovering anywhere on row
            val itemRect = Rectangle(boxX + 4, yOffset - 17, boxWidth - 8, itemHeight)
            legendItemBounds[pkg] = itemRect

            val isHovered = (hoveredLegendModule == pkg)
            val isSelected = (selectedLegendModule == pkg)

            if (isHovered || isSelected) {
                g2.color = JBColor(Color(226, 232, 240, 180), Color(45, 49, 60, 180))
                g2.fill(RoundRectangle2D.Float(itemRect.x.toFloat(), itemRect.y.toFloat(), itemRect.width.toFloat(), itemRect.height.toFloat(), 4f, 4f))
            }

            // Swatch circle
            g2.color = color
            g2.fillOval(boxX + 14, yOffset - 10, 12, 12)

            // Package label
            val displayPkg = formatPackageDisplayName(pkg)

            g2.color = primaryTextColor
            g2.font = Font("Dialog", if (isSelected) Font.BOLD else Font.PLAIN, 11)
            g2.drawString(displayPkg, boxX + 34, yOffset)

            // Entity count label on right
            val countText = "${entityList.size} entities"
            g2.color = secondaryTextColor
            g2.font = Font("Dialog", Font.ITALIC, 10)
            val fm = g2.fontMetrics
            val tw = fm.stringWidth(countText)
            g2.drawString(countText, boxX + boxWidth - tw - 12, yOffset)

            yOffset += itemHeight
        }

        g2.transform = originalTransform
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

            val entityAccentColor = if (isHighlighted) selectedBorderColor else getModuleColor(entity.packageName)

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
            val visibleFields = entity.fields
            val pkFields = visibleFields.filter { it.isPrimaryKey }
            val normalFields = visibleFields.filter { !it.isPrimaryKey }
            val allFields = pkFields + normalFields
            val query = filterQuery

            for (i in allFields.indices) {
                val field = allFields[i]
                val rowTop = headerSepY + i * ErdLayoutEngine.ROW_HEIGHT
                val yText = rowTop + 16

                val isFieldSearchMatch = query.isNotEmpty() && (
                    field.name.lowercase().contains(query) ||
                    field.type.lowercase().contains(query) ||
                    (!field.columnName.isNullOrBlank() && field.columnName.lowercase().contains(query))
                )

                drawFieldRow(g2, r, field, yText, isDimmed, isFieldSearchMatch)

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

    private fun drawFieldRow(g2: Graphics2D, r: Rectangle, field: FieldModel, yOffset: Int, isDimmed: Boolean, isFieldSearchMatch: Boolean = false) {
        val rowTop = yOffset - 16
        val rowHeight = ErdLayoutEngine.ROW_HEIGHT

        // 0. High-visibility Field Search Match Row Highlight Pill
        if (isFieldSearchMatch && !isDimmed) {
            val highlightBg = JBColor(Color(254, 243, 199, 180), Color(217, 119, 6, 75)) // Amber/Gold Glowing Pill
            val highlightBorder = JBColor(Color(245, 158, 11, 200), Color(251, 191, 36, 220)) // Amber border

            g2.color = highlightBg
            g2.fill(RoundRectangle2D.Float(r.x + 3f, rowTop + 1f, r.width - 6f, rowHeight - 2f, 6f, 6f))

            g2.color = highlightBorder
            g2.stroke = BasicStroke(1.2f)
            g2.draw(RoundRectangle2D.Float(r.x + 3f, rowTop + 1f, r.width - 6f, rowHeight - 2f, 6f, 6f))
        }

        val fieldTextColor = if (isFieldSearchMatch && !isDimmed) {
            JBColor(Color(180, 83, 9), Color(253, 230, 138)) // Glowing Amber/Gold 200
        } else if (isDimmed) {
            dimmedTextColor
        } else {
            primaryTextColor
        }

        val fieldTypeColor = if (isFieldSearchMatch && !isDimmed) {
            JBColor(Color(180, 83, 9), Color(253, 230, 138))
        } else if (isDimmed) {
            dimmedTextColor
        } else {
            typeTextColor
        }

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
            g2.font = Font("Dialog", if (isFieldSearchMatch) Font.BOLD else Font.PLAIN, 11)
            g2.color = fieldTextColor
            g2.drawString(field.name, r.x + 14, yOffset)
        }

        // Datatype = Teal / Sky Blue 400, right-aligned
        g2.color = fieldTypeColor
        g2.font = Font("Dialog", if (isFieldSearchMatch) Font.BOLD else Font.PLAIN, 11)
        val typeStr = field.type
        val fm = g2.fontMetrics
        val typeWidth = fm.stringWidth(typeStr)
        g2.drawString(typeStr, r.x + r.width - typeWidth - 12, yOffset)
    }

    private fun drawRelationshipLine(
        g2: Graphics2D,
        rel: RelationshipModel,
        relIndex: Int,
        allRels: List<RelationshipModel>,
        isHighlighted: Boolean,
        isDimmed: Boolean
    ): Pair<Pair<Point, Point>, Pair<Point, Point>>? {
        val sourceNode = nodes[rel.sourceEntity] ?: return null
        val targetNode = nodes[rel.targetEntity] ?: return null

        val sR = sourceNode.bounds
        val tR = targetNode.bounds

        // Self-referencing loop
        if (rel.sourceEntity == rel.targetEntity) {
            val arcX = sR.x + sR.width - 20
            val arcY = sR.y - 20
            g2.color = if (isHighlighted) lineHighlightColor else lineNeutralColor
            g2.stroke = BasicStroke(if (isHighlighted) 2.0f else 1.2f)
            g2.drawArc(arcX, arcY, 40, 40, 0, 270)
            return Pair(Pair(Point(arcX + 20, arcY), Point(arcX + 40, arcY)), Pair(Point(arcX + 40, arcY + 20), Point(arcX + 40, arcY + 40)))
        }

        // Distributed connection ports along card boundaries
        val (p1, p2) = calculateDistributedAnchors(rel, allRels)

        val strokeWidth = if (isHighlighted) 2.2f else 1.2f
        val lineColor = when {
            isHighlighted -> lineHighlightColor
            isDimmed -> JBColor(Color(80, 85, 95), Color(60, 65, 75))
            else -> lineNeutralColor
        }

        g2.color = lineColor
        g2.stroke = BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

        // Draw path with minimal bends (prefer straight lines if clear)
        val path = java.awt.geom.Path2D.Float()
        path.moveTo(p1.x.toDouble(), p1.y.toDouble())

        val far1: Point
        val far2: Point

        if (isPathClear(p1, p2, rel.sourceEntity, rel.targetEntity)) {
            // Direct straight line (0 kinks)
            path.lineTo(p2.x.toDouble(), p2.y.toDouble())
            far1 = p2
            far2 = p1
        } else {
            val isHorizontal = Math.abs(p1.x - p2.x) >= Math.abs(p1.y - p2.y)
            val corner1 = if (isHorizontal) Point(p2.x, p1.y) else Point(p1.x, p2.y)
            val corner2 = if (isHorizontal) Point(p1.x, p2.y) else Point(p2.x, p1.y)

            if (isPathClear(p1, corner1, rel.sourceEntity, rel.targetEntity) &&
                isPathClear(corner1, p2, rel.sourceEntity, rel.targetEntity)) {
                // 1-bend L-shape (1 kink)
                path.lineTo(corner1.x.toDouble(), corner1.y.toDouble())
                path.lineTo(p2.x.toDouble(), p2.y.toDouble())
                far1 = corner1
                far2 = corner1
            } else if (isPathClear(p1, corner2, rel.sourceEntity, rel.targetEntity) &&
                       isPathClear(corner2, p2, rel.sourceEntity, rel.targetEntity)) {
                // 1-bend L-shape (1 kink)
                path.lineTo(corner2.x.toDouble(), corner2.y.toDouble())
                path.lineTo(p2.x.toDouble(), p2.y.toDouble())
                far1 = corner2
                far2 = corner2
            } else {
                // 3-segment obstacle-free detour (2 kinks)
                val laneOffset = (relIndex % 5 - 2) * 14
                val initialMidX = (p1.x + p2.x) / 2 + laneOffset
                val initialMidY = (p1.y + p2.y) / 2 + laneOffset

                val midX = if (isHorizontal) findObstacleFreeMidX(initialMidX, p1, p2, rel.sourceEntity, rel.targetEntity) else initialMidX
                val midY = if (!isHorizontal) findObstacleFreeMidY(initialMidY, p1, p2, rel.sourceEntity, rel.targetEntity) else initialMidY

                if (isHorizontal) {
                    path.lineTo(midX.toDouble(), p1.y.toDouble())
                    path.lineTo(midX.toDouble(), p2.y.toDouble())
                    path.lineTo(p2.x.toDouble(), p2.y.toDouble())
                    far1 = Point(midX, p1.y)
                    far2 = Point(midX, p2.y)
                } else {
                    path.lineTo(p1.x.toDouble(), midY.toDouble())
                    path.lineTo(p2.x.toDouble(), midY.toDouble())
                    path.lineTo(p2.x.toDouble(), p2.y.toDouble())
                    far1 = Point(p1.x, midY)
                    far2 = Point(p2.x, midY)
                }
            }
        }

        g2.draw(path)
        return Pair(Pair(p1, far1), Pair(p2, far2))
    }

    private fun isSegmentIntersectingNode(p1: Point, p2: Point, nodeRect: Rectangle): Boolean {
        val expanded = Rectangle(nodeRect.x - 10, nodeRect.y - 10, nodeRect.width + 20, nodeRect.height + 20)
        return expanded.intersectsLine(p1.x.toDouble(), p1.y.toDouble(), p2.x.toDouble(), p2.y.toDouble())
    }

    private fun isPathClear(p1: Point, p2: Point, sourceEntity: String, targetEntity: String): Boolean {
        for (node in nodes.values) {
            if (node.entity.name == sourceEntity || node.entity.name == targetEntity) continue
            if (isSegmentIntersectingNode(p1, p2, node.bounds)) {
                return false
            }
        }
        return true
    }

    private fun findObstacleFreeMidX(initialMidX: Int, p1: Point, p2: Point, sourceEntity: String, targetEntity: String): Int {
        var currentMidX = initialMidX
        val yMin = Math.min(p1.y, p2.y)
        val yMax = Math.max(p1.y, p2.y)

        for (attempt in 0..3) {
            val obstacle = nodes.values.firstOrNull { node ->
                if (node.entity.name == sourceEntity || node.entity.name == targetEntity) return@firstOrNull false
                val r = node.bounds
                val intersectsX = currentMidX >= r.x - 20 && currentMidX <= r.x + r.width + 20
                val intersectsY = !(yMax < r.y || yMin > r.y + r.height)
                intersectsX && intersectsY
            } ?: break

            val r = obstacle.bounds
            currentMidX = if (p1.x < r.x) {
                r.x - 55
            } else {
                r.x + r.width + 55
            }
        }
        return currentMidX
    }

    private fun findObstacleFreeMidY(initialMidY: Int, p1: Point, p2: Point, sourceEntity: String, targetEntity: String): Int {
        var currentMidY = initialMidY
        val xMin = Math.min(p1.x, p2.x)
        val xMax = Math.max(p1.x, p2.x)

        for (attempt in 0..3) {
            val obstacle = nodes.values.firstOrNull { node ->
                if (node.entity.name == sourceEntity || node.entity.name == targetEntity) return@firstOrNull false
                val r = node.bounds
                val intersectsY = currentMidY >= r.y - 20 && currentMidY <= r.y + r.height + 20
                val intersectsX = !(xMax < r.x || xMin > r.x + r.width)
                intersectsX && intersectsY
            } ?: break

            val r = obstacle.bounds
            currentMidY = if (p1.y < r.y) {
                r.y - 45
            } else {
                r.y + r.height + 45
            }
        }
        return currentMidY
    }

    private fun calculateDistributedAnchors(rel: RelationshipModel, allRels: List<RelationshipModel>): Pair<Point, Point> {
        val sourceNode = nodes[rel.sourceEntity] ?: return Pair(Point(0, 0), Point(0, 0))
        val targetNode = nodes[rel.targetEntity] ?: return Pair(Point(0, 0), Point(0, 0))

        val sR = sourceNode.bounds
        val tR = targetNode.bounds

        val sCenter = Point(sR.x + sR.width / 2, sR.y + sR.height / 2)
        val tCenter = Point(tR.x + tR.width / 2, tR.y + tR.height / 2)

        val dx = (tCenter.x - sCenter.x).toDouble()
        val dy = (tCenter.y - sCenter.y).toDouble()
        val isHorizontal = Math.abs(dx) * 0.9 >= Math.abs(dy)

        val sSideRels = allRels.filter { r ->
            val sN = nodes[r.sourceEntity] ?: return@filter false
            val tN = nodes[r.targetEntity] ?: return@filter false
            val sC = Point(sN.bounds.x + sN.bounds.width / 2, sN.bounds.y + sN.bounds.height / 2)
            val tC = Point(tN.bounds.x + tN.bounds.width / 2, tN.bounds.y + tN.bounds.height / 2)
            val rIsH = Math.abs(sC.x - tC.x) >= Math.abs(sC.y - tC.y)
            (r.sourceEntity == rel.sourceEntity && rIsH == isHorizontal) ||
            (r.targetEntity == rel.sourceEntity && rIsH == isHorizontal)
        }
        val sIndex = sSideRels.indexOf(rel).coerceAtLeast(0)
        val sCount = sSideRels.size.coerceAtLeast(1)

        val tSideRels = allRels.filter { r ->
            val sN = nodes[r.sourceEntity] ?: return@filter false
            val tN = nodes[r.targetEntity] ?: return@filter false
            val sC = Point(sN.bounds.x + sN.bounds.width / 2, sN.bounds.y + sN.bounds.height / 2)
            val tC = Point(tN.bounds.x + tN.bounds.width / 2, tN.bounds.y + tN.bounds.height / 2)
            val rIsH = Math.abs(sC.x - tC.x) >= Math.abs(sC.y - tC.y)
            (r.sourceEntity == rel.targetEntity && rIsH == isHorizontal) ||
            (r.targetEntity == rel.targetEntity && rIsH == isHorizontal)
        }
        val tIndex = tSideRels.indexOf(rel).coerceAtLeast(0)
        val tCount = tSideRels.size.coerceAtLeast(1)

        val p1: Point
        val p2: Point

        if (isHorizontal) {
            val sY = sR.y + ((sIndex + 1) * sR.height / (sCount + 1))
            val tY = tR.y + ((tIndex + 1) * tR.height / (tCount + 1))
            if (sCenter.x < tCenter.x) {
                p1 = Point(sR.x + sR.width, sY)
                p2 = Point(tR.x, tY)
            } else {
                p1 = Point(sR.x, sY)
                p2 = Point(tR.x + tR.width, tY)
            }
        } else {
            val sX = sR.x + ((sIndex + 1) * sR.width / (sCount + 1))
            val tX = tR.x + ((tIndex + 1) * tR.width / (tCount + 1))
            if (sCenter.y < tCenter.y) {
                p1 = Point(sX, sR.y + sR.height)
                p2 = Point(tX, tR.y)
            } else {
                p1 = Point(sX, sR.y)
                p2 = Point(tX, tR.y + tR.height)
            }
        }

        return Pair(p1, p2)
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

        // Solid background cutout mask matching canvas background to prevent line bleed
        g2.color = canvasBgColor
        g2.fillOval(badgeX - radius - 3, badgeY - radius - 3, size + 6, size + 6)

        // Subtle shadow
        g2.color = JBColor(Color(0, 0, 0, 40), Color(0, 0, 0, 80))
        g2.fillOval(badgeX - radius + 1, badgeY - radius + 1, size, size)

        // Circle fill
        g2.color = bg
        g2.fillOval(badgeX - radius, badgeY - radius, size, size)

        // Circle border
        g2.color = border
        g2.stroke = BasicStroke(1.2f)
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
