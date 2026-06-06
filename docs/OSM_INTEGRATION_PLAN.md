# OpenStreetMap Location Search Integration Plan

## Overview

Integrate Nominatim (OpenStreetMap's free geocoding API) to let users search for locations by name, then populate the Location & GPS fields (city, state, country, GPS latitude/longitude) from the selected result. This replaces manual coordinate entry with a search-and-pick workflow.

---

## 1. Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  MetadataScreen  │ ──→ │ LocationSearchService │ ──→ │  Nominatim API   │
│  (UI: search     │     │ (application layer)  │     │  (OSM servers)  │
│   dialog, pick)  │ ←── │ rate-limited, cached │ ←── │  https://nominatim │
└─────────────────┘     └──────────────────┘     └─────────────────┘
```

### Layer responsibilities

| Layer | Component | Role |
|-------|-----------|------|
| **Domain** | `GeocodingPort` | Interface for location search — keeps application layer decoupled from HTTP |
| **Domain** | `LocationResult` | Data class: name, city, state, country, lat, lon, displayName, osmType |
| **Infrastructure** | `NominatimGeocodingAdapter` | HTTP client implementation of `GeocodingPort`, hits `nominatim.openstreetmap.org` |
| **Application** | `LocationSearchService` | Orchestrates search with debounce, caching, and error handling |
| **UI** | `LocationPickerDialog` | Composable search dialog with results list and selection callback |

---

## 2. Domain Layer

### 2.1 `LocationResult` data class

**File**: `src/main/kotlin/org/kryspetrie/fileimport/domain/model/LocationResult.kt`

```kotlin
@Serializable
data class LocationResult(
    val displayName: String,       // "Worcester, Massachusetts, United States"
    val name: String,              // "Worcester"
    val city: String? = null,      // "Worcester"
    val state: String? = null,     // "Massachusetts"  
    val country: String? = null,   // "United States"
    val latitude: Double,          // 42.2626
    val longitude: Double,         // -71.8023
    val osmType: String? = null,   // "relation", "way", "node"
    val osmId: Long? = null,      // OSM element ID for caching
)
```

### 2.2 `GeocodingPort` interface

**File**: `src/main/kotlin/org/kryspetrie/fileimport/domain/port/GeocodingPort.kt`

```kotlin
interface GeocodingPort {
    /** Search for locations matching [query]. Returns up to [limit] results. */
    suspend fun search(query: String, limit: Int = 10): List<LocationResult>
    
    /** Reverse geocode: given coordinates, find the nearest named location. */
    suspend fun reverseGeocode(lat: Double, lon: Double): LocationResult?
}
```

---

## 3. Infrastructure Layer — Nominatim Adapter

### 3.1 `NominatimGeocodingAdapter`

**File**: `src/main/kotlin/org/kryspetrie/fileimport/infrastructure/adapter/NominatimGeocodingAdapter.kt`

Implementation details:

- **HTTP client**: Use `java.net.HttpURLConnection` (no new dependency needed — stays pure JDK)
- **JSON parsing**: Use `kotlinx.serialization.json.Json` (already in the project)
- **Rate limiting**: Nominatim requires max 1 request/second. Use a `Semaphore(1)` + delay mechanism
- **User-Agent**: Required by Nominatim ToU. Set `PetrieImageImporter/1.0 (https://github.com/kryspetrie/petrie-file-importer)`
- **Caching**: In-memory LRU cache of recent searches (max 50 entries, 10-minute TTL)
- **Error handling**: Return empty list on network failure rather than throwing

```
GET https://nominatim.openstreetmap.org/search?q={query}&format=json&addressdetails=1&limit={limit}
GET https://nominatim.openstreetmap.org/reverse?lat={lat}&lon={lon}&format=json&addressdetails=1
```

### 3.2 Nominatim API Response Mapping

```json
{
  "display_name": "Worcester, Worcester County, Massachusetts, United States",
  "lat": "42.262596",
  "lon": "-71.8022945",
  "address": {
    "city": "Worcester",
    "state": "Massachusetts",
    "country": "United States",
    "country_code": "us"
  },
  "osm_type": "relation",
  "osm_id": 60829352
}
```

Map `address.city` → `city`, `address.state` → `state`, `address.country` → `country`. For locations where Nominatim returns `town`/`village`/`suburb` instead of `city`, fall back through `town → village → suburb → city`.

### 3.3 DI Registration

**File**: `src/main/kotlin/org/kryspetrie/fileimport/di/AppModule.kt`

Add:
```kotlin
single<GeocodingPort> { NominatimGeocodingAdapter(dispatcherProvider = get()) }
single { LocationSearchService(geocodingPort = get(), dispatcherProvider = get()) }
```

---

## 4. Application Layer — `LocationSearchService`

**File**: `src/main/kotlin/org/kryspetrie/fileimport/application/LocationSearchService.kt`

```kotlin
@Singleton
class LocationSearchService @Inject constructor(
    private val geocodingPort: GeocodingPort,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val _searchResults = MutableStateFlow<List<LocationResult>>(emptyList())
    val searchResults: StateFlow<List<LocationResult>> = _searchResults
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    
    /** Search with automatic debounce. Returns results via [searchResults] flow. */
    fun search(query: String) { ... }  // debounced, updates flows
    
    /** Clear search state */
    fun clearSearch() { ... }
}
```

Key behaviors:
- Debounce 300ms before calling the API
- Update `isSearching` before/after API call
- On error, set `errorMessage` and keep previous results
- On success, update `searchResults` and clear error

---

## 5. UI Layer — Location Picker Dialog

### 5.1 `LocationPickerDialog` composable

**File**: `src/main/kotlin/org/kryspetrie/fileimport/ui/screens/wizard/metadata/LocationPickerDialog.kt`

A new search dialog composable:

```
┌─────────────────────────────────────────────────────┐
│  📍 Search Location                          [✕]   │
├─────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────┐ [🔍 Search]  │
│  │ Type a place name...             │               │
│  └──────────────────────────────────┘               │
│                                                     │
│  ┌─────────────────────────────────────────────┐    │
│  │ 📍 Worcester, Massachusetts, United States  │    │
│  │    42.2626, -71.8023                        │    │
│  ├─────────────────────────────────────────────┤    │
│  │ 📍 Worcester, Western Cape, South Africa     │    │
│  │    -33.6806, 18.9983                        │    │
│  ├─────────────────────────────────────────────┤    │
│  │ 🏛️ Massachusetts, United States            │    │
│  │    42.0328, -71.7956                        │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│  🔄 Searching...  /  ⚠️ No results found            │
└─────────────────────────────────────────────────────┘
```

Features:
- Text field with search icon and debounced input (300ms)
- Results list showing `displayName` with coordinates underneath
- Click a result → calls `onLocationSelected(LocationResult)` callback
- Search progress indicator (`CircularProgressIndicator` when loading)
- Error state with retry button
- Empty state message

### 5.2 Integration with `LocationSection`

In the existing `LocationSection`, add a **"Search Location" button** next to the GPS coordinates heading:

```
GPS Coordinates                          [📍 Search]
Latitude:  [________]   Longitude:  [________]
```

When the user clicks "Search Location":
1. Opens `LocationPickerDialog`
2. User searches and selects a location
3. The callback populates all Location fields at once:
   - `city` ← `locationResult.city`
   - `state` ← `locationResult.state`
   - `country` ← `locationResult.country`
   - `gpsLatitude` ← `locationResult.latitude.toString()`
   - `gpsLongitude` ← `locationResult.longitude.toString()`
   - Optionally `locationName` ← `locationResult.name` (if empty)

This requires `LocationSection` to accept an `onSearchLocation` callback and for the parent composable to handle the dialog state.

---

## 6. Configuration & Privacy

### 6.1 Offline Grace
- If the network is unavailable, the search button shows "Search (offline)" and is disabled
- Existing manual coordinate entry still works without network

### 6.2 User Preference
- Add `locationSearchEnabled: Boolean = true` to `PhotoScanProfile` for users who want to opt out
- The Search Location button visibility is controlled by this setting

### 6.3 Nominatim Usage Policy
- Must set a valid User-Agent header
- Maximum 1 request per second (rate-limited in adapter)
- No bulk/geocoding-for-ML usage
- Results cached in-memory for 10 minutes to minimize API calls

---

## 7. Implementation Order

| Step | Component | Description |
|------|-----------|-------------|
| **1** | `LocationResult.kt` | Data class for geocoding results |
| **2** | `GeocodingPort.kt` | Domain port interface |
| **3** | `NominatimGeocodingAdapter.kt` | HTTP client + JSON parsing + rate limiting + caching |
| **4** | `LocationSearchService.kt` | Application service with debounce and state flows |
| **5** | `AppModule.kt` | DI registration |
| **6** | `LocationPickerDialog.kt` | Search UI composable |
| **7** | `LocationSection` update | Add "Search Location" button + dialog integration |
| **8** | `MetadataScreen` update | Wire dialog state and location population callbacks |
| **9** | Unit tests | Test adapter, service, and state management |
| **10** | Integration test | Manual test: search "Worcester" → select → verify fields populated |

---

## 8. No New Dependencies Required

The project already has:
- `kotlinx-coroutines-swing` — for `Dispatchers.IO` and async operations
- `kotlinx-serialization-json` — for parsing Nominatim JSON responses
- JDK `java.net.HttpURLConnection` — for HTTP (no need for Ktor/OkHttp for simple GET requests)
- Koin — for DI registration

This means we can implement the entire feature without adding any new Gradle dependencies.