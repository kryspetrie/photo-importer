# UI Test Infrastructure Fix - 2026-08-03

## Problem
Compose Desktop UI tests were failing with `UninitializedPropertyAccessException: lateinit property scene has not been initialized` because the `@Rule` annotation from JUnit 4 wasn't being properly recognized when running tests on JUnit 5 Platform.

## Root Cause
- Compose Desktop's `createComposeRule()` returns a `DesktopComposeTestRule` that requires JUnit 4's `@Rule` annotation for proper initialization
- The `uiTest` task was configured to use JUnit Platform (JUnit 5) exclusively
- JUnit 5 doesn't natively support JUnit 4's `@Rule` annotation
- The junit-vintage-engine was present but tests were using JUnit 5 lifecycle annotations (`@BeforeEach`, `@AfterEach`) instead of JUnit 4's (`@Before`, `@After`)

## Solution
1. **Added JUnit 4 dependency** to `build.gradle.kts`:
   ```kotlin
   testImplementation("junit:junit:4.13.2")
   ```

2. **Updated uiTest task configuration** to properly support both JUnit 4 and JUnit 5:
   ```kotlin
   tasks.register<Test>("uiTest") {
       useJUnitPlatform()  // Enables vintage engine for JUnit 4 tests
       systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
       // Include patterns for UI test classes
       include("**/*UiComponentTest*.class")
       include("**/*ComprehensiveTest*.class")
       include("**/*StateTest*.class")
       include("**/*InteractionTest*.class")
       include("**/*ComponentTest*.class")
   }
   ```

3. **Converted all Compose UI test files** to use JUnit 4 annotations consistently:
   - Changed `@BeforeEach` → `@Before`
   - Changed `@AfterEach` → `@After`
   - Changed `@Test` (JUnit 5) → `@Test` (JUnit 4)
   - Added `@get:Rule` annotation to `composeTestRule` fields
   - Updated imports to use `org.junit.*` instead of `org.junit.jupiter.api.*`

## Files Modified
- `build.gradle.kts` - Added JUnit 4 dependency and updated uiTest task
- `src/test/kotlin/org/kryspetrie/fileimport/ui/screens/*ComprehensiveTest.kt` (3 files)
- `src/test/kotlin/org/kryspetrie/fileimport/ui/screens/*StateTest.kt` (2 files)
- `src/test/kotlin/org/kryspetrie/fileimport/ui/screens/*InteractionTest.kt` (3 files)

## Results
### Before Fix
- **Compilation**: ✅ Successful
- **Tests Run**: 361
- **Failures**: 193 (all with `UninitializedPropertyAccessException`)
- **Root Issue**: Test infrastructure not initializing Compose UI test environment

### After Fix
- **Compilation**: ✅ Successful
- **Tests Run**: 361
- **Failures**: 161 (assertion failures, not infrastructure failures)
- **Improvement**: 32 tests now passing
- **Status**: Test infrastructure working correctly; remaining failures are due to test assertions not matching actual UI text

## Remaining Issues
The 161 remaining failures are **not** infrastructure issues. They are assertion failures where tests expect specific text that doesn't match the actual UI strings. Examples:
- Test expects: "Reorganize"
- Actual UI shows: "Reorganize Library"
- Test expects: "Move, copy, or rename photos based on metadata"
- Actual UI shows: "Apply folder and filename patterns to an existing media library."

These tests need to be updated to match the current UI strings, or the UI strings need to be updated to match the tests.

## Next Steps
1. Update failing test assertions to match current UI strings
2. Verify all comprehensive tests pass
3. Add new state tests for MediaImportScreen and DuplicateScannerScreen
4. Run coverage report to measure improvement
5. Continue with Phase 1 completion (fix all failing UI tests)

## Commands
```bash
# Run UI tests
./gradlew uiTest

# Generate coverage report
./gradlew koverHtmlReport -x uiTest

# View coverage
open build/reports/kover/html/index.html
```

## Key Learnings
1. **Compose Desktop UI tests require JUnit 4's @Rule**: Even when using JUnit 5 Platform, the `createComposeRule()` function needs JUnit 4's `@Rule` annotation to properly initialize the test environment.

2. **Mixed annotations don't work**: You cannot mix JUnit 4's `@Rule` with JUnit 5's `@BeforeEach`/`@AfterEach`. Must use consistent JUnit 4 annotations throughout the test class.

3. **Vintage engine requires explicit JUnit 4 dependency**: The `junit-vintage-engine` alone isn't enough; you also need `junit:junit:4.x` in your dependencies.

4. **Test infrastructure vs test content**: Infrastructure failures (like `UninitializedPropertyAccessException`) are different from assertion failures. Fix infrastructure first, then address test content.
