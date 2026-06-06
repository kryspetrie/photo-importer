package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.LocationResult

/**
 * Domain port for geocoding operations.
 *
 * Provides forward and reverse geocoding capabilities through a hexagonal-architecture port,
 * keeping the domain layer independent of any specific geocoding provider.
 *
 * The default adapter implementation uses the Nominatim (OpenStreetMap) geocoding service, but this
 * interface allows for alternative providers or mock implementations in testing.
 */
interface GeocodingPort {

    /**
     * Forward geocoding: search for locations matching the given query string.
     *
     * @param query The search text, e.g. "Worcester, MA"
     * @param limit Maximum number of results to return (default 10)
     * @return List of matching [LocationResult] entries, ordered by relevance
     */
    suspend fun search(query: String, limit: Int = 10): List<LocationResult>

    /**
     * Reverse geocoding: look up the location at the given coordinates.
     *
     * @param lat Latitude in decimal degrees
     * @param lon Longitude in decimal degrees
     * @return The matching [LocationResult], or null if no result was found
     */
    suspend fun reverseGeocode(lat: Double, lon: Double): LocationResult?
}
