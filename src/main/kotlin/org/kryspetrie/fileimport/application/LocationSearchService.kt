package org.kryspetrie.fileimport.application

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.kryspetrie.fileimport.domain.model.LocationResult
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.GeocodingPort

@Singleton
class LocationSearchService
@Inject
constructor(
    private val geocodingPort: GeocodingPort,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val _searchResults = MutableStateFlow<List<LocationResult>>(emptyList())
    val searchResults: StateFlow<List<LocationResult>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)
    private var searchJob: Job? = null

    fun search(query: String) {
        if (query.isBlank() || query.length < 2) {
            clearSearch()
            return
        }

        _isSearching.value = true
        _errorMessage.value = null

        searchJob?.cancel()
        searchJob =
            scope.launch {
                delay(DEBOUNCE_DELAY_MS)

                try {
                    val results = geocodingPort.search(query)
                    _searchResults.value = results
                    _isSearching.value = false
                } catch (e: Exception) {
                    _errorMessage.value = e.message ?: "An unknown error occurred"
                    _isSearching.value = false
                }
            }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        _searchResults.value = emptyList()
        _isSearching.value = false
        _errorMessage.value = null
    }

    fun onCleared() {
        scope.cancel()
    }

    companion object {
        private const val DEBOUNCE_DELAY_MS = 300L
    }
}
