package org.kryspetrie.fileimport.application.export

import java.util.Locale
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.xmp.JpegXmpRewriter
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.port.FileSystemPort

/**
 * Writes XMP face region data (MWG-RS Regions) into existing JPEG files.
 *
 * MWG-RS (Metadata Working Group - Region Schema) uses normalized center-based coordinates (x/y =
 * center, w/h = fractions) stored in XMP, readable by Lightroom, digiKam, etc.
 *
 * @param fileSystem Port for file I/O operations (replaces direct `java.io.File` usage)
 */
class XmpMetadataWriter(private val fileSystem: FileSystemPort) {

    /**
     * Writes XMP face region data (MWG-RS Regions) into an existing JPEG file.
     *
     * Reads any existing XMP, merges in the face regions, and rewrites. If no existing XMP is
     * found, a fresh XMP packet is created.
     *
     * @param jpegPath The JPEG file path to rewrite with XMP data (modified in-place)
     * @param config Configuration with face region data
     */
    fun writeXmpFaceRegions(jpegPath: FilePath, config: PhotoScanConfiguration) {
        try {
            // Build the MWG-RS region XMP fragment
            val regions = config.faceRegions
            if (regions.isEmpty()) return

            val regionEntries =
                regions.joinToString("\n") { region ->
                    """        <rdf:Description rdf:about=""
                   mwg-rs:Name="${escapeXml(region.name)}"
                   mwg-rs:Type="${escapeXml(region.type)}"
                   mwg-rs:Area="
                    x='${formatDecimal(region.x)}'
                    y='${formatDecimal(region.y)}'
                    w='${formatDecimal(region.w)}'
                    h='${formatDecimal(region.h)}'
                    unit='normalized'"/>"""
                }

            val mwgRsXmp =
                """
        |<rdf:Description rdf:about=''
        |   xmlns:mwg-rs='http://www.metadataworkinggroup.com/schemas/regions/'>
        |  <mwg-rs:Regions>
        |    <rdf:Alt>
        |$regionEntries
        |    </rdf:Alt>
        |  </mwg-rs:Regions>
        |</rdf:Description>
            """
                    .trimMargin()

            // Read existing JPEG bytes
            val jpegBytes = fileSystem.readBytes(jpegPath)

            // Read existing XMP from the JPEG if present
            val existingXmp: String? =
                try {
                    Imaging.getXmpXml(jpegBytes)
                } catch (_: Exception) {
                    null
                }

            val newXmp: String =
                if (!existingXmp.isNullOrBlank()) {
                    // Merge: insert MWG-RS into existing XMP before the closing </x:xmpmeta>
                    val existing = existingXmp
                    val closingTag = "</x:xmpmeta>"
                    if (existing.contains(closingTag)) {
                        existing.replace(closingTag, "$mwgRsXmp\n$closingTag")
                    } else {
                        "$existing\n$mwgRsXmp"
                    }
                } else {
                    // Create a fresh XMP packet
                    """<?xpacket begin='${'\uFEFF'}' id='W5M0MpCehiHzreSzNTczkc9d'?>
<x:xmpmeta xmlns:x='adobe:ns:meta/'>
<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>
$mwgRsXmp
</rdf:RDF>
</x:xmpmeta>
<?xpacket end='w'?>"""
                        .trimIndent()
                }

            // Write the XMP back into the JPEG
            val baos = java.io.ByteArrayOutputStream()
            JpegXmpRewriter().updateXmpXml(jpegBytes, baos, newXmp)
            baos.close()

            fileSystem.writeBytes(jpegPath, baos.toByteArray())
        } catch (e: Exception) {
            // XMP writing is best-effort — don't fail the export
            System.err.println(
                "[XmpMetadataWriter] Warning: Failed to write XMP face regions: ${e.message}"
            )
        }
    }

    /** Escapes special XML characters in attribute values. */
    fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    /** Formats a double to 6 decimal places for MWG-RS coordinates. */
    fun formatDecimal(value: Double): String = String.format(Locale.US, "%.6f", value)
}