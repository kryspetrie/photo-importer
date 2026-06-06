package org.kryspetrie.fileimport.infrastructure.adapter

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.LocationResult
import org.kryspetrie.fileimport.domain.port.DispatcherProvider

/**
 * Unit tests for NominatimGeocodingAdapter.
 *
 * These tests validate JSON parsing, city fallback logic, and cache behavior without making real
 * network calls. Network integration tests are in [NominatimIntegrationTest].
 */
class NominatimGeocodingAdapterTest {

    private lateinit var adapter: NominatimGeocodingAdapter

    @BeforeEach
    fun setUp() {
        adapter = NominatimGeocodingAdapter(TestDispatcherProvider())
    }

    @Nested
    @DisplayName("parseSearchResponse")
    inner class SearchParsing {

        @Test
        @DisplayName("parses a valid two-result Nominatim search response")
        fun parseValidSearchResponse() {
            val json =
                """[
                {
                    "place_id": 237942957,
                    "licence": "Data © OpenStreetMap contributors",
                    "osm_type": "relation",
                    "osm_id": 60829352,
                    "lat": "42.262596",
                    "lon": "-71.8022945",
                    "display_name": "Worcester, Worcester County, Massachusetts, United States",
                    "name": "Worcester",
                    "address": {
                        "city": "Worcester",
                        "state": "Massachusetts",
                        "country": "United States"
                    }
                },
                {
                    "place_id": 347330,
                    "osm_type": "relation",
                    "osm_id": 347330,
                    "lat": "-33.6806",
                    "lon": "18.9983",
                    "display_name": "Worcester, Cape Winelands District Municipality, Western Cape, South Africa",
                    "name": "Worcester",
                    "address": {
                        "town": "Worcester",
                        "state": "Western Cape",
                        "country": "South Africa"
                    }
                }
            ]"""

            val results = adapter.parseSearchResponse(json)

            assertEquals(2, results.size)

            val worcester = results[0]
            assertEquals("Worcester", worcester.name)
            assertEquals("Worcester", worcester.city)
            assertEquals("Massachusetts", worcester.state)
            assertEquals("United States", worcester.country)
            assertEquals(42.262596, worcester.latitude, 0.0001)
            assertEquals(-71.8022945, worcester.longitude, 0.0001)
            assertEquals("relation", worcester.osmType)
            assertEquals(60829352L, worcester.osmId)

            val southAfrica = results[1]
            assertEquals("Worcester", southAfrica.name)
            assertEquals("Worcester", southAfrica.city) // town → city fallback
            assertEquals("Western Cape", southAfrica.state)
            assertEquals("South Africa", southAfrica.country)
            assertEquals(-33.6806, southAfrica.latitude, 0.0001)
            assertEquals(18.9983, southAfrica.longitude, 0.0001)
            assertEquals(347330L, southAfrica.osmId)
        }

        @Test
        @DisplayName("returns empty list for malformed JSON")
        fun parseMalformedJson() {
            val results = adapter.parseSearchResponse("not valid json")
            assertEquals(emptyList<LocationResult>(), results)
        }

        @Test
        @DisplayName("skips entries missing required lat/lon fields")
        fun parsePartialEntries() {
            val json =
                """[
                {
                    "display_name": "No Coords",
                    "name": "No Coords",
                    "address": {}
                },
                {
                    "display_name": "Valid Place",
                    "lat": "10.0",
                    "lon": "20.0",
                    "name": "Valid Place",
                    "address": {
                        "country": "Nowhere"
                    }
                }
            ]"""

            val results = adapter.parseSearchResponse(json)
            assertEquals(1, results.size)
            assertEquals("Valid Place", results[0].name)
        }

        @Test
        @DisplayName("falls back to city name when name field is missing")
        fun nameFallsBackToCity() {
            val json =
                """[
                {
                    "display_name": "Springfield, Illinois, United States",
                    "lat": "39.7817",
                    "lon": "-89.6501",
                    "address": {
                        "city": "Springfield",
                        "state": "Illinois"
                    }
                }
            ]"""

            val results = adapter.parseSearchResponse(json)
            assertEquals("Springfield", results[0].name)
        }

        @Test
        @DisplayName("falls back to first comma-delimited segment when name and city are missing")
        fun nameFallsBackToDisplayName() {
            val json =
                """[
                {
                    "display_name": "Some Unknown Place, Province, Country",
                    "lat": "1.0",
                    "lon": "2.0",
                    "address": {
                        "country": "Country"
                    }
                }
            ]"""

            val results = adapter.parseSearchResponse(json)
            assertEquals("Some Unknown Place", results[0].name)
        }
    }

