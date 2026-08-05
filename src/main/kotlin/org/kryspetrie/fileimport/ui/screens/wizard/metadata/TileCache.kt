@file:Suppress("TooManyFunctions", "MagicNumber")
@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

// LRU tile cache (in-memory) with viewport-aware eviction
// ──────────────────────────────────────────────────────────────────────────────

/**
 * In-memory tile cache that prioritizes keeping tiles near the current viewport.
 *
 * Each [MapStyle] has its own independent TileCache instance with its own 2048-tile budget, so
 * street and satellite tiles never evict each other. When eviction is needed, tiles farthest from
 * the viewport center (in tile-space distance) are removed first. This ensures that when we reach
 * capacity, tiles relevant to the current map view are retained while tiles from distant regions
 * are evicted first.
 */
class TileCache(private val maxTiles: Int = 2048) {
    private val cache =
        object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, ImageBitmap>
            ): Boolean {
                // Don't auto-evict; we use viewport-aware eviction instead
                return false
            }
        }

    /** Current viewport center in tile coordinates, used for eviction priority. */
    @Volatile private var viewportCenterZ: Int = 0
    @Volatile private var viewportCenterX: Double = 0.0
    @Volatile private var viewportCenterY: Double = 0.0

    /** Update the viewport center for eviction priority. Called when the camera moves. */
    fun updateViewportCenter(z: Int, x: Double, y: Double) {
        viewportCenterZ = z
        viewportCenterX = x
        viewportCenterY = y
    }

    @Synchronized fun get(key: String): ImageBitmap? = cache[key]

    @Synchronized
    fun put(key: String, bitmap: ImageBitmap) {
        cache[key] = bitmap
        if (cache.size > maxTiles) {
            evictFarTiles()
        }
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    fun size(): Int = cache.size

    /**
     * Evict tiles that are farthest from the viewport center when the cache exceeds capacity. This
     * keeps tiles relevant to the current map view while discarding tiles from areas the user has
     * scrolled away from. Key format: "z/x/y" (style is not included since each cache is
     * style-specific).
     */
    private fun evictFarTiles() {
        if (cache.size <= maxTiles * 9 / 10) return // Only evict down to 90% capacity
        val cz = viewportCenterZ
        val cx = viewportCenterX
        val cy = viewportCenterY
        val toRemove = mutableListOf<String>()
        val sorted =
            cache.entries.sortedByDescending { entry ->
                val parts = entry.key.split("/")
                if (parts.size != 3) return@sortedByDescending Long.MAX_VALUE
                val tz = parts[0].toIntOrNull() ?: return@sortedByDescending Long.MAX_VALUE
                val tx = parts[1].toDoubleOrNull() ?: return@sortedByDescending Long.MAX_VALUE
                val ty = parts[2].toDoubleOrNull() ?: return@sortedByDescending Long.MAX_VALUE
                // Same zoom level is highest priority; adjacent zooms are lower
                val zoomPenalty = if (tz == cz) 0L else (kotlin.math.abs(tz - cz) * 100L)
                // Distance in tile coordinates at the same zoom
                val dist = ((tx - cx) * (tx - cx) + (ty - cy) * (ty - cy)).toLong()
                zoomPenalty + dist
            }
        val targetRemove = cache.size - maxTiles * 9 / 10
        for (i in 0 until targetRemove.coerceAtLeast(1)) {
            sorted.getOrNull(i)?.key?.let { toRemove.add(it) }
            if (cache.size - toRemove.size <= maxTiles * 9 / 10) break
        }
        for (key in toRemove) {
            cache.remove(key)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
