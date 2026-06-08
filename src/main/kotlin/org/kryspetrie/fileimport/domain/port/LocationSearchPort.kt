package org.kryspetrie.fileimport.domain.port

import kotlinx.coroutines.flow.StateFlow
import org.kryspetrie.fileimport.domain.model.LocationResult

/**
 * Port interface for location/geocoding search.
 *
 * Provides asynchronous location search with result streaming. Implementations call external
 * geocoding services (e.g., Nominatim) and emit results over time.
 *
 * @see LocationResult The geocoding result model
 */
interface LocationSearchPort {

    /** Search results as a reactive stream. */
    val searchResults: StateFlow<List<LocationResult>>

    /** Whether a search is currently in progress. */
    val isSearching: StateFlow<Boolean>

    /** Error message from the last failed search, if any. */
    val errorMessage: StateFlow<String?>

    /**
     * Initiates a search for locations matching [query].
     *
     * @param query The search query (e.g., city name, coordinates)
     */
    fun search(query: String)

    /** Clears the current search results and error state. */
    fun clearResults()
}
