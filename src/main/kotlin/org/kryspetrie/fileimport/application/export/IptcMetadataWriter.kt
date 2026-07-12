package org.kryspetrie.fileimport.application.export

import java.io.ByteArrayOutputStream
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.jpeg.iptc.IptcRecord
import org.apache.commons.imaging.formats.jpeg.iptc.IptcTypes
import org.apache.commons.imaging.formats.jpeg.iptc.JpegIptcRewriter
import org.apache.commons.imaging.formats.jpeg.iptc.PhotoshopApp13Data
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.port.FileSystemPort

/**
 * Writes IPTC keywords, location, and subject data into existing JPEG files using the Photoshop
 * APP13 segment.
 *
 * IPTC:Keywords (record 2, dataset 25) is the cross-platform standard for keywords that macOS
 * Preview, Photos.app, and Finder all read.
 *
 * IPTC location fields (SubLocation, City, Province/State, Country) map to photoshop:* XMP fields
 * and are read by Lightroom, Bridge, digiKam, Apple Photos, etc.
 *
 * @param fileSystem Port for file I/O operations (replaces direct `java.io.File` usage)
 */
class IptcMetadataWriter(private val fileSystem: FileSystemPort) {

    /**
     * Writes IPTC keywords, location, and subject data into an existing JPEG file.
     *
     * @param jpegPath The JPEG file path to rewrite with IPTC data (modified in-place)
     * @param keywordsValue Comma-separated keyword string (may be null if no keywords)
     * @param config Configuration with location and subject fields
     */
    fun writeIptcData(jpegPath: FilePath, keywordsValue: String?, config: PhotoScanConfiguration) {
        try {
            // Read existing JPEG bytes
            val jpegBytes = fileSystem.readBytes(jpegPath)

            // Read existing IPTC data from the JPEG if present
            val metadata = Imaging.getMetadata(jpegBytes)
            val existingRecords =
                if (metadata is JpegImageMetadata) {
                    val photoshop = metadata.photoshop
                    photoshop?.photoshopApp13Data?.records?.filterNotNull()?.toMutableList()
                        ?: mutableListOf()
                } else {
                    mutableListOf()
                }

            // Remove existing KEYWORDS records (we'll replace them)
            existingRecords.removeAll { it.iptcType == IptcTypes.KEYWORDS }

            // Add keyword records from the keywords field
            if (keywordsValue != null) {
                val keywordList =
                    keywordsValue.split(",").map { it.trim() }.filter { it.isNotBlank() }
                for (keyword in keywordList) {
                    existingRecords.add(IptcRecord(IptcTypes.KEYWORDS, keyword))
                }
            }

            // Add subject/person names as additional keyword records for compatibility
            if (config.subjects.isNotBlank()) {
                val subjectList =
                    config.subjects.split(",").map { it.trim() }.filter { it.isNotBlank() }
                for (subject in subjectList) {
                    // Only add if not already in keywords (avoid duplicates)
                    val keywordMatches =
                        existingRecords.filter {
                            it.iptcType == IptcTypes.KEYWORDS && it.value == subject
                        }
                    if (keywordMatches.isEmpty()) {
                        existingRecords.add(IptcRecord(IptcTypes.KEYWORDS, subject))
                    }
                }
            }

            // --- IPTC Location fields ---
            existingRecords.removeAll { it.iptcType == IptcTypes.SUBLOCATION }
            existingRecords.removeAll { it.iptcType == IptcTypes.CITY }
            existingRecords.removeAll { it.iptcType == IptcTypes.PROVINCE_STATE }
            existingRecords.removeAll { it.iptcType == IptcTypes.COUNTRY_PRIMARY_LOCATION_NAME }
            existingRecords.removeAll { it.iptcType == IptcTypes.COUNTRY_PRIMARY_LOCATION_CODE }

            if (config.address.isNotBlank()) {
                existingRecords.add(IptcRecord(IptcTypes.SUBLOCATION, config.address.trim()))
            } else if (config.locationName.isNotBlank()) {
                existingRecords.add(IptcRecord(IptcTypes.SUBLOCATION, config.locationName.trim()))
            }
            if (config.city.isNotBlank()) {
                existingRecords.add(IptcRecord(IptcTypes.CITY, config.city.trim()))
            }
            if (config.state.isNotBlank()) {
                existingRecords.add(IptcRecord(IptcTypes.PROVINCE_STATE, config.state.trim()))
            }
            if (config.country.isNotBlank()) {
                existingRecords.add(
                    IptcRecord(IptcTypes.COUNTRY_PRIMARY_LOCATION_NAME, config.country.trim())
                )
            }

            // Build the Photoshop APP13 data block
            val existingBlocks =
                if (metadata is JpegImageMetadata) {
                    metadata.photoshop?.photoshopApp13Data?.nonIptcBlocks?.filterNotNull()
                        ?: emptyList()
                } else {
                    emptyList()
                }
            val app13Data = PhotoshopApp13Data(existingRecords, existingBlocks)

            // Rewrite the JPEG with the new IPTC data
            val baos = ByteArrayOutputStream()
            JpegIptcRewriter().writeIPTC(jpegBytes, baos, app13Data)
            baos.close()

            // Write updated JPEG back to file
            fileSystem.writeBytes(jpegPath, baos.toByteArray())
        } catch (e: Exception) {
            // IPTC writing is best-effort — don't fail the export
            System.err.println(
                "[IptcMetadataWriter] Warning: Failed to write IPTC data: ${e.message}"
            )
        }
    }
}
