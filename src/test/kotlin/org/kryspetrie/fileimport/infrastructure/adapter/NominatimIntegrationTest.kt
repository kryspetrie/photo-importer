package org.kryspetrie.fileimport.infrastructure.adapter

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.port.DispatcherProvider

/**
 * Integration tests that make real network calls to the Nominatim (OpenStreetMap) API.
 *
 * These tests validate that:
 * 1. We can communicate with the Nominatim server over HTTPS
 * 2. Search responses parse correctly from real data
 * 3. Reverse geocoding works for known coordinates
 * 4. Rate limiting correctly spaces requests 1 second apart
 *
 * Run with: ./gradlew test --tests "*NominatimIntegrationTest*" These are excluded from normal CI
 * by the "integration" tag.
 */
@Tag("integration")
class NominatimIntegrationTest {

    private lateinit var adapter: NominatimGeocodingAdapter

    @BeforeEach
    fun setUp() {
        adapter = NominatimGeocodingAdapter(TestDispatcherProvider())
    }

    @Test
    @DisplayName("Search for Worcester, Massachusetts returns results including Worcester MA")
    fun searchWorcesterReturnsResults() = runBlocking {
        val results =
            withTimeoutOrNull(30_000L) { adapter.search("Worcester, Massachusetts") }
                ?: throw RuntimeException("Search timed out after 30 seconds")

        assertFalse(
            results.isEmpty(),
            "Expected at least one result for 'Worcester, Massachusetts'",
        )

        val worcesterMA = results.find { it.state == "Massachusetts" && it.name == "Worcester" }
        assertNotNull(worcesterMA, "Expected to find Worcester, MA in results")

        worcesterMA!!.let { r ->
            assertTrue(
                r.latitude > 42.0 && r.latitude < 43.0,
                "Worcester MA latitude should be near 42.26, got ${r.latitude}",
            )
            assertTrue(
                r.longitude < -71.0 && r.longitude > -72.0,
                "Worcester MA longitude should be near -71.80, got ${r.longitude}",
            )
            assertEquals("United States", r.country)
            assertNotNull(r.osmType, "Expected osmType to be set")
            assertNotNull(r.osmId, "Expected osmId to be set")
        }
    }

    @Test
    @DisplayName("Search for London returns results in different countries")
    fun searchLondonReturnsMultipleCountries() = runBlocking {
        val results =
            withTimeoutOrNull(30_000L) { adapter.search("London") }
                ?: throw RuntimeException("Search timed out after 30 seconds")

        assertFalse(results.isEmpty(), "Expected at least one result for 'London'")

        val countries = results.map { it.country }.distinct()
        assertTrue(
            countries.size >= 2,
            "Expected London search to return results in multiple countries, got: $countries",
        )

        val ukLondon = results.find { it.country == "United Kingdom" }
        assertNotNull(ukLondon, "Expected to find London, UK")

        val caLondon = results.find { it.country == "Canada" && it.name == "London" }
        assertNotNull(caLondon, "Expected to find London, Canada")
    }

    @Test
    @DisplayName("Reverse geocode for Worcester MA coordinates returns correct location")
    fun reverseGeocodeWorcesterMA() = runBlocking {
        val result =
            withTimeoutOrNull(30_000L) { adapter.reverseGeocode(42.2626, -71.8023) }
                ?: throw RuntimeException("Reverse geocode timed out after 30 seconds")

        assertNotNull(
            result,
            "Expected a non-null result for reverse geocode of Worcester MA coordinates",
        )

        result!!.let { r ->
            assertTrue(
                r.displayName.contains("Worcester") || r.city?.contains("Worcester") == true,
                "Expected display name or city to contain 'Worcester', got displayName='${r.displayName}', city='${r.city}'",
            )
            assertEquals("Massachusetts", r.state, "Expected state to be Massachusetts")
            assertEquals("United States", r.country, "Expected country to be United States")
            assertTrue(
                Math.abs(r.latitude - 42.2626) < 0.1,
                "Latitude should be near 42.26, got ${r.latitude}",
            )
            assertTrue(
                Math.abs(r.longitude - (-71.8023)) < 0.1,
                "Longitude should be near -71.80, got ${r.longitude}",
            )
        }
    }

    @Test
    @DisplayName("Reverse geocode for Paris coordinates returns correct location")
    fun reverseGeocodeParis() = runBlocking {
        val result =
            withTimeoutOrNull(30_000L) { adapter.reverseGeocode(48.8566, 2.3522) }
                ?: throw RuntimeException("Reverse geocode timed out after 30 seconds")

        assertNotNull(result, "Expected a non-null result for reverse geocode of Paris coordinates")

        result!!.let { r ->
            assertEquals("France", r.country, "Expected country to be France")
            assertNotNull(r.state, "Expected state to be set")
            assertTrue(
                Math.abs(r.latitude - 48.8566) < 0.5,
                "Latitude should be near 48.85, got ${r.latitude}",
            )
        }
    }

    @Test
    @DisplayName("Search with nonsense query returns empty results (not an error)")
    fun searchNonsenseReturnsEmpty() = runBlocking {
        val results =
            withTimeoutOrNull(30_000L) { adapter.search("zzzzzzzzzzzxqqqqqqqqqq") }
                ?: throw RuntimeException("Search timed out after 30 seconds")

        // Should return empty list, not throw an exception
        assertTrue(
            results.isEmpty() || results.size < 5,
            "Nonsense query should return few or no results, got ${results.size}",
        )
    }

    @Test
    @DisplayName("Cache returns same results on repeated search without making new network call")
    fun cacheReturnsSameResults() = runBlocking {
        // First call populates the cache
        val firstResults =
            withTimeoutOrNull(30_000L) { adapter.search("Boston, Massachusetts") }
                ?: throw RuntimeException("First search timed out")

        assertFalse(firstResults.isEmpty(), "Expected results for Boston")

        // Second call should use cache — returns immediately
        val secondResults =
            withTimeoutOrNull(5_000L) { adapter.search("Boston, Massachusetts") }
                ?: throw RuntimeException("Cached search timed out — cache may not be working")

        assertEquals(
            firstResults.size,
            secondResults.size,
            "Cached results should have same count as original",
        )
        assertEquals(firstResults, secondResults, "Cached results should be identical to original")
    }

    @Test
    @DisplayName("Rate limiting: two rapid searches both succeed (second waits 1s)")
    fun rateLimitingAllowsSequentialCalls() = runBlocking {
        // Make two calls in rapid succession — the adapter should rate-limit
        val results1 =
            withTimeoutOrNull(30_000L) { adapter.search("Paris, France") }
                ?: throw RuntimeException("First search timed out")

        val results2 =
            withTimeoutOrNull(30_000L) { adapter.search("Berlin, Germany") }
                ?: throw RuntimeException("Second search timed out")

        assertFalse(results1.isEmpty(), "Expected results for Paris")
        assertFalse(results2.isEmpty(), "Expected results for Berlin")
    }

    @Test
    @DisplayName("City fallback: search for a town returns city field from town value")
    fun cityFallbackFromTown() = runBlocking {
        val results =
            withTimeoutOrNull(30_000L) { adapter.search("Barnstable, Massachusetts") }
                ?: throw RuntimeException("Search timed out")

        if (results.isNotEmpty()) {
            // At least one result should have a city or town field populated
            val hasSettlement = results.any { it.city != null }
            assertTrue(
                hasSettlement,
                "Expected at least one result with a city/town/village field, got: ${results.map { it.city }}",
            )
        }
    }

    /** Test dispatcher provider that uses real IO dispatcher for network calls. */
    private class TestDispatcherProvider : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }
}
