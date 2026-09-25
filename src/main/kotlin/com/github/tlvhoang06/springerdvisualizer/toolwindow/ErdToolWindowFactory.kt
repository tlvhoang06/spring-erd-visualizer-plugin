package com.github.tlvhoang06.springerdvisualizer.toolwindow

import com.github.tlvhoang06.springerdvisualizer.analyzer.ProjectEntityScanner
import com.github.tlvhoang06.springerdvisualizer.renderer.ErdCanvasPanel
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
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JPanel

class ErdToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val canvasPanel = ErdCanvasPanel()

        val mainPanel = JPanel(BorderLayout())
        val toolbarGroup = DefaultActionGroup().apply {
            add(object : AnAction("Refresh", "Refresh ERD diagram from source entities", null), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    refreshDiagram(project, canvasPanel)
                }
            })
            add(object : AnAction("Fit to Screen", "Fit diagram to window", null), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    canvasPanel.fitToScreen()
                }
            })
            add(object : AnAction("Reset View", "Reset zoom and pan position", null), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    canvasPanel.resetView()
                }
            })
            add(object : AnAction("Zoom In", "Zoom in", null), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    canvasPanel.zoomIn()
                }
            })
            add(object : AnAction("Zoom Out", "Zoom out", null), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    canvasPanel.zoomOut()
                }
            })
        }

        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_TITLE, toolbarGroup, true)
        toolbar.targetComponent = mainPanel

        mainPanel.add(toolbar.component, BorderLayout.NORTH)
        mainPanel.add(canvasPanel, BorderLayout.CENTER)

        val content = ContentFactory.getInstance().createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)

        // Perform initial scan
        refreshDiagram(project, canvasPanel)
    }

    private fun refreshDiagram(project: Project, canvasPanel: ErdCanvasPanel) {
        ApplicationManager.getApplication().runReadAction {
            val graph = ProjectEntityScanner.scanProjectEntities(project)
            ApplicationManager.getApplication().invokeLater {
                canvasPanel.setGraph(graph)
            }
        }
    }
}
