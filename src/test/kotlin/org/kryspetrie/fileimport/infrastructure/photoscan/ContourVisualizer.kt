package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Debug test to visualize what contours and corners are being detected.
 *
 * Creates output images with:
 * 1. Original image with detected contours overlaid
 * 2. Binary mask showing detected regions
 * 3. Corner positions marked
 */
object ContourVisualizer {

    fun visualizeDetection(imagePath: String, outputDir: String = "/tmp") {
        val image = ImageIO.read(java.io.File(imagePath))
        visualizeDetection(image, outputDir)
    }

    fun visualizeDetection(image: BufferedImage, outputDir: String = "/tmp"): String {
        val detector = RectangleDetector(minArea = 100000, minQuadRatio = 0.4f)
        val quads = detector.detectRectangles(image, expectedCount = 3)

        // Create visualization image
        val vis = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val g = vis.graphics
        g.drawImage(image, 0, 0, null)

        // Draw detected quads
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        for ((i, quad) in quads.withIndex()) {
            val color = colors[i % colors.size]
            g.color = color
            (g as java.awt.Graphics2D).stroke = java.awt.BasicStroke(4f)

            // Draw polygon
            val xs = quad.corners.map { it.x }
            val ys = quad.corners.map { it.y }
            val xpoints = xs.toIntArray()
            val ypoints = ys.toIntArray()
            g.drawPolygon(xpoints, ypoints, 4)

            // Draw corner points with labels
            for ((j, corner) in quad.corners.withIndex()) {
                g.color = color
                g.fillOval(corner.x - 10, corner.y - 10, 20, 20)
                g.color = Color.WHITE
                g.drawString("$j", corner.x - 4, corner.y + 4)
            }

            // Draw centroid
            g.color = color
            g.fillOval(quad.centroid.x - 15, quad.centroid.y - 15, 30, 30)
            g.color = Color.WHITE
            g.drawString("C", quad.centroid.x - 6, quad.centroid.y + 4)
        }

        // Save visualization
        val outputPath = "$outputDir/contours_detected.png"
        ImageIO.write(vis, "PNG", java.io.File(outputPath))
        return outputPath
    }

    fun analyzeContourStructure(imagePath: String): String {
        val image = ImageIO.read(java.io.File(imagePath))
        return analyzeContourStructure(image)
    }

    fun analyzeContourStructure(image: BufferedImage): String {
        val detector = RectangleDetector(minArea = 100000, minQuadRatio = 0.4f)
        val quads = detector.detectRectangles(image, expectedCount = 3)

        val sb = StringBuilder()
        sb.appendLine("=== Contour Analysis ===")
        sb.appendLine("Image size: ${image.width}x${image.height}")
        sb.appendLine()

        for ((i, quad) in quads.withIndex()) {
            sb.appendLine("Quad $i:")
            sb.appendLine("  Centroid: (${quad.centroid.x}, ${quad.centroid.y})")
            sb.appendLine("  Area: ${quad.area}")
            sb.appendLine("  Aspect ratio: ${"%.2f".format(quad.aspectRatio)}")

            // Calculate edge lengths
            val corners = quad.corners
            for (j in corners.indices) {
                val start = corners[j]
                val end = corners[(j + 1) % 4]
                val length = kotlin.math.sqrt(
                    ((end.x - start.x) * (end.x - start.x) + (end.y - start.y) * (end.y - start.y)).toDouble()
                )
                sb.appendLine("  Edge $j length: ${"%.0f".format(length)}px")
            }

            // Sort corners and show coordinates
            val sorted = corners.sortedBy { it.x + it.y }
            sb.appendLine("  Sorted corners (TL, TR, BR, BL):")
            val labels = listOf("TL", "TR", "BR", "BL")
            for (k in sorted.indices) {
                sb.appendLine("    ${labels[k]}: (${sorted[k].x}, ${sorted[k].y})")
            }
            sb.appendLine()
        }

        return sb.toString()
    }
}