    @Nested
    @DisplayName("parseReverseResponse")
    inner class ReverseParsing {

        @Test
        @DisplayName("parses a valid reverse geocoding response")
        fun parseValidReverseResponse() {
            val json =
                """{
                "place_id": 123456,
                "lat": "42.262596",
                "lon": "-71.8022945",
                "display_name": "9 Main Street, Worcester, Worcester County, Massachusetts, 01608, United States",
                "address": {
                    "house_number": "9",
                    "road": "Main Street",
                    "city": "Worcester",
                    "state": "Massachusetts",
                    "postcode": "01608",
                    "country": "United States"
                }
            }"""

            val result = adapter.parseReverseResponse(json)
            assertNotNull(result)
            assertEquals("Main Street", result!!.name) // road → name fallback
            assertEquals("Worcester", result.city)
            assertEquals("Massachusetts", result.state)
            assertEquals("United States", result.country)
            assertEquals(42.262596, result.latitude, 0.0001)
            assertEquals(-71.8022945, result.longitude, 0.0001)
        }

        @Test
        @DisplayName("returns null for malformed reverse response JSON")
        fun parseMalformedReverseResponse() {
            val result = adapter.parseReverseResponse("{bad json}")
            assertNull(result)
        }

        @Test
        @DisplayName("returns null when lat/lon are missing from reverse response")
        fun parseReverseResponseMissingLatLon() {
            val json =
                """{
                "display_name": "Somewhere",
                "address": { "country": "Country" }
            }"""

            val result = adapter.parseReverseResponse(json)
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("extractCity fallback chain")
    inner class CityFallback {

        @Test
        @DisplayName("prefers city over town/village/suburb")
        fun prefersCity() {
            val addressJson =
                """{
                "city": "Metro City",
                "town": "Small Town",
                "village": "Tiny Village",
                "suburb": "Suburban Area"
            }"""
            val address = Json.parseToJsonElement(addressJson).jsonObject
            assertEquals("Metro City", adapter.extractCity(address))
        }

        @Test
        @DisplayName("falls back to town when city is absent")
        fun fallsBackToTown() {
            val addressJson =
                """{
                "town": "Small Town",
                "village": "Tiny Village",
                "suburb": "Suburban Area"
            }"""
            val address = Json.parseToJsonElement(addressJson).jsonObject
            assertEquals("Small Town", adapter.extractCity(address))
        }

        @Test
        @DisplayName("falls back to village when city and town are absent")
        fun fallsBackToVillage() {
            val addressJson =
                """{
                "village": "Tiny Village",
                "suburb": "Suburban Area"
            }"""
            val address = Json.parseToJsonElement(addressJson).jsonObject
            assertEquals("Tiny Village", adapter.extractCity(address))
        }

        @Test
        @DisplayName("falls back to suburb when city, town, and village are absent")
        fun fallsBackToSuburb() {
            val addressJson =
                """{
                "suburb": "Suburban Area"
            }"""
            val address = Json.parseToJsonElement(addressJson).jsonObject
            assertEquals("Suburban Area", adapter.extractCity(address))
        }

        @Test
        @DisplayName("returns null when no city-like field is present")
        fun returnsNullWhenNoCityField() {
            val addressJson =
                """{
                "state": "Some State",
                "country": "Some Country"
            }"""
            val address = Json.parseToJsonElement(addressJson).jsonObject
            assertNull(adapter.extractCity(address))
        }

        @Test
        @DisplayName("returns null for null address")
        fun returnsNullForNullAddress() {
            assertNull(adapter.extractCity(null))
        }
    }

    @Nested
    @DisplayName("LocationResult data class")
    inner class DataClassTests {

        @Test
        @DisplayName("LocationResult copy works correctly")
        fun locationResultCopy() {
            val original =
                LocationResult(
                    displayName = "Paris, France",
                    name = "Paris",
                    city = "Paris",
                    state = "Île-de-France",
                    country = "France",
                    latitude = 48.8566,
                    longitude = 2.3522,
                )
            val modified = original.copy(city = "Paris 01", state = "Île-de-France")
            assertEquals("Paris 01", modified.city)
            assertEquals(original.name, modified.name)
            assertEquals(original.latitude, modified.latitude, 0.001)
        }

        @Test
        @DisplayName("LocationResult serialization round-trips")
        fun locationResultSerialization() {
            val result =
                LocationResult(
                    displayName = "London, England, United Kingdom",
                    name = "London",
                    city = "London",
                    state = "England",
                    country = "United Kingdom",
                    latitude = 51.5074,
                    longitude = -0.1278,
                    osmType = "relation",
                    osmId = 65606,
                )
            val json =
                kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.serializer<LocationResult>(),
                    result,
                )
            val decoded =
                kotlinx.serialization.json.Json.decodeFromString(
                    kotlinx.serialization.serializer<LocationResult>(),
                    json,
                )
            assertEquals(result, decoded)
        }

        @Test
        @DisplayName("LocationResult default values work")
        fun locationResultDefaults() {
            val result =
                LocationResult(
                    displayName = "Unknown",
                    name = "Unknown",
                    latitude = 0.0,
                    longitude = 0.0,
                )
            assertNull(result.city)
            assertNull(result.state)
            assertNull(result.country)
            assertNull(result.osmType)
            assertNull(result.osmId)
        }
    }

    /** Test dispatcher provider that uses Dispatchers.Default for IO. */
    private class TestDispatcherProvider : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.Default
        override val default: CoroutineDispatcher = Dispatchers.Default
    }
}
