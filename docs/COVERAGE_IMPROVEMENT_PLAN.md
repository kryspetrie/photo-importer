# Test Coverage Improvement Plan

## Executive Summary

Goal: Achieve **80%+ test coverage** for testable components (domain, application, infrastructure).

## Current State

| Component | Coverage |
|-----------|----------|
| Domain Model | 86% |
| Infrastructure Logging | 90% |
| Infrastructure PhotoScan | 78% |
| Infrastructure Wizard | 66% |
| Application Services | 56% |
| Domain Ports | 47% |
| **Testable Code Total** | **73%** |

## Problems Identified

1. **Kover 0.9.2** - doesn't support easy exclusion configuration via DSL
2. **UI Code Included** - ~9,400 lines of UI/Compose code pulls overall coverage to 30%
3. **Missing Tests** - Application services and domain ports need more coverage

## Solution Plan

### Phase 1: Kover Upgrade

**Action**: Upgrade from Kover 0.9.2 to 0.9.8

**Benefits**:
- Better DSL support for exclusions
- Improved reporting
- More stable configuration

**Implementation**:
```kotlin
id("org.jetbrains.kotlinx.kover") version "0.9.8"
```

### Phase 2: Configure Kover Exclusions

**Goal**: Exclude untestable code from coverage calculations

**Excluded Packages**:
- `org.kryspetrie.fileimport.ui.**` - Compose UI (untestable in unit tests)
- `org.kryspetrie.fileimport.presentation.**` - Presentation layer
- `org.kryspetrie.fileimport.generated.**` - Generated code
- `org.kryspetrie.fileimport.di.**` - Dependency injection
- `org.kryspetrie.fileimport.platform.**` - Platform-specific
- `org.kryspetrie.fileimport.cli.**` - Command-line interface

**Kover Configuration**:
```kotlin
kover {
    filters {
        classes {
            exclude("*ui*")
            exclude("*Ui*")
            exclude("*Screen*")
            exclude("*Theme*")
            exclude("*Component*")
        }
        packages {
            exclude("org.kryspetrie.fileimport.ui")
            exclude("org.kryspetrie.fileimport.presentation")
            exclude("org.kryspetrie.fileimport.generated")
            exclude("org.kryspetrie.fileimport.di")
            exclude("org.kryspetrie.fileimport.platform")
            exclude("org.kryspetrie.fileimport.cli")
        }
    }
}
```

### Phase 3: Fill Coverage Gaps

#### 3.1 Application Services (Target: 70%+)

| Service | Current | Target | Tests Needed |
|---------|---------|--------|--------------|
| ImportService | ~50% | 70% | More path/validation tests |
| DuplicateScannerService | ~60% | 80% | Edge case coverage |
| WatchFolderService | ~70% | 80% | Event handling tests |
| ReorganizeService | ~60% | 75% | Operation tests |

#### 3.2 Domain Ports (Target: 70%+)

| Port | Current | Target | Tests Needed |
|------|---------|--------|--------------|
| All ports | 47% | 70% | Mock implementations |

#### 3.3 Infrastructure Wizard (Target: 75%+)

| Component | Current | Target | Tests Needed |
|-----------|---------|--------|--------------|
| BoundingBox | ~70% | 85% | More transform tests |
| FourPointState | ~65% | 80% | State transition tests |
| PerspectiveTransformer | ~60% | 75% | Math verification |

### Phase 4: Integration Tests

Create integration tests for key workflows:

1. **ImportWorkflowIntegrationTest**
   - Full import flow with mocked dependencies
   - Error handling scenarios
   - Concurrent operation handling

2. **PhotoScanWorkflowIntegrationTest**
   - Box creation → refinement → export flow
   - Profile management integration
   - Settings persistence

3. **SettingsPersistenceIntegrationTest**
   - Save/load roundtrip
   - Migration scenarios
   - Corruption handling

### Phase 5: Coverage Verification

**Target Metrics**:
- Overall testable code: **80%+**
- Domain model: **90%+**
- Application services: **70%+**
- Infrastructure: **75%+**

**Verification Commands**:
```bash
./gradlew test koverHtmlReport
./gradlew koverVerify
```

## Implementation Checklist

### Kover Upgrade
- [ ] Update build.gradle.kts with Kover 0.9.8
- [ ] Add exclusion filters for UI code
- [ ] Verify exclusion configuration works
- [ ] Generate new baseline report

### Test Coverage Improvements
- [ ] Add ImportService edge case tests
- [ ] Add DuplicateScannerService tests
- [ ] Add WatchFolderService event tests
- [ ] Add BoundingBox transform tests
- [ ] Add FourPointState transition tests
- [ ] Add port mock tests

### Integration Tests
- [ ] Create ImportWorkflowIntegrationTest
- [ ] Create PhotoScanWorkflowIntegrationTest
- [ ] Create SettingsPersistenceIntegrationTest

### Final Verification
- [ ] Run full test suite (expect 548+ tests)
- [ ] Verify 80%+ coverage on testable code
- [ ] Update README with coverage badge
- [ ] Document any remaining gaps

## Timeline

1. **Kover Upgrade**: 5 minutes
2. **Exclusion Configuration**: 10 minutes
3. **Test Coverage Improvements**: 30-45 minutes
4. **Integration Tests**: 30 minutes
5. **Verification**: 10 minutes

**Total Estimated Time**: ~2 hours

## Success Criteria

- ✅ Kover 0.9.8 configured and working
- ✅ UI code excluded from coverage
- ✅ Testable code coverage ≥ 80%
- ✅ All 548+ tests still passing
- ✅ No regression in coverage reporting
