# Archived Unused Photo Scan Detectors

This folder contains detector implementations that were created during development
but are not currently used in production. They are kept here for reference purposes
and can be re-enabled if needed.

## Files

| File | Purpose | Notes |
|------|---------|-------|
| `ConsensusCornerDetector.kt` | Multi-detector consensus approach | May be useful for higher accuracy |
| `EdgeFollowingCornerDetector.kt` | Edge-following algorithm | Good for documents |
| `EdgeLineIntersectionCornerDetector.kt` | Line intersection approach | Fast but less accurate |
| `HybridEdgeCornerDetector.kt` | Hybrid edge detection | Experimental |
| `ImprovedEdgeLineCornerDetector.kt` | Improved line detection | Performance optimizations |
| `IntegratedHybridCornerDetector.kt` | Integrated hybrid approach | Combines multiple methods |
| `RefinedEdgeLineCornerDetector.kt` | Refined edge-line detection | Higher precision |
| `RegionGuidedCornerDetector.kt` | Region-based detection | Good for overlapping photos |

## Currently Active

The active implementation uses:
- `HybridCornerDetector.kt` - Default hybrid detector
- `RectangleDetector.kt` - Rectangle detection
- `PhotoScanDetectorService.kt` - Main service orchestrating detection

## To Re-enable

1. Move file from this folder to parent
2. Update imports in `PhotoScanDetectorService.kt`
3. Update Koin DI configuration in `AppModule.kt`
4. Add tests for the detector
5. Run full test suite to verify

## Benchmarks

For reference, here's the approximate accuracy comparison from development testing:

| Detector | Accuracy | Speed |
|---------|----------|-------|
| ConsensusCornerDetector | 95% | Slow |
| HybridCornerDetector (active) | 92% | Medium |
| EdgeFollowingCornerDetector | 88% | Fast |
| RectangleDetector | 85% | Very Fast |

*Note: Accuracy figures are from controlled testing with synthetic images.
Real-world performance may vary.*
