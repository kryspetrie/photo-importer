package org.kryspetrie.fileimport.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ExifValueResolver")
class ExifValueResolverTest {

    private val resolver = ExifValueResolver

    @Nested
    @DisplayName("resolveKeywords")
    inner class ResolveKeywords {

        @Test
        fun `returns keywords when non-blank`() {
            val config = PhotoScanConfiguration(keywords = "vacation, family")
            assertEquals("vacation, family", resolver.resolveKeywords(config))
        }

        @Test
        fun `returns null when keywords are blank`() {
            val config = PhotoScanConfiguration(keywords = "")
            assertNull(resolver.resolveKeywords(config))
        }

        @Test
        fun `returns null when keywords are whitespace only`() {
            val config = PhotoScanConfiguration(keywords = "   ")
            assertNull(resolver.resolveKeywords(config))
        }
    }

    @Nested
    @DisplayName("resolveDateOriginal")
    inner class ResolveDateOriginal {

        @Test
        fun `returns formatted date when originalDate is set`() {
            val config = PhotoScanConfiguration(originalDate = "2024-06-15")
            assertEquals("2024:06:15 00:00:00", resolver.resolveDateOriginal(config))
        }

        @Test
        fun `returns formatted date with time when originalDate includes time`() {
            val config = PhotoScanConfiguration(originalDate = "2024-06-15 14:30:00")
            assertEquals("2024:06:15 14:30:00", resolver.resolveDateOriginal(config))
        }

        @Test
        fun `returns year-only date when only year is set`() {
            val config = PhotoScanConfiguration(year = "2023")
            assertEquals("2023:01:01 00:00:00", resolver.resolveDateOriginal(config))
        }

        @Test
        fun `originalDate takes priority over year`() {
            val config = PhotoScanConfiguration(originalDate = "2024-06-15", year = "2023")
            assertEquals("2024:06:15 00:00:00", resolver.resolveDateOriginal(config))
        }

        @Test
        fun `returns null when no date fields are set`() {
            val config = PhotoScanConfiguration()
            assertNull(resolver.resolveDateOriginal(config))
        }
    }

    @Nested
    @DisplayName("formatDateToExif")
    inner class FormatDateToExif {

        @Test
        fun `converts date-only format`() {
            assertEquals("2024:06:15 00:00:00", resolver.formatDateToExif("2024-06-15"))
        }

        @Test
        fun `converts date-time format`() {
            assertEquals("2024:06:15 14:30:00", resolver.formatDateToExif("2024-06-15 14:30:00"))
        }

        @Test
        fun `handles already-formatted date`() {
            assertEquals("2024:06:15 00:00:00", resolver.formatDateToExif("2024:06:15"))
        }

        @Test
        fun `trims whitespace`() {
            assertEquals("2024:06:15 00:00:00", resolver.formatDateToExif(" 2024-06-15 "))
        }
    }

    @Nested
    @DisplayName("parseFocalLength")
    inner class ParseFocalLength {

        @Test
        fun `parses plain number`() {
            assertEquals(50.0, resolver.parseFocalLength("50")!!, 0.001)
        }

        @Test
        fun `parses number with mm suffix`() {
            assertEquals(50.0, resolver.parseFocalLength("50mm")!!, 0.001)
        }

        @Test
        fun `parses number with MM suffix (uppercase)`() {
            assertEquals(50.0, resolver.parseFocalLength("50MM")!!, 0.001)
        }

        @Test
        fun `parses decimal focal length`() {
            assertEquals(24.5, resolver.parseFocalLength("24.5")!!, 0.001)
        }

        @Test
        fun `parses decimal with mm suffix`() {
            assertEquals(24.5, resolver.parseFocalLength("24.5mm")!!, 0.001)
        }

        @Test
        fun `returns null for invalid input`() {
            assertNull(resolver.parseFocalLength("not-a-number"))
        }

        @Test
        fun `handles whitespace`() {
            assertEquals(50.0, resolver.parseFocalLength(" 50 ")!!, 0.001)
        }
    }

