@file:Suppress("TooManyFunctions", "MagicNumber")
@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File
import org.kryspetrie.fileimport.infrastructure.adapter.Platform

// Disk tile cache with viewport-aware eviction
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Persistent disk cache for map tiles with viewport-aware eviction.
 *
 * Each map style (street, satellite) gets its own subdirectory with an independent size limit (1 GB
 * each). When a style's cache exceeds its budget, tiles far from the current viewport are evicted
 * first (measured by tile-coordinate distance at the same zoom, with a penalty for different zoom
 * levels). This ensures cache space is used for tiles the user is likely to need next rather than
 * tiles from distant regions, and that street and satellite tiles never evict each other.
 */
class DiskTileCache(cacheDir: File = File(Platform.cacheDir, "map-tiles")) {
    private val cacheDir: File
    /** Maximum age in milliseconds before a cached tile is considered stale (7 days). */
    private val maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000
    /** Maximum disk cache size in bytes per style (1 GB each for street and satellite). */
    private val maxCacheBytesPerStyle: Long = 1024L * 1024 * 1024

    /** Current viewport center for eviction priority. */
    @Volatile private var vpZ: Int = 0
    @Volatile private var vpX: Double = 0.0
    @Volatile private var vpY: Double = 0.0

    private val evictionLock = Any()
    private var evictionScheduled = false

    init {
        this.cacheDir = cacheDir
        if (!this.cacheDir.exists()) this.cacheDir.mkdirs()
        // Ensure per-style subdirectories exist
        for (style in MapStyle.entries) {
            val styleDir = File(cacheDir, style.name.lowercase())
            if (!styleDir.exists()) styleDir.mkdirs()
        }
    }

    /** Update viewport center for eviction priority. */
    fun updateViewportCenter(z: Int, x: Double, y: Double) {
        vpZ = z
        vpX = x
        vpY = y
    }

    /** Get the subdirectory for a given map style. */
    private fun styleDir(style: MapStyle): File {
        return File(cacheDir, style.name.lowercase())
    }

    fun get(z: Int, x: Int, y: Int, style: MapStyle = MapStyle.STREET): ByteArray? {
        val file = File(styleDir(style), "${z}_${x}_${y}.png")
        return if (file.exists()) {
            try {
                if (System.currentTimeMillis() - file.lastModified() > maxAgeMs) {
                    file.delete()
                    null
                } else {
                    file.readBytes()
                }
            } catch (_: Exception) {
                null
            }
        } else null
    }

    fun put(z: Int, x: Int, y: Int, bytes: ByteArray, style: MapStyle = MapStyle.STREET) {
        try {
            val dir = styleDir(style)
            if (!dir.exists()) dir.mkdirs()
            File(dir, "${z}_${x}_${y}.png").writeBytes(bytes)
        } catch (_: Exception) {}
    }

    /**
     * Schedule viewport-aware eviction if any style\'s cache exceeds capacity. Each style has its
     * own independent budget. Eviction runs asynchronously to avoid blocking the UI. When over
     * capacity, tiles farthest from the current viewport center are evicted first.
     */
    fun evictIfNeeded() {
        synchronized(evictionLock) {
            if (evictionScheduled) return
            evictionScheduled = true
        }
        Thread {
                try {
                    for (style in MapStyle.entries) {
                        doEvictionForStyle(style)
                    }
                } finally {
                    synchronized(evictionLock) { evictionScheduled = false }
                }
            }
            .start()
    }

    private fun doEvictionForStyle(style: MapStyle) {
        try {
            val dir = styleDir(style)
            if (!dir.exists()) return
            val files = dir.listFiles() ?: return
            val totalSize = files.sumOf { it.length() }
            if (totalSize <= maxCacheBytesPerStyle) return

            val cz = vpZ
            val cx = vpX
            val cy = vpY
            // Parse tile coordinates from filenames and sort by distance from viewport
            val parsed =
                files
                    .mapNotNull { file ->
                        // Format: z_x_y.png
                        val name = file.nameWithoutExtension
                        val parts = name.split("_")
                        if (parts.size != 3) return@mapNotNull null
                        val tz = parts[0].toIntOrNull() ?: return@mapNotNull null
                        val tx = parts[1].toIntOrNull() ?: return@mapNotNull null
                        val ty = parts[2].toIntOrNull() ?: return@mapNotNull null
                        val zPenalty = if (tz == cz) 0L else (kotlin.math.abs(tz - cz) * 100L)
                        val dist = ((tx - cx) * (tx - cx).toLong() + (ty - cy) * (ty - cy).toLong())
                        Triple(file, zPenalty + dist, file.length())
                    }
                    .sortedByDescending { it.second }

            var currentSize = totalSize
            val targetSize = (maxCacheBytesPerStyle * 0.8).toLong()
            for ((file, _, fileSize) in parsed) {
                if (currentSize <= targetSize) break
                if (file.delete()) currentSize -= fileSize
            }
        } catch (_: Exception) {}
    }
}

// Tile loader
// ──────────────────────────────────────────────────────────────────────────────
