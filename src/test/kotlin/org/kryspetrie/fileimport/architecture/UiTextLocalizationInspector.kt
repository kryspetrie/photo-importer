package org.kryspetrie.fileimport.architecture

import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Finds hardcoded user-facing string literals in UI Kotlin sources.
 *
 * Violations must be resolved with [org.kryspetrie.fileimport.domain.model.i18n.StringKey] lookups
 * or annotated with [org.kryspetrie.fileimport.domain.model.i18n.LocalizedExempt].
 */
object UiTextLocalizationInspector {

    data class Violation(val file: String, val line: Int, val literal: String, val context: String)

    private val uiPackagePrefix = "org.kryspetrie.fileimport.ui"
    private val extraSources = listOf("PetrieFileImporterApp.kt")

    private val i18nMarkers =
        listOf(
            "StringKey.",
            "s.t(",
            ".t(StringKey",
            "localePort.t(",
            "strings()",
            "LocalizedExempt",
        )

    /** Patterns matched against whitespace-normalized source, so multi-line calls are covered. */
    private val normalizedPatterns =
        listOf(
            Regex("""\bText\s*\(\s*"([^"$\\][^"]*)"""),
            Regex("""\bText\s*\(\s*text\s*=\s*"([^"$\\][^"]*)"""),
            Regex("""\b(?:Menu|Item)\s*\(\s*"([^"$\\][^"]*)"""),
            Regex(
                """\b(?:title|label|contentDescription|placeholder|message|hint)\s*=\s*"([^"$\\][^"]*)"""
            ),
            // contentDescription passed positionally: Icon(Icons.Default.Close, "Close")
            Regex("""\bIcon\s*\(\s*[A-Za-z_][\w.]*\s*,\s*"([^"$\\][^"]*)"""),
        )

    /** Patterns matched against comment-stripped source with original line breaks. */
    private val rawPatterns =
        listOf(
            Regex("""\bShortcutRow\s*\(\s*"[^"]*"\s*,\s*"([^"$\\][^"]*)"""),
            Regex("""\bShortcutSection\s*\(\s*title\s*=\s*"([^"$\\][^"]*)"""),
            Regex("""\badd\s*\(\s*"([^"$\\][^"]*)"\s+to\b"""),
            Regex("""\bLoadingContent\s*\(\s*message\s*=\s*"([^"$\\][^"]*)"""),
            Regex("""\berrorMessage\s*=\s*"([^"$\\][^"]*)"""),
            Regex("""\berrorMessage\s*=\s*e\.message\s*\?:\s*"([^"$\\][^"]*)"""),
            Regex("""\bcorrectionsApplied\s*=\s*listOf\s*\(\s*"([^"$\\][^"]*)"""),
        )

    fun findViolations(sourceRoot: Path): List<Violation> {
        val kotlinRoot = sourceRoot.resolve("main/kotlin/org/kryspetrie/fileimport")
        if (!kotlinRoot.toFile().exists()) return emptyList()

        return kotlinRoot
            .toFile()
            .walkTopDown()
            .filter { it.extension == "kt" }
            .filter { file ->
                val relative = kotlinRoot.relativize(file.toPath()).toString()
                relative.startsWith("ui") || extraSources.any { relative.endsWith(it) }
            }
            .flatMap { file ->
                inspectFile(kotlinRoot.relativize(file.toPath()).toString(), file.readText())
            }
            .sortedWith(compareBy({ it.file }, { it.line }, { it.literal }))
            .toList()
    }

    internal fun inspectFile(relativePath: String, source: String): List<Violation> {
        if (source.contains("@file:LocalizedExempt")) return emptyList()

        val withoutComments = stripComments(source)
        val normalized = withoutComments.replace(Regex("\\s+"), " ")
        val lineStarts = buildLineStartOffsets(withoutComments)

        val violations = mutableListOf<Violation>()
        val searches =
            normalizedPatterns.map { it to normalized } + rawPatterns.map { it to withoutComments }
        for ((regex, haystack) in searches) {
            for (match in regex.findAll(haystack)) {
                val literal = match.groupValues[1]
                if (!isUserFacingLiteral(literal)) continue

                val snippet =
                    withoutComments
                        .substring(match.range.first.coerceAtMost(withoutComments.length - 1))
                        .lineSequence()
                        .first()
                        .trim()
                if (i18nMarkers.any { snippet.contains(it) }) continue

                val line =
                    lineNumberAt(
                        lineStarts,
                        match.range.first.coerceAtMost(withoutComments.length - 1),
                    )
                violations.add(Violation(relativePath, line, literal, snippet.take(120)))
            }
        }
        return violations.distinctBy { "${it.file}:${it.line}:${it.literal}" }
    }

    private fun isUserFacingLiteral(value: String): Boolean {
        if (value.isBlank()) return false
        if (!value.any { it.isLetter() }) return false
        if (value.length <= 2 && !value.contains(Regex("[a-zA-Z]{3}"))) return false
        if (value.matches(Regex("""^[\d\s.,:;%+\-*/()?\\]+$"""))) return false
        if (value.matches(Regex("""^[%$][\d.]+[a-zA-Z]?$"""))) return false
        return true
    }

    private fun stripComments(source: String): String {
        val withoutBlock = source.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        return withoutBlock.lineSequence().joinToString("\n") { line ->
            val commentIndex = line.indexOf("//")
            if (commentIndex >= 0) line.substring(0, commentIndex) else line
        }
    }

    private fun buildLineStartOffsets(text: String): IntArray {
        val starts = mutableListOf(0)
        text.forEachIndexed { index, char -> if (char == '\n') starts.add(index + 1) }
        return starts.toIntArray()
    }

    private fun lineNumberAt(lineStarts: IntArray, offset: Int): Int {
        var line = 1
        for (i in 1 until lineStarts.size) {
            if (lineStarts[i] > offset) break
            line++
        }
        return line
    }
}
