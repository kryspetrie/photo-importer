# Coverage Improvement Plan

## Current Coverage Status (from Kover Report)

### Overall Coverage
- **Class Coverage**: 44.3% (27/61 classes)
- **Method Coverage**: 28.8%
- **Branch Coverage**: 23.1%
- **Line Coverage**: 28.3%
- **Instruction Coverage**: 23.2%

### Critical Gaps - UI Screens (ns-16: org.kryspetrie.fileimport.ui.screens)
- **Overall Screen Coverage**: 16% class, 4.8% method, 1.6% branch, 6.1% line

#### Specific Screen Coverage:
1. **DuplicateScannerScreenKt**: 0% coverage ❌
2. **MediaImportScreenKt**: 0% coverage ❌
3. **ReorganizeScreenKt**: 16.7% coverage ⚠️
4. **PetrieFileImporterApp**: Needs verification
5. **MetadataEditorScreen**: Needs verification
6. **ImportProgressScreen**: Needs verification
7. **PreviewStructureScreen**: Needs verification
8. **DuplicateReviewScreen**: 0% coverage ❌

### Root Causes of Low Coverage

1. **UI Tests Failing**: 280 out of 343 UI tests are failing due to `UninitializedPropertyAccessException`
   - Tests need proper Koin initialization
   - Many tests don't use `TestKoinHelper.setup()`/`tearDown()`

2. **Composable Functions Not Tested**: The actual composable function bodies (the `Kt` files) are not being exercised
   - Tests call the composables but may not trigger all code paths
   - Conditional rendering based on state is not covered

3. **State Transitions Not Tested**: ViewModel state changes that trigger different UI states are not covered

## Improvement Strategy

### Phase 1: Fix Failing UI Tests (Priority: CRITICAL)
- [ ] Add `TestKoinHelper.start()` to all UI test `@BeforeEach` methods
- [ ] Add `TestKoinHelper.stop()` to all UI test `@AfterEach` methods
- [ ] Verify all existing tests pass
- **Expected Impact**: +15-20% coverage

### Phase 2: Add Missing Screen Tests (Priority: HIGH)
- [ ] **DuplicateScannerScreen** - Full interaction tests
  - Detection toggle interactions
  - Scan button enablement
  - Results view rendering
  - Resolve action selection
- [ ] **MediaImportScreen** - Full interaction tests
  - Source/destination field interactions
  - Settings section expansion
  - Import flow buttons
  - Watch folder interactions
- [ ] **ImportProgressScreen** - Progress state tests
- [ ] **PreviewStructureScreen** - Preview rendering tests
- [ ] **DuplicateReviewScreen** - Review and resolution tests
- **Expected Impact**: +10-15% coverage

### Phase 3: Add State Transition Tests (Priority: HIGH)
- [ ] ViewModel state transition tests for all screens
- [ ] Test different UI states (loading, error, success, empty)
- [ ] Test conditional rendering based on state
- **Expected Impact**: +8-12% coverage

### Phase 4: Add Component Tests (Priority: MEDIUM)
- [ ] Shared components (SettingsToggle, ChunkyScrollbar, etc.)
- [ ] Wizard sub-components
- [ ] Dialog components
- **Expected Impact**: +5-8% coverage

### Phase 5: Add Integration Tests (Priority: MEDIUM)
- [ ] Full user flow tests
- [ ] Multi-screen navigation tests
- [ ] State persistence tests
- **Expected Impact**: +3-5% coverage

## Target Coverage Goals

| Metric | Current | Phase 1 | Phase 2 | Phase 3 | Phase 4 | Phase 5 | Final Target |
|--------|---------|---------|---------|---------|---------|---------|--------------|
| Class | 44.3% | 60% | 75% | 82% | 87% | 90% | **90%** |
| Method | 28.8% | 45% | 60% | 70% | 78% | 82% | **80%** |
| Branch | 23.1% | 35% | 50% | 60% | 68% | 72% | **70%** |
| Line | 28.3% | 45% | 60% | 70% | 78% | 82% | **80%** |
| Instruction | 23.2% | 40% | 55% | 65% | 73% | 77% | **75%** |

## Immediate Actions

1. **Fix All Failing Tests**: Update all UI tests to use `TestKoinHelper`
2. **Run Coverage After Each Phase**: Track progress with `./gradlew koverHtmlReport -x uiTest`
3. **Focus on High-Impact Tests**: Prioritize tests that cover multiple code paths
4. **Use Parametrized Tests**: Test multiple states/scenarios efficiently

## Commands

```bash
# Run tests and generate coverage
./gradlew clean test koverHtmlReport -x uiTest

# View coverage report
open build/reports/kover/html/index.html

# Run only UI tests (requires display)
./gradlew uiTest

# Run all tests
./gradlew test uiTest koverHtmlReport
```

## Test Patterns for Maximum Coverage

### 1. State-Based Testing
```kotlin
@Test
fun shouldDisplayErrorCardWhenErrorMessageSet() {
    val viewModel = mock<ReorganizeViewModel> {
        on { errorMessage } doReturn "Test error"
        on { step } doReturn ReorganizeViewModel.ReorgStep.SETUP
    }
    
    composeTestRule.setContent {
        TestStringsProvider {
            ImporterTheme {
                ReorganizeScreen(viewModel = viewModel)
            }
        }
    }
    
    composeTestRule.onNodeWithText("Test error").assertIsDisplayed()
}
```

### 2. Interaction Testing
```kotlin
@Test
fun shouldCallOnImportWhenButtonClicked() {
    var importCalled = false
    
    composeTestRule.setContent {
        TestStringsProvider {
            ImporterTheme {
                MediaImportScreen(
                    onImportClick = { importCalled = true }
                )
            }
        }
    }
    
    composeTestRule.onNodeWithText("Start Import").performClick()
    assertThat(importCalled).isTrue()
}
```

### 3. Conditional Rendering
```kotlin
@Test
fun shouldShowPreviewSectionWhenScanComplete() {
    val viewModel = mock<ReorganizeViewModel> {
        on { step } doReturn ReorganizeViewModel.ReorgStep.PREVIEW
        on { preview } doReturn ReorganizePreview(...)
    }
    
    // Test preview section renders
}
```

## Monitoring Progress

After each phase, run:
```bash
./gradlew koverHtmlReport -x uiTest
cat build/reports/kover/html/index.html | grep -E "percent|absValue" | head -20
```

Track coverage trends in a spreadsheet or document.
