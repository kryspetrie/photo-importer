package org.kryspetrie.fileimport.application

import kotlinx.coroutines.CancellationException
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
import org.kryspetrie.fileimport.domain.port.LocationSearchPort

class LocationSearchService(
    private val geocodingPort: GeocodingPort,
    private val dispatcherProvider: DispatcherProvider,
) : LocationSearchPort {

    private val _searchResults = MutableStateFlow<List<LocationResult>>(emptyList())
    override val searchResults: StateFlow<List<LocationResult>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    override val isSearching: StateFlow<Boolean> = _isSearching

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage

    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)
    private var searchJob: Job? = null

    override fun search(query: String) {
        if (query.isBlank() || query.length < 2) {
            clearResults()
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _errorMessage.value = e.message ?: "An unknown error occurred"
                    _isSearching.value = false
                }
            }
    }

    override fun clearResults() {
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
