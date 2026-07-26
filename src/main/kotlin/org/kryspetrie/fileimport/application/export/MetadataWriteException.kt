package org.kryspetrie.fileimport.application.export

/**
 * Exception thrown when metadata writing fails.
 *
 * Previously, all metadata writers (EXIF, IPTC, XMP) swallowed exceptions to stderr,
 * producing valid-but-wrong files with no failure signal. This exception propagates
 * write failures so callers can report errors to the user.
 */
class MetadataWriteException(message: String, cause: Throwable? = null) : Exception(message, cause)