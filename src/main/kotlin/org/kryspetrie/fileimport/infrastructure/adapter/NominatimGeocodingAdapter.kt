package org.kryspetrie.fileimport.infrastructure.adapter

import java.net.HttpURLConnection
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kryspetrie.fileimport.domain.model.LocationResult
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.GeocodingPort

@Singleton
class NominatimGeocodingAdapter
@Inject
constructor(private val dispatcherProvider: DispatcherProvider) : GeocodingPort {

    private data class CacheEntry(val results: List<LocationResult>, val timestamp: Long)

    private val rateLimiter = Semaphore(1)
    @Volatile private var lastRequestTime: Long = 0L

    private val cache =
        object : LinkedHashMap<String, CacheEntry>(50, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, CacheEntry>?
            ): Boolean {
                return size > 50
            }
        }

    private val cacheLock = Any()

    companion object {
        private const val BASE_URL = "https://nominatim.openstreetmap.org"
        private const val USER_AGENT = "PetrieImageImporter/1.0"
        private const val CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes
        private const val TAG = "[NominatimGeocodingAdapter]"
    }

    override suspend fun search(query: String, limit: Int): List<LocationResult> {
        val cached = getFromCache(query)
        if (cached != null) return cached

        return withContext(dispatcherProvider.io) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url =
                    "$BASE_URL/search?q=$encodedQuery&format=json&addressdetails=1&limit=$limit"

                val responseBody =
                    executeRateLimited(url) ?: return@withContext emptyList<LocationResult>()

                val results = parseSearchResponse(responseBody)
                putInCache(query, results)
                results
            } catch (e: Exception) {
                System.err.println("$TAG Error in search: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun reverseGeocode(lat: Double, lon: Double): LocationResult? {
        val cacheKey = "reverse:$lat:$lon"
        val cached = getFromCache(cacheKey)
        if (cached != null && cached.isNotEmpty()) return cached.first()

        return withContext(dispatcherProvider.io) {
            try {
                val url = "$BASE_URL/reverse?lat=$lat&lon=$lon&format=json&addressdetails=1"

                val responseBody = executeRateLimited(url) ?: return@withContext null

                val result = parseReverseResponse(responseBody)
                if (result != null) {
                    putInCache(cacheKey, listOf(result))
                }
                result
            } catch (e: Exception) {
                System.err.println("$TAG Error in reverse: ${e.message}")
                null
            }
        }
    }

    private suspend fun executeRateLimited(url: String): String? {
        return rateLimiter.withPermit {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            val remaining = 1000L - elapsed
            if (remaining > 0) {
                delay(remaining)
            }

            try {
                val connection = java.net.URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                lastRequestTime = System.currentTimeMillis()

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    System.err.println("$TAG HTTP error: $responseCode for $url")
                    return@withPermit null
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                body
            } catch (e: Exception) {
                lastRequestTime = System.currentTimeMillis()
                System.err.println("$TAG Network error: ${e.message}")
                return@withPermit null
            }
        }
    }

    internal fun parseSearchResponse(body: String): List<LocationResult> {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val array = json.parseToJsonElement(body).jsonArray

            array.mapNotNull { element ->
                val obj = element.jsonObject
                val displayName =
                    obj["display_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val lat =
                    obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val lon =
                    obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val address = obj["address"]?.jsonObject

                val city = extractCity(address)
                val state = address?.get("state")?.jsonPrimitive?.content
                val country = address?.get("country")?.jsonPrimitive?.content
                val name =
                    obj["name"]?.jsonPrimitive?.content ?: city ?: displayName.substringBefore(",")
                val osmType = obj["osm_type"]?.jsonPrimitive?.content
                val osmId = obj["osm_id"]?.jsonPrimitive?.content?.toLongOrNull()

                LocationResult(
                    displayName = displayName,
                    name = name,
                    latitude = lat,
                    longitude = lon,
                    city = city,
                    state = state,
                    country = country,
                    osmType = osmType,
                    osmId = osmId,
                )
            }
        } catch (e: Exception) {
            System.err.println("$TAG Error parsing search response: ${e.message}")
            emptyList()
        }
    }

    internal fun parseReverseResponse(body: String): LocationResult? {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val obj = json.parseToJsonElement(body).jsonObject

            val displayName = obj["display_name"]?.jsonPrimitive?.content ?: return null
            val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
            val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
            val address = obj["address"]?.jsonObject

            val name =
                address?.get("road")?.jsonPrimitive?.content
                    ?: address?.get("city")?.jsonPrimitive?.content
                    ?: address?.get("town")?.jsonPrimitive?.content

            val city = extractCity(address)
            val state = address?.get("state")?.jsonPrimitive?.content
            val country = address?.get("country")?.jsonPrimitive?.content
            val resolvedName = name ?: city ?: displayName.substringBefore(",")

            LocationResult(
                displayName = displayName,
                name = resolvedName,
                latitude = lat,
                longitude = lon,
                city = city,
                state = state,
                country = country,
            )
        } catch (e: Exception) {
            System.err.println("$TAG Error parsing reverse response: ${e.message}")
            null
        }
    }

    internal fun extractCity(address: kotlinx.serialization.json.JsonObject?): String? {
        if (address == null) return null
        return address["city"]?.jsonPrimitive?.content
            ?: address["town"]?.jsonPrimitive?.content
            ?: address["village"]?.jsonPrimitive?.content
            ?: address["suburb"]?.jsonPrimitive?.content
    }

    private fun getFromCache(key: String): List<LocationResult>? {
        synchronized(cacheLock) {
            val entry = cache[key] ?: return null
            val age = System.currentTimeMillis() - entry.timestamp
            if (age > CACHE_TTL_MS) {
                cache.remove(key)
                return null
            }
            return entry.results
        }
    }

    private fun putInCache(key: String, results: List<LocationResult>) {
        synchronized(cacheLock) { cache[key] = CacheEntry(results, System.currentTimeMillis()) }
    }
}
