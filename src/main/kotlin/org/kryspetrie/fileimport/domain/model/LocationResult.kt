package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a geocoding result from Nominatim/OpenStreetMap.
 *
 * Maps the essential fields from a Nominatim response into a domain-level model, providing a
 * normalized view of a location regardless of the specific OSM element type.
 *
 * The [city] field is populated from the most specific settlement field available in the Nominatim
 * address object (city → town → village → suburb).
 *
 * @property displayName Full display name, e.g. "Worcester, Massachusetts, United States"
 * @property name Short name, e.g. "Worcester"
 * @property city Populated from Nominatim address.city (falls back to address.town,
 *   address.village, address.suburb)
 * @property state From address.state
 * @property country From address.country
 * @property latitude Decimal degrees
 * @property longitude Decimal degrees
 * @property osmType OSM element type: "relation", "way", or "node"
 * @property osmId OSM element ID
 */
@Serializable
data class LocationResult(
    val displayName: String,
    val name: String,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
    val osmType: String? = null,
    val osmId: Long? = null,
)
