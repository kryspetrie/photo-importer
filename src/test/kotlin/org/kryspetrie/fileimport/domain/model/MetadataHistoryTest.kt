package org.kryspetrie.fileimport.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetadataHistoryTest {

    // ── addValue tests ──

    @Test
    fun `addValue adds to front of list`() {
        val history = MetadataHistory()
            .addValue("description", "Beach sunset")
            .addValue("description", "Mountain view")

        assertEquals(listOf("Mountain view", "Beach sunset"), history.description)
    }

    @Test
    fun `addValue deduplicates existing value`() {
        val history = MetadataHistory()
            .addValue("description", "Beach sunset")
            .addValue("description", "Mountain view")
            .addValue("description", "Beach sunset")

        assertEquals(listOf("Beach sunset", "Mountain view"), history.description)
    }

    @Test
    fun `addValue does not add blank values`() {
        val history = MetadataHistory()
            .addValue("description", "Beach sunset")
            .addValue("description", "")

        assertEquals(listOf("Beach sunset"), history.description)
    }

    @Test
    fun `addValue caps at MAX_ENTRIES`() {
        val history = (1..15).fold(MetadataHistory()) { h, i ->
            h.addValue("description", "Item $i")
        }

        assertEquals(MetadataHistory.MAX_ENTRIES, history.description.size)
        // Most recent should be first
        assertEquals("Item 15", history.description.first())
        assertEquals("Item 6", history.description.last())
    }

    @Test
    fun `addValue works for all field keys`() {
        val allKeys = MetadataHistory.FIELD_KEYS
        val history = allKeys.fold(MetadataHistory()) { h, key ->
            h.addValue(key, "test-value")
        }

        allKeys.forEach { key ->
            assertEquals(listOf("test-value"), history.getSuggestions(key), "Field $key should have value")
        }
    }

    @Test
    fun `addValue with unknown key returns unchanged`() {
        val history = MetadataHistory().addValue("unknown_field", "value")
        assertEquals(MetadataHistory(), history)
    }

    @Test
    fun `addValue works for gpsLatitude`() {
        val history = MetadataHistory()
            .addValue("gpsLatitude", "42.2626")
            .addValue("gpsLatitude", "51.5074")

        assertEquals(listOf("51.5074", "42.2626"), history.gpsLatitude)
    }

    @Test
    fun `addValue works for gpsLongitude`() {
        val history = MetadataHistory()
            .addValue("gpsLongitude", "-71.8023")
            .addValue("gpsLongitude", "-0.1278")

        assertEquals(listOf("-0.1278", "-71.8023"), history.gpsLongitude)
    }

    // ── getSuggestions tests ──

    @Test
    fun `getSuggestions returns empty for unknown key`() {
        val history = MetadataHistory().addValue("description", "test")
        assertEquals(emptyList<String>(), history.getSuggestions("unknown"))
    }

    @Test
    fun `getSuggestions returns values in MRU order`() {
        val history = MetadataHistory()
            .addValue("city", "Worcester")
            .addValue("city", "Paris")
            .addValue("city", "London")

        assertEquals(listOf("London", "Paris", "Worcester"), history.getSuggestions("city"))
    }

    // ── removeValue tests ──

    @Test
    fun `removeValue removes specific value`() {
        val history = MetadataHistory()
            .addValue("description", "A")
            .addValue("description", "B")
            .addValue("description", "C")
            .removeValue("description", "B")

        assertEquals(listOf("C", "A"), history.description)
    }

    @Test
    fun `removeValue with blank value returns unchanged`() {
        val history = MetadataHistory().addValue("description", "test")
        val result = history.removeValue("description", "")
        assertEquals(history, result)
    }

    @Test
    fun `removeValue with unknown key returns unchanged`() {
        val history = MetadataHistory().addValue("description", "test")
        val result = history.removeValue("unknown", "test")
        assertEquals(history, result)
    }

    // ── addSet tests ──

    @Test
    fun `addSet adds a set to recentSets`() {
        val set = RecentMetadataSet(
            locationName = "Grandma's house",
            city = "Worcester",
            state = "MA",
            country = "United States",
            gpsLatitude = "42.2626",
            gpsLongitude = "-71.8023",
        )

        val history = MetadataHistory().addSet(set)

        assertEquals(1, history.recentSets.size)
        assertEquals("Grandma's house", history.recentSets[0].locationName)
        assertEquals("Worcester", history.recentSets[0].city)
    }

    @Test
    fun `addSet puts new set at front (MRU order)`() {
        val first = RecentMetadataSet(city = "Boston", timestamp = 1000)
        val second = RecentMetadataSet(city = "Worcester", timestamp = 2000)

        val history = MetadataHistory()
            .addSet(first)
            .addSet(second)

        assertEquals(2, history.recentSets.size)
        assertEquals("Worcester", history.recentSets[0].city)
        assertEquals("Boston", history.recentSets[1].city)
    }

    @Test
    fun `addSet deduplicates matching sets`() {
        val set1 = RecentMetadataSet(city = "Worcester", state = "MA", timestamp = 1000)
        val set2 = RecentMetadataSet(city = "Worcester", state = "MA", timestamp = 2000)

        val history = MetadataHistory()
            .addSet(set1)
            .addSet(set2)

        // Should deduplicate — only one set with same non-blank fields
        assertEquals(1, history.recentSets.size)
        assertEquals(2000, history.recentSets[0].timestamp)
    }

    @Test
    fun `addSet does not deduplicate if fields differ`() {
        val set1 = RecentMetadataSet(city = "Worcester", state = "MA", timestamp = 1000)
        val set2 = RecentMetadataSet(city = "Boston", state = "MA", timestamp = 2000)

        val history = MetadataHistory()
            .addSet(set1)
            .addSet(set2)

        assertEquals(2, history.recentSets.size)
        assertEquals("Boston", history.recentSets[0].city)
        assertEquals("Worcester", history.recentSets[1].city)
    }

    @Test
    fun `addSet ignores empty sets`() {
        val emptySet = RecentMetadataSet(timestamp = 1000) // all fields blank
        val history = MetadataHistory().addSet(emptySet)

        assertEquals(0, history.recentSets.size)
    }

    @Test
    fun `addSet caps at MAX_SETS`() {
        var history = MetadataHistory()
        for (i in 1..(MetadataHistory.MAX_SETS + 5)) {
            history = history.addSet(RecentMetadataSet(city = "City $i", timestamp = i.toLong()))
        }

        assertEquals(MetadataHistory.MAX_SETS, history.recentSets.size)
        // Most recent should be first
        assertEquals("City ${MetadataHistory.MAX_SETS + 5}", history.recentSets[0].city)
    }

    // ── removeSet tests ──

    @Test
    fun `removeSet removes by timestamp`() {
        val set1 = RecentMetadataSet(city = "Boston", timestamp = 1000)
        val set2 = RecentMetadataSet(city = "Worcester", timestamp = 2000)

        val history = MetadataHistory()
            .addSet(set1)
            .addSet(set2)
            .removeSet(1000)

        assertEquals(1, history.recentSets.size)
        assertEquals("Worcester", history.recentSets[0].city)
    }

    @Test
    fun `removeSet with non-existent timestamp returns unchanged`() {
        val set = RecentMetadataSet(city = "Boston", timestamp = 1000)
        val history = MetadataHistory().addSet(set)
        val result = history.removeSet(9999)

        assertEquals(history, result)
    }

    // ── getLocationSets tests ──

    @Test
    fun `getLocationSets returns only sets with location data`() {
        val locationSet = RecentMetadataSet(city = "Worcester", state = "MA", timestamp = 1000)
        val nonLocationSet = RecentMetadataSet(description = "Beach photo", timestamp = 2000)

        val history = MetadataHistory()
            .addSet(locationSet)
            .addSet(nonLocationSet)

        val locationSets = history.getLocationSets()
        assertEquals(1, locationSets.size)
        assertEquals("Worcester", locationSets[0].city)
    }

    @Test
    fun `getLocationSets matches on any location field`() {
        val gpsOnly = RecentMetadataSet(gpsLatitude = "42.2626", gpsLongitude = "-71.8023", timestamp = 1000)
        val cityName = RecentMetadataSet(city = "Paris", timestamp = 2000)
        val stateName = RecentMetadataSet(state = "CA", timestamp = 3000)
        val countryName = RecentMetadataSet(country = "France", timestamp = 4000)
        val locName = RecentMetadataSet(locationName = "Home", timestamp = 5000)

        val history = MetadataHistory()
            .addSet(gpsOnly)
            .addSet(cityName)
            .addSet(stateName)
            .addSet(countryName)
            .addSet(locName)

        assertEquals(5, history.getLocationSets().size)
    }

    @Test
    fun `getLocationSets returns empty for no location data`() {
        val nonLocationSet = RecentMetadataSet(description = "Beach", timestamp = 1000)
        val history = MetadataHistory().addSet(nonLocationSet)

        assertEquals(0, history.getLocationSets().size)
    }

    // ── Deduplication with blank fields ──

    @Test
    fun `addSet dedup considers blank fields as matching`() {
        // Two sets where only city differs — they should NOT be deduped
        val set1 = RecentMetadataSet(city = "Worcester", description = "", timestamp = 1000)
        val set2 = RecentMetadataSet(city = "Boston", description = "", timestamp = 2000)

        val history = MetadataHistory().addSet(set1).addSet(set2)
        assertEquals(2, history.recentSets.size)
    }

    @Test
    fun `addSet dedup where one has extra blank fields still matches`() {
        // If set1 has city=Worcester and set2 also has city=Worcester, they are the same
        // regardless of other blank fields
        val set1 = RecentMetadataSet(city = "Worcester", timestamp = 1000)
        val set2 = RecentMetadataSet(city = "Worcester", description = "", timestamp = 2000)

        val history = MetadataHistory().addSet(set1).addSet(set2)
        assertEquals(1, history.recentSets.size)
    }

    @Test
    fun `addSet preserves original individual field histories`() {
        val history = MetadataHistory()
            .addValue("city", "Worcester")
            .addSet(RecentMetadataSet(city = "Boston", timestamp = 1000))

        // Individual field history should still work
        assertEquals(listOf("Worcester"), history.city) // Individual history unchanged
        assertEquals(listOf("Boston"), listOf(history.recentSets[0].city))
    }
}
