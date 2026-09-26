package com.github.tlvhoang06.springerdvisualizer.toolwindow

import com.github.tlvhoang06.springerdvisualizer.analyzer.ProjectEntityScanner
import com.github.tlvhoang06.springerdvisualizer.renderer.ErdCanvasPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class ErdToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val canvasPanel = ErdCanvasPanel()
        val statusLabel = JLabel(" Scanning project entities...").apply {
            font = Font("Dialog", Font.PLAIN, 11)
            foreground = JBColor.GRAY
            border = EmptyBorder(4, 10, 4, 10)
        }

        val mainPanel = JPanel(BorderLayout())
        val toolbarGroup = DefaultActionGroup().apply {
            add(object : AnAction("Refresh", "Refresh ERD diagram from source entities", AllIcons.Actions.Refresh), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    refreshDiagram(project, canvasPanel, statusLabel)
                }
            })
            add(object : AnAction("Fit to Screen", "Fit diagram to window", AllIcons.General.FitContent), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    canvasPanel.fitToScreen()
                }
            })
            add(object : AnAction("Reset View", "Reset zoom and pan position", AllIcons.Actions.Rollback), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    canvasPanel.resetView()
                }
            })
            add(object : AnAction("Zoom In", "Zoom in", AllIcons.General.ZoomIn), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    canvasPanel.zoomIn()
                }
            })
            add(object : AnAction("Zoom Out", "Zoom out", AllIcons.General.ZoomOut), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    canvasPanel.zoomOut()
                }
            })
        }

        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_TITLE, toolbarGroup, true)
        toolbar.targetComponent = mainPanel

        val topPanel = JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.WEST)
            add(statusLabel, BorderLayout.EAST)
        }

        mainPanel.add(topPanel, BorderLayout.NORTH)
        mainPanel.add(canvasPanel, BorderLayout.CENTER)

        val content = ContentFactory.getInstance().createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)

        // Initial scan
        refreshDiagram(project, canvasPanel, statusLabel)
    }

    private fun refreshDiagram(project: Project, canvasPanel: ErdCanvasPanel, statusLabel: JLabel) {
        statusLabel.text = "Scanning project entities..."
        ApplicationManager.getApplication().executeOnPooledThread {
            ApplicationManager.getApplication().runReadAction {
                val graph = ProjectEntityScanner.scanProjectEntities(project)
                ApplicationManager.getApplication().invokeLater {
                    canvasPanel.setGraph(graph)
                    val entityCount = graph.entities.size
                    val relCount = graph.deduplicatedRelationships().size
                    if (entityCount == 0) {
                        statusLabel.text = "0 Entities found"
                    } else {
                        statusLabel.text = "$entityCount Entities | $relCount Relationships"
                    }
                }
            }
        }
    }
}
