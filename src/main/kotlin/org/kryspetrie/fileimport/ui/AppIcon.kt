package org.kryspetrie.fileimport.ui

import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

fun createAppIcon(size: Int = 512): BufferedImage {
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

    val s = size.toFloat()
    val margin = s * 0.06f
    val photoW = s * 0.68f
    val photoH = s * 0.52f
    val offset = s * 0.11f
    val cornerR = s * 0.04f
    val border = s * 0.012f

    for (i in 2 downTo 0) {
        val x = margin + (2 - i) * offset
        val y = margin + i * offset * 0.9f

        // Drop shadow
        g.color = Color(0, 0, 0, 25)
        g.fill(RoundRectangle2D.Float(x + 3, y + 3, photoW, photoH, cornerR, cornerR))

        // White card
        g.color = Color.WHITE
        g.fill(RoundRectangle2D.Float(x, y, photoW, photoH, cornerR, cornerR))

        val inset = border * 1.5f
        val cx = x + inset
        val cy = y + inset
        val cw = photoW - inset * 2
        val ch = photoH - inset * 2

        if (i == 0) {
            // Top photo: sky scene
            g.paint = GradientPaint(cx, cy, Color(110, 180, 240), cx, cy + ch, Color(180, 220, 250))
            g.fill(RoundRectangle2D.Float(cx, cy, cw, ch, cornerR * 0.5f, cornerR * 0.5f))

            // Sun
            val sunR = s * 0.06f
            g.color = Color(255, 210, 80)
            g.fill(
                Ellipse2D.Float(cx + cw * 0.72f - sunR, cy + ch * 0.18f - sunR, sunR * 2, sunR * 2)
            )

            // Mountains
            val mtn = GeneralPath()
            mtn.moveTo(cx, cy + ch)
            mtn.lineTo(cx + cw * 0.18f, cy + ch * 0.45f)
            mtn.lineTo(cx + cw * 0.35f, cy + ch * 0.65f)
            mtn.lineTo(cx + cw * 0.52f, cy + ch * 0.30f)
            mtn.lineTo(cx + cw * 0.72f, cy + ch * 0.55f)
            mtn.lineTo(cx + cw, cy + ch * 0.42f)
            mtn.lineTo(cx + cw, cy + ch)
            mtn.closePath()
            g.paint =
                GradientPaint(
                    cx,
                    cy + ch * 0.3f,
                    Color(80, 140, 70),
                    cx,
                    cy + ch,
                    Color(60, 110, 55),
                )
            g.fill(mtn)
        } else if (i == 1) {
            g.paint = GradientPaint(cx, cy, Color(200, 210, 225), cx, cy + ch, Color(180, 190, 205))
            g.fill(RoundRectangle2D.Float(cx, cy, cw, ch, cornerR * 0.5f, cornerR * 0.5f))
        } else {
            g.paint = GradientPaint(cx, cy, Color(215, 220, 230), cx, cy + ch, Color(195, 200, 215))
            g.fill(RoundRectangle2D.Float(cx, cy, cw, ch, cornerR * 0.5f, cornerR * 0.5f))
        }

        // Card border
        g.color = Color(170, 175, 185)
        g.stroke = BasicStroke(border * 0.5f)
        g.draw(RoundRectangle2D.Float(x, y, photoW, photoH, cornerR, cornerR))
    }

    g.dispose()
    return img
}
