package org.kryspetrie.fileimport

import java.io.File
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

  val icon256 = createAppIcon(256)
  ImageIO.write(icon256, "png", icoFile)
  println("Created: ${icoFile.absolutePath}")

  if (System.getProperty("os.name").lowercase().contains("mac")) {
    generateIcns(pngFile, icnsFile)
  } else {
    ImageIO.write(icon512, "png", icnsFile)
    println("Created: ${icnsFile.absolutePath} (PNG fallback)")
  }

  println("Icons generated in ${resourceDir.absolutePath}")
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
                "iconutil", "-c", "icns", iconsetDir.absolutePath, "-o", outputIcns.absolutePath)
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
