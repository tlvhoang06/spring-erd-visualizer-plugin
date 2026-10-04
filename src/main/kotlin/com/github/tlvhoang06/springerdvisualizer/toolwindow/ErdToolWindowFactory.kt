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
        val canvasPanel = ErdCanvasPanel().apply {
            this.project = project
        }
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
            add(object : com.intellij.openapi.actionSystem.ToggleAction("Auto-Navigate on Click", "Open entity source file when clicking table on ERD diagram", AllIcons.Actions.EditSource), DumbAware {
                override fun isSelected(e: AnActionEvent): Boolean = canvasPanel.autoNavigateOnClick
                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    canvasPanel.autoNavigateOnClick = state
                }
            })
            add(object : AnAction("Toggle Module Legend", "Show/hide module color legend panel", AllIcons.Gutter.Colors), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    canvasPanel.showLegend = !canvasPanel.showLegend
                }
            })
            add(object : AnAction("Reset Hidden Entities", "Restore any hidden entities back to diagram", AllIcons.Actions.ShowCode), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    canvasPanel.resetHiddenEntities()
                }
            })
            add(object : AnAction("Export Mermaid", "Copy Mermaid ERD diagram syntax to clipboard", AllIcons.Actions.Copy), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    val mermaidText = com.github.tlvhoang06.springerdvisualizer.exporter.MermaidExporter.export(canvasPanel.graphModel)
                    com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(java.awt.datatransfer.StringSelection(mermaidText))
                    com.intellij.openapi.ui.Messages.showInfoMessage(project, "Mermaid ERD syntax copied to clipboard!", "Export Mermaid")
                }
            })
            add(object : AnAction("Export PNG Image", "Export ERD diagram canvas to PNG image file", AllIcons.ToolbarDecorator.Export), DumbAware {
                override fun actionPerformed(e: AnActionEvent) {
                    val descriptor = com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    descriptor.title = "Select Folder for ERD PNG Export"
                    val file = com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, project, null)
                    if (file != null) {
                        val outputFile = java.io.File(file.path, "spring_erd_diagram.png")
                        val success = com.github.tlvhoang06.springerdvisualizer.exporter.ImageExporter.exportToPng(canvasPanel, outputFile)
                        if (success) {
                            com.intellij.openapi.ui.Messages.showInfoMessage(project, "Exported ERD diagram to PNG:\n${outputFile.absolutePath}", "Export PNG")
                        } else {
                            com.intellij.openapi.ui.Messages.showErrorDialog(project, "Failed to export PNG image.", "Export PNG")
                        }
                    }
                }
            })
        }

        val searchTextField = com.intellij.ui.SearchTextField(false).apply {
            textEditor.emptyText.text = "Search entities or fields..."
            font = Font("Dialog", Font.PLAIN, 11)
            addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
                override fun textChanged(e: javax.swing.event.DocumentEvent) {
                    canvasPanel.filterQuery = text
                }
            })
        }

        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_TITLE, toolbarGroup, true)
        toolbar.targetComponent = mainPanel

        val topPanel = JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.WEST)
            add(searchTextField, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.EAST)
        }

        mainPanel.add(topPanel, BorderLayout.NORTH)
        mainPanel.add(canvasPanel, BorderLayout.CENTER)

        val content = ContentFactory.getInstance().createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)

        // Register keyboard shortcuts (F5: Refresh, F: Fit, Ctrl+0: Reset, Ctrl+F: Search focus, Esc: Clear)
        canvasPanel.registerKeyboardShortcuts(
            onRefresh = { refreshDiagram(project, canvasPanel, statusLabel) },
            onSearchFocus = { searchTextField.requestFocusInWindow() }
        )

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