    @Nested
    @DisplayName("parseAperture")
    inner class ParseAperture {

        @Test
        fun `parses f-number with f-slash prefix`() {
            assertEquals(2.8, resolver.parseAperture("f/2.8")!!, 0.001)
        }

        @Test
        fun `parses f-number with F-slash prefix`() {
            assertEquals(2.8, resolver.parseAperture("F/2.8")!!, 0.001)
        }

        @Test
        fun `parses plain number`() {
            assertEquals(2.8, resolver.parseAperture("2.8")!!, 0.001)
        }

        @Test
        fun `parses f-prefix without slash`() {
            assertEquals(2.8, resolver.parseAperture("f2.8")!!, 0.001)
        }

        @Test
        fun `parses F-prefix without slash`() {
            assertEquals(2.8, resolver.parseAperture("F2.8")!!, 0.001)
        }

        @Test
        fun `returns null for invalid input`() {
            assertNull(resolver.parseAperture("not-a-number"))
        }

        @Test
        fun `handles whitespace`() {
            assertEquals(2.8, resolver.parseAperture(" 2.8 ")!!, 0.001)
        }
    }

    @Nested
    @DisplayName("parseShutterSpeed")
    inner class ParseShutterSpeed {

        @Test
        fun `parses fraction format`() {
            val result = resolver.parseShutterSpeed("1/125")
            assertNotNull(result)
            assertEquals(1, result!!.numerator)
            assertEquals(125, result.divisor)
        }

        @Test
        fun `parses decimal format`() {
            val result = resolver.parseShutterSpeed("0.008")
            assertNotNull(result)
            // RationalNumber.valueOf(0.008) approximates to 1/125
            assertTrue(result!!.toDouble() in (0.007..0.009))
        }

        @Test
        fun `parses integer as decimal value`() {
            // "125" is a valid decimal, so it's treated as 125.0 seconds
            // Integer "1/N" format only applies when the string isn't a valid decimal
            val result = resolver.parseShutterSpeed("125")
            assertNotNull(result)
            // RationalNumber.valueOf(125.0) represents 125 seconds
            assertEquals(125.0, result!!.toDouble(), 0.001)
        }

        @Test
        fun `returns null for invalid input`() {
            assertNull(resolver.parseShutterSpeed("not-a-speed"))
        }

        @Test
        fun `returns null for zero denominator`() {
            assertNull(resolver.parseShutterSpeed("1/0"))
        }

        @Test
        fun `handles whitespace`() {
            val result = resolver.parseShutterSpeed(" 1/125 ")
            assertNotNull(result)
            assertEquals(1, result!!.numerator)
            assertEquals(125, result.divisor)
        }
    }

    @Nested
    @DisplayName("decimalToGpsRationals")
    inner class DecimalToGpsRationals {

        @Test
        fun `converts whole degree`() {
            val result = resolver.decimalToGpsRationals(42.0)
            assertEquals(42, result[0].numerator)
            assertEquals(1, result[0].divisor)
            assertEquals(0, result[1].numerator)
            assertEquals(1, result[1].divisor)
        }

        @Test
        fun `converts degree with minutes`() {
            val result = resolver.decimalToGpsRationals(42.5)
            assertEquals(42, result[0].numerator)
            assertEquals(1, result[0].divisor)
            assertEquals(30, result[1].numerator) // 0.5 * 60 = 30
            assertEquals(1, result[1].divisor)
        }

        @Test
        fun `converts degree with minutes and seconds`() {
            val result = resolver.decimalToGpsRationals(42.2626)
            assertEquals(42, result[0].numerator)
            assertEquals(1, result[0].divisor)
            assertEquals(15, result[1].numerator) // 0.2626 * 60 = 15.756 → 15
            assertEquals(1, result[1].divisor)
            // 15.756 - 15 = 0.756, 0.756 * 60 = 45.36, round(45.36 * 10000) = 453600
            assertEquals(453600, result[2].numerator)
            assertEquals(10000, result[2].divisor)
        }

        @Test
        fun `converts zero`() {
            val result = resolver.decimalToGpsRationals(0.0)
            assertEquals(0, result[0].numerator)
            assertEquals(0, result[1].numerator)
            assertEquals(0, result[2].numerator)
        }
    }

