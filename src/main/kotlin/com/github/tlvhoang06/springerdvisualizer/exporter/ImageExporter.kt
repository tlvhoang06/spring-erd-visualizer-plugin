package com.github.tlvhoang06.springerdvisualizer.exporter

import com.github.tlvhoang06.springerdvisualizer.renderer.ErdCanvasPanel
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object ImageExporter {

    fun exportToPng(canvasPanel: ErdCanvasPanel, outputFile: File): Boolean {
        val graph = canvasPanel.graphModel
        if (graph.entities.isEmpty()) return false

        val width = Math.max(800, canvasPanel.width)
        val height = Math.max(600, canvasPanel.height)

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()

        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            
            // Fill background
            g2.color = canvasPanel.background ?: Color(43, 45, 48)
            g2.fillRect(0, 0, width, height)

            canvasPanel.renderCanvas(g2)
        } finally {
            g2.dispose()
        }

        outputFile.parentFile?.mkdirs()
        return ImageIO.write(image, "PNG", outputFile)
    }
}
