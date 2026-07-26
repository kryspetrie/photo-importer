package org.kryspetrie.fileimport.ui.i18n

import org.kryspetrie.fileimport.domain.model.ConflictResolution
import org.kryspetrie.fileimport.domain.model.DateSource
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.i18n.StringKey

/** Builds a one-line import configuration summary for settings headers. */
fun Strings.configSummary(c: ImportConfiguration): String =
    buildString {
        if (c.createSubfolders) append(c.folderPattern) else append(t(StringKey.IMPORT_SUMMARY_FLAT))
        append(t(StringKey.IMPORT_SUMMARY_SEPARATOR))
        if (c.preserveOriginalName) append(t(StringKey.IMPORT_SUMMARY_ORIGINAL_NAMES))
        else append(c.fileNamePattern)
        if (c.verifyAfterCopy) {
            append(t(StringKey.IMPORT_SUMMARY_SEPARATOR))
            append(t(StringKey.IMPORT_SETTINGS_VERIFY))
        }
        if (c.deleteAfterImport) {
            append(t(StringKey.IMPORT_SUMMARY_SEPARATOR))
            append(t(StringKey.IMPORT_SETTINGS_DELETE_SOURCE))
        }
        if (c.detectVisualDuplicates) {
            append(t(StringKey.IMPORT_SUMMARY_SEPARATOR))
            append(t(StringKey.IMPORT_SETTINGS_DEDUPE))
        }
        if (c.autoOrientEnabled) {
            append(t(StringKey.IMPORT_SUMMARY_SEPARATOR))
            append(t(StringKey.IMPORT_SUMMARY_AUTO_ORIENT))
        }
    }

fun Strings.conflictResolutionLabel(value: ConflictResolution): String =
    when (value) {
        ConflictResolution.RENAME -> t(StringKey.SETTINGS_ORG_CONFLICT_RENAME)
        ConflictResolution.SKIP -> t(StringKey.SETTINGS_ORG_CONFLICT_SKIP)
        ConflictResolution.REPLACE -> t(StringKey.SETTINGS_ORG_CONFLICT_REPLACE)
        ConflictResolution.ASK_USER -> t(StringKey.SETTINGS_ORG_CONFLICT_ASK)
    }

fun Strings.dateSourceLabel(value: DateSource): String =
    when (value) {
        DateSource.EXIF_DATE -> t(StringKey.SETTINGS_ORG_DATE_EXIF)
        DateSource.FILE_MODIFIED_DATE -> t(StringKey.SETTINGS_ORG_DATE_MODIFIED)
        DateSource.FILE_CREATED_DATE -> t(StringKey.SETTINGS_ORG_DATE_CREATED)
    }

fun Strings.dateSourceDescription(value: DateSource): String =
    when (value) {
        DateSource.EXIF_DATE -> t(StringKey.SETTINGS_ORG_DATE_EXIF_DESC)
        DateSource.FILE_MODIFIED_DATE -> t(StringKey.SETTINGS_ORG_DATE_MODIFIED_DESC)
        DateSource.FILE_CREATED_DATE -> t(StringKey.SETTINGS_ORG_DATE_CREATED_DESC)
    }

/** Formats a timestamp as a relative time string (e.g. "2 min ago", "just now"). */
fun Strings.formatRelativeTime(timestampMs: Long): String {
    val diffSec = (System.currentTimeMillis() - timestampMs) / 1000
    return when {
        diffSec < 5 -> t(StringKey.TIME_JUST_NOW)
        diffSec < 60 -> t(StringKey.TIME_SECONDS_AGO, "count" to "$diffSec")
        diffSec < 3600 -> t(StringKey.TIME_MINUTES_AGO, "count" to "${diffSec / 60}")
        diffSec < 86400 -> t(StringKey.TIME_HOURS_AGO, "count" to "${diffSec / 3600}")
        else -> t(StringKey.TIME_DAYS_AGO, "count" to "${diffSec / 86400}")
    }
}
