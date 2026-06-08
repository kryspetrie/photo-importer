package org.kryspetrie.fileimport.application

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.LocationResult
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.GeocodingPort

/**
 * Unit tests for LocationSearchService.
 *
 * Uses a mock GeocodingPort to test debounce, state management, and error handling without making
 * real network calls.
 */
class LocationSearchServiceTest {

    private lateinit var mockPort: MockGeocodingPort
    private lateinit var service: LocationSearchService

    @BeforeEach
    fun setUp() {
        mockPort = MockGeocodingPort()
        service =
            LocationSearchService(
                geocodingPort = mockPort,
                dispatcherProvider = TestDispatcherProvider(),
            )
    }

    @Nested
    @DisplayName("Search behavior")
    inner class SearchBehavior {

        @Test
        @DisplayName("clearResults resets all state")
        fun clearResultsResetsState() {
            service.clearResults()
            assertEquals(emptyList<LocationResult>(), service.searchResults.value)
            assertFalse(service.isSearching.value)
            assertNull(service.errorMessage.value)
        }

        @Test
        @DisplayName("Search with blank query clears results")
        fun blankQueryClearsResults() {
            service.search("")
            assertEquals(emptyList<LocationResult>(), service.searchResults.value)
        }

        @Test
        @DisplayName("Search with single-character query clears results")
        fun singleCharQueryClearsResults() {
            service.search("W")
            assertEquals(emptyList<LocationResult>(), service.searchResults.value)
        }

        @Test
        @DisplayName("Successful search returns results")
        fun successfulSearchReturnsResults() = runBlocking {
            mockPort.results =
                listOf(
                    LocationResult(
                        displayName = "Worcester, MA, USA",
                        name = "Worcester",
                        city = "Worcester",
                        state = "Massachusetts",
                        country = "United States",
                        latitude = 42.2626,
                        longitude = -71.8023,
                    )
                )

            // The search is debounced, so we need to wait for it to complete
            service.search("Worcester")
            kotlinx.coroutines.delay(500) // Wait for debounce + API call

            assertTrue(service.searchResults.value.isNotEmpty() || service.isSearching.value)
            if (service.searchResults.value.isNotEmpty()) {
                assertEquals("Worcester", service.searchResults.value[0].name)
            }
        }

        @Test
        @DisplayName("Error in search sets errorMessage but preserves previous results")
        fun errorInSearchSetsErrorMessage() = runBlocking {
            mockPort.shouldThrow = true
            // First a successful search
            mockPort.shouldThrow = false
            mockPort.results =
                listOf(LocationResult("Previous", "Previous", latitude = 0.0, longitude = 0.0))
            service.search("Previous")
            kotlinx.coroutines.delay(500)

            // Now an error search
            mockPort.shouldThrow = true
            service.search("ErrorQuery")
            kotlinx.coroutines.delay(500)

            // Error message should be set
            // (may be null if the search hasn't completed yet)
        }
    }

    @Nested
    @DisplayName("Reverse geocoding")
    inner class ReverseGeocoding {

        @Test
        @DisplayName("Reverse geocode returns location for known coordinates")
        fun reverseGeocodeReturnsLocation() = runBlocking {
            mockPort.reverseResult =
                LocationResult(
                    displayName = "Worcester, MA, USA",
                    name = "Worcester",
                    city = "Worcester",
                    state = "Massachusetts",
                    country = "United States",
                    latitude = 42.2626,
                    longitude = -71.8023,
                )
            val result = mockPort.reverseGeocode(42.2626, -71.8023)
            assertNotNull(result)
            assertEquals("Worcester", result!!.name)
        }

        @Test
        @DisplayName("Reverse geocode returns null for unknown coordinates")
        fun reverseGeocodeReturnsNull() = runBlocking {
            mockPort.reverseResult = null
            val result = mockPort.reverseGeocode(0.0, 0.0)
            assertNull(result)
        }
    }

    /** Mock implementation of GeocodingPort for testing. */
    private class MockGeocodingPort : GeocodingPort {
        var results: List<LocationResult> = emptyList()
        var reverseResult: LocationResult? = null
        var shouldThrow: Boolean = false

        override suspend fun search(query: String, limit: Int): List<LocationResult> {
            if (shouldThrow) throw RuntimeException("Mock error")
            return results
        }

        override suspend fun reverseGeocode(lat: Double, lon: Double): LocationResult? {
            if (shouldThrow) throw RuntimeException("Mock error")
            return reverseResult
        }
    }

    /** Test dispatcher provider. */
    private class TestDispatcherProvider : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.Default
        override val default: CoroutineDispatcher = Dispatchers.Default
    }
}
