package org.kryspetrie.fileimport.testsupport

import com.petrielabs.metadataeditor.adapters.exiftool.ExifToolLocator
import com.petrielabs.metadataeditor.adapters.exiftool.ExifToolProcessRunner
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Helpers for verifying RAW image payloads are unchanged after metadata writes. */
object RawPayloadTestSupport {
    private val json = Json { ignoreUnknownKeys = true }

    fun extractEmbeddedPayloads(path: Path, runner: ExifToolProcessRunner): Map<String, ByteArray> {
        val tags =
            listOf("JpgFromRaw", "PreviewImage", "ThumbnailImage", "ThumbnailTIFF", "OtherImage")
        return buildMap {
            for (tag in tags) {
                val bytes = extractBinaryTag(path, tag) ?: continue
                if (bytes.isNotEmpty()) put(tag, bytes)
            }
        }
    }

    fun extractLargestStrip(path: Path, runner: ExifToolProcessRunner): ByteArray? {
        val result =
            runner.run(
                listOf(
                    "-json",
                    "-s",
                    "-G1",
                    "-a",
                    "-StripOffsets",
                    "-StripByteCounts",
                    path.toAbsolutePath().toString(),
                )
            )
        if (result.exitCode != 0) return null
        val root = json.parseToJsonElement(result.output).jsonArray.first().jsonObject

        data class Strip(val offsets: List<Long>, val counts: List<Long>)

        val offsetsByGroup = mutableMapOf<String, List<Long>>()
        val countsByGroup = mutableMapOf<String, List<Long>>()
        for ((key, value) in root) {
            val group = key.substringBeforeLast(':').ifBlank { "IFD" }
            when {
                key.endsWith("StripOffsets") -> offsetsByGroup[group] = jsonLongList(value)
                key.endsWith("StripByteCounts") -> countsByGroup[group] = jsonLongList(value)
            }
        }

        val strips =
            offsetsByGroup.mapNotNull { (group, offsets) ->
                val counts = countsByGroup[group] ?: return@mapNotNull null
                if (offsets.size != counts.size) return@mapNotNull null
                Strip(offsets, counts)
            }
        val best = strips.maxByOrNull { it.counts.sum() } ?: return null
        val fileBytes = Files.readAllBytes(path)
        val total = best.counts.sum().toInt()
        val out = ByteArray(total)
        var pos = 0
        for (i in best.offsets.indices) {
            val start = best.offsets[i].toInt()
            val len = best.counts[i].toInt()
            if (start < 0 || len <= 0 || start + len > fileBytes.size) return null
            fileBytes.copyInto(out, pos, start, start + len)
            pos += len
        }
        return out
    }

    fun jpegPixelsOnly(jpeg: ByteArray, runner: ExifToolProcessRunner): ByteArray {
        val src = Files.createTempFile("preview-src-", ".jpg")
        val dest = Files.createTempFile("preview-clean-", ".jpg")
        try {
            Files.write(src, jpeg)
            Files.deleteIfExists(dest)
            val result =
                runner.run(
                    listOf(
                        "-all=",
                        "-o",
                        dest.toAbsolutePath().toString(),
                        src.toAbsolutePath().toString(),
                    )
                )
            check(result.exitCode == 0 && Files.exists(dest) && Files.size(dest) > 0) {
                "Failed to strip preview metadata: ${result.output}"
            }
            return Files.readAllBytes(dest)
        } finally {
            Files.deleteIfExists(src)
            Files.deleteIfExists(dest)
        }
    }

    fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun extractBinaryTag(path: Path, tag: String): ByteArray? {
        ExifToolTestSupport.configureExifToolResources()
        val executable =
            ExifToolLocator(
                    extraResourceRoots =
                        listOf(
                            File("appResources/macos"),
                            File("appResources/linux"),
                            File("appResources/windows"),
                        )
                )
                .resolve()
        val command = buildList {
            val windows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
            if (!windows && executable.name == "exiftool") add("perl")
            add(executable.absolutePath)
            add("-b")
            add("-$tag")
            add(path.toAbsolutePath().toString())
        }
        val process = ProcessBuilder(command).directory(executable.parentFile).start()
        val bytes = process.inputStream.readBytes()
        val code = process.waitFor()
        if (code != 0 || bytes.isEmpty()) return null
        return bytes
    }

    private fun jsonLongList(value: JsonElement): List<Long> =
        when (value) {
            is JsonArray -> value.flatMap { jsonLongList(it) }
            is JsonPrimitive -> {
                val content = value.content.trim()
                when {
                    content.startsWith("(Binary") -> emptyList()
                    content.contains(' ') ->
                        content.split(Regex("\\s+")).mapNotNull { token -> token.toLongOrNull() }
                    else -> listOfNotNull(content.toLongOrNull())
                }
            }
            else -> emptyList()
        }
}
