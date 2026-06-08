package org.kryspetrie.fileimport.ui.util

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import org.kryspetrie.fileimport.ui.createAppIcon

fun main() {
    val resourceDir = File("src/main/resources")
    resourceDir.mkdirs()

    val pngFile = File(resourceDir, "icon.png")
    val icnsFile = File(resourceDir, "icon.icns")
    val icoFile = File(resourceDir, "icon.ico")

    val icon512 = createAppIcon(512)
    ImageIO.write(icon512, "png", pngFile)
    println("Created: ${pngFile.absolutePath}")

    writeIco(icoFile, listOf(16, 32, 48, 256).map { createAppIcon(it) })
    println("Created: ${icoFile.absolutePath}")

    if (System.getProperty("os.name").lowercase().contains("mac")) {
        generateIcns(pngFile, icnsFile)
    } else {
        ImageIO.write(icon512, "png", icnsFile)
        println("Created: ${icnsFile.absolutePath} (PNG fallback)")
    }

    println("Icons generated in ${resourceDir.absolutePath}")
}

private fun writeIco(file: File, images: List<BufferedImage>) {
    val pngBlobs =
        images.map { img ->
            val baos = ByteArrayOutputStream()
            ImageIO.write(img, "png", baos)
            baos.toByteArray()
        }

    val headerSize = 6
    val dirEntrySize = 16
    var dataOffset = headerSize + dirEntrySize * images.size

    val buf = ByteBuffer.allocate(dataOffset + pngBlobs.sumOf { it.size })
    buf.order(ByteOrder.LITTLE_ENDIAN)

    // ICO header: reserved(2) + type 1=ICO(2) + count(2)
    buf.putShort(0)
    buf.putShort(1)
    buf.putShort(images.size.toShort())

    // Directory entries
    for ((i, img) in images.withIndex()) {
        val w = if (img.width >= 256) 0 else img.width
        val h = if (img.height >= 256) 0 else img.height
        buf.put(w.toByte()) // width (0 = 256)
        buf.put(h.toByte()) // height (0 = 256)
        buf.put(0) // color palette count
        buf.put(0) // reserved
        buf.putShort(1) // color planes
        buf.putShort(32) // bits per pixel
        buf.putInt(pngBlobs[i].size) // image data size
        buf.putInt(dataOffset) // offset to image data
        dataOffset += pngBlobs[i].size
    }

    for (blob in pngBlobs) {
        buf.put(blob)
    }

    file.writeBytes(buf.array())
}

private fun generateIcns(sourcePng: File, outputIcns: File) {
    try {
        val iconsetDir = File(System.getProperty("java.io.tmpdir"), "petrie-icon.iconset")
        iconsetDir.mkdirs()

        val sizes = listOf(16, 32, 64, 128, 256, 512)
        for (size in sizes) {
            val icon = createAppIcon(size)
            ImageIO.write(icon, "png", File(iconsetDir, "icon_${size}x${size}.png"))
        }
        val retinaMapping = mapOf(32 to 16, 64 to 32, 256 to 128, 512 to 256)
        for ((size, half) in retinaMapping) {
            File(iconsetDir, "icon_${size}x${size}.png")
                .copyTo(File(iconsetDir, "icon_${half}x${half}@2x.png"), overwrite = true)
        }

        val process =
            ProcessBuilder(
                    "iconutil",
                    "-c",
                    "icns",
                    iconsetDir.absolutePath,
                    "-o",
                    outputIcns.absolutePath,
                )
                .redirectErrorStream(true)
                .start()
        process.waitFor()

        if (outputIcns.exists()) {
            println("Created: ${outputIcns.absolutePath} (native ICNS)")
        } else {
            ImageIO.write(createAppIcon(512), "png", outputIcns)
            println("Created: ${outputIcns.absolutePath} (PNG fallback — iconutil failed)")
        }

        iconsetDir.deleteRecursively()
    } catch (e: Exception) {
        ImageIO.write(createAppIcon(512), "png", outputIcns)
        println("Created: ${outputIcns.absolutePath} (PNG fallback — ${e.message})")
    }
}
