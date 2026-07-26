import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

val exiftoolVersion = "13.59"
val exiftoolUnixSha256 = "87d3317882fdae9cb4dcfe57a96a378d0132ffc02c731315bf128b19ddcf7aac"
val exiftoolWindowsSha256 = "44b512b25af500724ba579d0a53c8fc5851628b692dd5e5d94ae4a15c2cba9ec"
val appResourcesDir = layout.projectDirectory.dir("appResources")

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

fun verifySha256(file: File, expected: String) {
    val actual = sha256Hex(file)
    check(actual.equals(expected, ignoreCase = true)) {
        "Checksum mismatch for ${file.name}: expected $expected but was $actual"
    }
}

fun download(url: String, dest: File) {
    dest.parentFile.mkdirs()
    val curl =
        ProcessBuilder(
                "curl",
                "-fsSL",
                "-A",
                "petrie-importer-gradle",
                "-o",
                dest.absolutePath,
                url,
            )
            .start()
    if (curl.waitFor() == 0 && dest.length() > 0L) return

    dest.delete()
    var current = url
    repeat(8) {
        val connection =
            (URI(current).toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "petrie-importer-gradle")
                connectTimeout = 60_000
                readTimeout = 120_000
            }
        val code = connection.responseCode
        if (code in 300..399) {
            current =
                connection.getHeaderField("Location")
                    ?: error("Redirect without Location from $current")
            connection.disconnect()
            return@repeat
        }
        check(code in 200..299) { "Download failed ($code) for $current" }
        connection.inputStream.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return
    }
    error("Too many redirects downloading $url")
}

fun unzip(zipFile: File, destDir: File) {
    destDir.mkdirs()
    ZipInputStream(zipFile.inputStream()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val out = File(destDir, entry.name)
            if (entry.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile.mkdirs()
                out.outputStream().use { zis.copyTo(it) }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
}

fun untarGzip(archive: File, destDir: File) {
    destDir.mkdirs()
    val process =
        ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", destDir.absolutePath)
            .inheritIO()
            .start()
    check(process.waitFor() == 0) { "tar failed for $archive" }
}

tasks.register("downloadExifTool") {
    group = "setup"
    description = "Download ExifTool into appResources for macOS, Linux, and Windows packaging."
    val marker = layout.buildDirectory.file("exiftool/.downloaded-$exiftoolVersion")
    outputs.file(marker)

    doLast {
        val cache = layout.buildDirectory.dir("exiftool-cache").get().asFile
        cache.mkdirs()

        val unixArchive = File(cache, "exiftool-$exiftoolVersion.tar.gz")
        val windowsZip = File(cache, "exiftool-${exiftoolVersion}_64.zip")

        if (!unixArchive.exists()) {
            println("Downloading ExifTool $exiftoolVersion (Unix)...")
            download(
                "https://github.com/exiftool/exiftool/archive/refs/tags/$exiftoolVersion.tar.gz",
                unixArchive,
            )
        }
        verifySha256(unixArchive, exiftoolUnixSha256)

        if (!windowsZip.exists()) {
            println("Downloading ExifTool $exiftoolVersion (Windows)...")
            download(
                "https://sourceforge.net/projects/exiftool/files/exiftool-${exiftoolVersion}_64.zip/download",
                windowsZip,
            )
        }
        verifySha256(windowsZip, exiftoolWindowsSha256)

        val unixExtract = File(cache, "unix-extract")
        unixExtract.deleteRecursively()
        untarGzip(unixArchive, unixExtract)
        val unixRoot = File(unixExtract, "exiftool-$exiftoolVersion")
        check(unixRoot.isDirectory) { "Expected ExifTool unix root at $unixRoot" }

        fun installUnix(targetOsDir: File) {
            targetOsDir.deleteRecursively()
            targetOsDir.mkdirs()
            val dest = File(targetOsDir, "exiftool")
            dest.mkdirs()
            unixRoot.copyRecursively(dest, overwrite = true)
            File(dest, "exiftool").setExecutable(true)
        }

        installUnix(appResourcesDir.dir("macos").asFile)
        installUnix(appResourcesDir.dir("linux").asFile)

        val winExtract = File(cache, "win-extract")
        winExtract.deleteRecursively()
        unzip(windowsZip, winExtract)
        val windowsDir = appResourcesDir.dir("windows").asFile
        windowsDir.deleteRecursively()
        windowsDir.mkdirs()
        val winDest = File(windowsDir, "exiftool")
        winDest.mkdirs()

        val winRoot =
            winExtract
                .walkTopDown()
                .firstOrNull {
                    it.isFile && it.name.startsWith("exiftool") && it.extension.equals("exe", true)
                }
                ?.parentFile ?: error("Windows ExifTool executable not found in $windowsZip")

        winRoot.listFiles()?.forEach { src ->
            val targetName =
                when {
                    src.isFile &&
                        src.name.startsWith("exiftool") &&
                        src.extension.equals("exe", true) -> "exiftool.exe"
                    else -> src.name
                }
            src.copyRecursively(File(winDest, targetName), overwrite = true)
        }
        check(File(winDest, "exiftool.exe").isFile) {
            "Expected flattened Windows ExifTool at ${File(winDest, "exiftool.exe")}"
        }

        File(appResourcesDir.asFile, "common/README.txt").apply {
            parentFile.mkdirs()
            writeText(
                """
                Bundled ExifTool $exiftoolVersion for Petrie Image Importer installers.
                Regenerated by: ./gradlew downloadExifTool
                """
                    .trimIndent() + "\n"
            )
        }

        marker.get().asFile.apply {
            parentFile.mkdirs()
            writeText(exiftoolVersion)
        }
        println("ExifTool $exiftoolVersion installed under appResources/{macos,linux,windows}")
    }
}

tasks.configureEach {
    if (name == "prepareAppResources" || name == "run" || name == "test") {
        dependsOn("downloadExifTool")
    }
}