    @Nested
    @DisplayName("applyTriStateField")
    inner class ApplyTriStateField {

        @Test
        fun `NULL_OUT calls onNullOut`() {
            var nullOutCalled = false
            resolver.applyTriStateField(
                OverrideState.NULL_OUT,
                "value",
                onNullOut = { nullOutCalled = true },
                onOverride = {},
                legacyPredicate = true,
                legacyAction = {},
            )
            assertTrue(nullOutCalled)
        }

        @Test
        fun `OVERRIDE with non-blank value calls onOverride`() {
            var overrideValue: String? = null
            resolver.applyTriStateField(
                OverrideState.OVERRIDE,
                "hello",
                onNullOut = {},
                onOverride = { overrideValue = it },
                legacyPredicate = true,
                legacyAction = {},
            )
            assertEquals("hello", overrideValue)
        }

        @Test
        fun `OVERRIDE with blank value does not call onOverride`() {
            var overrideCalled = false
            resolver.applyTriStateField(
                OverrideState.OVERRIDE,
                "",
                onNullOut = {},
                onOverride = { overrideCalled = true },
                legacyPredicate = true,
                legacyAction = {},
            )
            assertTrue(!overrideCalled)
        }

        @Test
        fun `KEEP_SOURCE with legacy predicate true calls legacyAction`() {
            var legacyCalled = false
            resolver.applyTriStateField(
                OverrideState.KEEP_SOURCE,
                "value",
                onNullOut = {},
                onOverride = {},
                legacyPredicate = true,
                legacyAction = { legacyCalled = true },
            )
            assertTrue(legacyCalled)
        }

        @Test
        fun `KEEP_SOURCE with legacy predicate false does not call legacyAction`() {
            var legacyCalled = false
            resolver.applyTriStateField(
                OverrideState.KEEP_SOURCE,
                "value",
                onNullOut = {},
                onOverride = {},
                legacyPredicate = false,
                legacyAction = { legacyCalled = true },
            )
            assertTrue(!legacyCalled)
        }

        @Test
        fun `null override state uses legacy behavior`() {
            var legacyCalled = false
            resolver.applyTriStateField(
                null,
                "value",
                onNullOut = {},
                onOverride = {},
                legacyPredicate = true,
                legacyAction = { legacyCalled = true },
            )
            assertTrue(legacyCalled)
        }
    }

    @Nested
    @DisplayName("decimalToGpsRationals - edge cases")
    inner class DecimalToGpsRationalsEdgeCases {

        @Test
        fun `converts negative longitude to absolute values`() {
            // -73.9857° (western hemisphere) should produce 73° 59' 8.52"
            val result = resolver.decimalToGpsRationals(-73.9857)
            assertEquals(73, result[0].numerator)
            assertEquals(1, result[0].divisor)
            // abs = 73.9857, fractional = 0.9857, 0.9857*60 = 59.142
            assertEquals(59, result[1].numerator)
            assertEquals(1, result[1].divisor)
        }

        @Test
        fun `converts negative latitude to absolute values`() {
            // -33.8688° (southern hemisphere)
            val result = resolver.decimalToGpsRationals(-33.8688)
            assertEquals(33, result[0].numerator)
            assertEquals(1, result[0].divisor)
            // abs = 33.8688, fractional = 0.8688, 0.8688*60 = 52.128
            assertEquals(52, result[1].numerator)
        }

        @Test
        fun `seconds never negative`() {
            val result = resolver.decimalToGpsRationals(-0.5)
            // abs = 0.5, degrees=0, minutes=30, seconds=0
            assertEquals(0, result[0].numerator)
            assertEquals(30, result[1].numerator)
            assertTrue(result[2].numerator >= 0)
        }

        @Test
        fun `all rationals are non-negative for negative coordinates`() {
            val testValues = listOf(-122.4194, -73.9857, -33.8688, -0.001, -179.9999)
            for (v in testValues) {
                val result = resolver.decimalToGpsRationals(v)
                assertTrue(result[0].numerator >= 0, "Degrees should be non-negative for $v")
                assertTrue(result[1].numerator >= 0, "Minutes should be non-negative for $v")
                assertTrue(result[2].numerator >= 0, "Seconds should be non-negative for $v")
            }
        }

        @Test
        fun `degrees and minutes never overflow 60`() {
            // Test a value close to 60 minutes: 89.9999
            val result = resolver.decimalToGpsRationals(89.9999)
            assertTrue(result[0].numerator < 180, "Degrees should be < 180")
            assertTrue(result[1].numerator < 60, "Minutes should be < 60")
        }
    }
}
