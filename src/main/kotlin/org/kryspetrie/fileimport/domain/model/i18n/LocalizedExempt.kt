package org.kryspetrie.fileimport.domain.model.i18n

/**
 * Marks a UI string literal as intentionally not localized.
 *
 * Use sparingly for non-translatable values (file extensions, technical identifiers). Prefer
 * [StringKey] for all user-visible text.
 */
@Target(AnnotationTarget.FILE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class LocalizedExempt(val reason: String = "")
