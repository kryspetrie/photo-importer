# Petrie Plugin System — Architecture Proposal

> **Status**: Draft — For review and discussion
> **Last Updated**: 2026-07-16
> **Target**: Petrie Image Importer v2.0+

---

## 1. Design Goals

| # | Goal | Rationale |
|---|------|-----------|
| G1 | Allow third-party extensions without modifying core code | Extensibility is the entire point. Plugins must be drop-in, not forks. |
| G2 | Support image processing plugins (filters, watermarks, format converters) | Users want custom export pipelines. The current hardcoded rotation/perspective crop is too rigid. |
| G3 | Support metadata plugins (new EXIF handlers, custom tag writers) | EXIF is a moving target. New tag schemas (IPTC Extension, XMP) should be pluggable. |
| G4 | Support import source plugins (new cloud providers, camera protocols) | Adding Dropbox/OneDrive/etc shouldn't require a core PR. |
| G5 | Support export destination plugins (cloud storage, social media) | Same as G4. SmugMug, Google Photos, S3 — these are all niche targets. |
| G6 | Support ML model plugins (face detection alternatives, custom detectors) | Face detection via ONNX is already in the app. Other models (object detection, OCR, scene classification) should be swappable. |
| G7 | Maintain hexagonal architecture — plugins interact through ports only | Plugins are external actors. They must not depend on adapters or infrastructure internals. |
| G8 | Sandboxing — plugins cannot access infrastructure directly | Security and stability. A bad plugin must not crash the host or exfiltrate data. |
| G9 | Type safety — plugin API is Kotlin-first with versioned interfaces | The plugin API module is the only dependency a plugin author needs. No JSON-wrangling at the interface boundary. |

---

## 2. Architecture

### 2.1 Plugin Types

The system defines **6 plugin extension points**, each mapped to a specific part of the import pipeline:

```
┌──────────────────────────────────────────────────────────┐
│                    Petrie Core                           │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │
│  │ Import   │  │ Image    │  │ Metadata  │  │ Export  │ │
│  │ Source   │  │ Process  │  │ Writer   │  │Destin.  │ │
│  │ Plugin   │  │ Plugin   │  │ Plugin   │  │Plugin   │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬────┘ │
│       │              │              │              │      │
│  ┌────┴─────┐  ┌─────┴────┐                              │
│  │Detector  │  │  UI      │                              │
│  │ Plugin   │  │ Plugin   │                              │
│  └──────────┘  └──────────┘                              │
│                                                          │
│  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │
│                 Plugin API (shared)                       │
└──────────────────────────────────────────────────────────┘
```

#### 1. ImageProcessorPlugin

**Purpose**: Transforms images — rotate, filter, watermark, format convert.

```kotlin
interface ImageProcessorPlugin : PetriePlugin {
    fun process(image: ProcessedImage, config: Map<String, String>): ProcessedImage
}
```

| Hook Point | When | Notes |
|------------|------|-------|
| `before-export` | Before writing the final export file | Good for watermarks, format conversion |
| `after-crop` | After perspective crop is applied | Good for filters, corrections |
| `after-perspective-correct` | After perspective transform, before crop | Good for geometry corrections |

**Execution order**: Plugins at the same hook point run in manifest-declared `priority` order (default: alphabetical by ID). The output of one plugin feeds into the next — this is a **pipeline**, not parallel.

#### 2. MetadataWriterPlugin

**Purpose**: Writes or modifies EXIF/IPTC/XMP metadata.

```kotlin
interface MetadataWriterPlugin : PetriePlugin {
    fun enhanceMetadata(metadata: ImageMetadata, image: ProcessedImage): ImageMetadata
}
```

| Hook Point | When | Notes |
|------------|------|-------|
| `before-exif-write` | Before EXIF is written to the output file | Last chance to modify tags |
| `after-exif-read` | After EXIF is read from the source | Good for enrichment (GPS lookup, etc.) |

#### 3. ImportSourcePlugin

**Purpose**: Provides new file sources — cloud APIs, network cameras, directory watchers.

```kotlin
interface ImportSourcePlugin : PetriePlugin {
    fun scanSource(config: SourceConfig): List<ImageFile>
    fun supportsScheme(scheme: String): Boolean
}
```

| Hook Point | When | Notes |
|------------|------|-------|
| `import-source-discovery` | During the "Add Files" step | Plugin can provide a custom file picker UI via UIPlugin |

#### 4. ExportDestinationPlugin

**Purpose**: Sends files to new destinations — S3, Google Photos, SmugMug, etc.

```kotlin
interface ExportDestinationPlugin : PetriePlugin {
    fun export(files: List<ExportFile>, config: DestinationConfig): ExportResult
    fun testConnection(config: DestinationConfig): ConnectionTestResult
}
```

| Hook Point | When | Notes |
|------------|------|-------|
| `post-export` | After all files are written locally | Plugin receives the final file paths |

#### 5. DetectorPlugin

**Purpose**: ML model plugins — face detection, object detection, OCR.

```kotlin
interface DetectorPlugin : PetriePlugin {
    fun detect(image: ProcessedImage, type: DetectionType): List<Detection>
    fun supportedTypes(): Set<DetectionType>
}
```

| Hook Point | When | Notes |
|------------|------|-------|
| `photo-scan-detection` | During PhotoScan, when detection is requested | Replaces or augments built-in ONNX detector |

**DetectionType** enumerations: `FACE`, `OBJECT`, `TEXT`, `BARCODE`, `CUSTOM`. The core dispatches to the first registered `DetectorPlugin` that claims to support the requested type.

#### 6. UIPlugin

**Purpose**: Adds custom screens or panels — map plugins, timeline views, custom editors.

```kotlin
interface UIPlugin : PetriePlugin {
    fun composePanel(context: PluginContext): @Composable () -> Unit
    fun panelSlot(): PanelSlot
}
```

**PanelSlot** is an enum defining where the UI can appear:
- `SETTINGS_PANEL` — a tab in the settings screen
- `EDITOR_PANEL` — a sidebar panel in the photo editor
- `IMPORT_PANEL` — a panel in the import wizard
- `EXPORT_PANEL` — a panel in the export wizard

> ⚠️ UIPlugin is the most architecturally dangerous plugin type. See §3.3 for restrictions.

---

### 2.2 Plugin Lifecycle

```
 ┌─────────────┐     ┌─────────────┐     ┌──────────────┐
 │  Discovery  │────>│   Loading   │────>│  Validation  │
 │             │     │             │     │              │
 │ Scan        │     │ Isolated    │     │ Check API    │
 │ plugins/   │     │ ClassLoader │     │ version,    │
 │ directory   │     │ per plugin  │     │ permissions │
 └─────────────┘     └─────────────┘     └──────┬───────┘
                                                │
                                                ▼
                                         ┌──────────────┐
                                         │  Registration │
                                         │               │
                                         │ Register with │
                                         │ PluginRegistry│
                                         └──────┬───────┘
                                                │
                          ┌─────────────────────┼──────────────────────┐
                          ▼                                            ▼
                   ┌──────────────┐                            ┌──────────────┐
                   │  Activation  │                            │    Error     │
                   │              │                            │              │
                   │ Call         │                            │ Log error,   │
                   │ onActivate() │                            │ mark as ERROR│
                   └──────┬───────┘                            └──────────────┘
                          │
                          ▼
                   ┌──────────────┐     ┌────────────────┐
                   │  Execution   │────>│  Deactivation   │
                   │              │     │                 │
                   │ Called at    │     │ Call            │
                   │ hook points  │     │ onDeactivate()  │
                   └──────────────┘     └─────────────────┘
```

**Detailed state transitions**:

1. **Discovery**: Scan `~/.petrie-importer/plugins/` directory for JAR files. Each subdirectory is treated as a separate plugin. The `petrie-plugin.json` manifest is read from the JAR root.

2. **Loading**: An isolated `URLClassLoader` is created per plugin (see §3.2). Only the `plugin-api` module's classes are shared between the parent and child classloaders.

3. **Validation**: The `apiVersion` in the manifest must match the currently supported major version. Required permissions are checked against a whitelist. The entry point class must implement the declared plugin type interface.

4. **Registration**: If validation passes, the plugin is registered with `PluginRegistry`. It is discoverable via `getPlugins(type)` but not yet active.

5. **Activation**: The user (or auto-start config) triggers activation. `onActivate(pluginContext)` is called, providing the plugin with its configuration and service access. From this point, the plugin will be invoked at its declared hook points.

6. **Execution**: The plugin is called at hook points defined by its type. Each call is wrapped in a try/catch with a configurable timeout.

7. **Deactivation**: `onDeactivate()` is called. The plugin is removed from active hook point dispatch. It may be re-activated later with different config.

---

### 2.3 Plugin Manifest

Each plugin JAR **must** contain a `petrie-plugin.json` file at its root. This is the contract between the plugin and the core system.

```json
{
  "id": "com.example.watermark",
  "name": "Watermark Plugin",
  "version": "1.0.0",
  "apiVersion": "1",
  "description": "Adds custom watermarks to exported photos",
  "author": "Example Corp",
  "type": "imageProcessor",
  "entryPoint": "com.example.watermark.WatermarkPlugin",
  "permissions": ["image.read", "image.write", "metadata.read"],
  "dependencies": [],
  "configuration": [
    {
      "key": "watermarkText",
      "type": "string",
      "default": "© 2024",
      "label": "Watermark Text"
    },
    {
      "key": "position",
      "type": "enum",
      "values": ["bottom-right", "bottom-left", "center", "tiled"],
      "default": "bottom-right",
      "label": "Position"
    },
    {
      "key": "opacity",
      "type": "float",
      "min": 0.0,
      "max": 1.0,
      "default": 0.5,
      "label": "Opacity"
    },
    {
      "key": "enabled",
      "type": "boolean",
      "default": true,
      "label": "Enabled"
    }
  ]
}
```

**Manifest fields**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | string | ✅ | Reverse-DNS identifier. Must be unique across all installed plugins. |
| `name` | string | ✅ | Human-readable name shown in settings UI. |
| `version` | string | ✅ | Semantic version of the plugin. |
| `apiVersion` | string | ✅ | Major version of the Petrie Plugin API this plugin targets. Must match core's supported version. |
| `description` | string | ✅ | One-line description for settings UI. |
| `author` | string | ✅ | Author or organization name. |
| `type` | string | ✅ | One of: `imageProcessor`, `metadataWriter`, `importSource`, `exportDestination`, `detector`, `uiPanel` |
| `entryPoint` | string | ✅ | Fully-qualified class name implementing the plugin type interface. |
| `permissions` | string[] | ✅ | List of permissions this plugin requires. See §3.1. |
| `dependencies` | string[] | ❌ | List of plugin IDs this plugin depends on. Must be activated before this plugin can activate. |
| `configuration` | object[] | ❌ | Declarative config spec. Rendered as settings UI automatically. |

---

### 2.4 Core Interfaces

```kotlin
// ─── domain/model/plugin/ ───

enum class PluginType {
    IMAGE_PROCESSOR,
    METADATA_WRITER,
    IMPORT_SOURCE,
    EXPORT_DESTINATION,
    DETECTOR,
    UI_PANEL
}

enum class PluginState {
    DISCOVERED,   // Found on disk, not yet loaded
    LOADED,       // ClassLoader created, manifest parsed
    VALIDATED,    // API version and permissions check passed
    ACTIVATED,    // onActivate() called, ready for execution
    ERROR,        // Validation or activation failed
    DEACTIVATED   // onDeactivate() called, no longer receiving hooks
}

enum class DetectionType {
    FACE, OBJECT, TEXT, BARCODE, CUSTOM
}

data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int,
    val type: PluginType,
    val description: String,
    val author: String,
    val entryPoint: String,
    val permissions: Set<String>,
    val configuration: List<PluginConfigSpec>
)

data class PluginConfigSpec(
    val key: String,
    val type: ConfigType,
    val default: String?,
    val label: String,
    val values: List<String> = emptyList(),  // For ENUM type
    val min: Float? = null,                    // For FLOAT/INT types
    val max: Float? = null                     // For FLOAT/INT types
)

enum class ConfigType {
    STRING, INT, FLOAT, BOOLEAN, ENUM
}
```

```kotlin
// ─── plugin-api module (published as separate artifact) ───

/**
 * Base interface for all Petrie plugins.
 * Every plugin type interface extends this.
 */
interface PetriePlugin {
    val info: PluginInfo

    /**
     * Called when the plugin is activated. The context provides
     * configuration, logging, and safe service access.
     *
     * Implementations should be lightweight — don't load ML models here.
     * Lazy-load heavy resources on first use instead.
     */
    fun onActivate(context: PluginContext)

    /**
     * Called when the plugin is deactivated or the app is shutting down.
     * Release resources, close connections, save state.
     */
    fun onDeactivate()
}

/**
 * Provided to plugins on activation. This is the only way
 * plugins interact with the host application.
 */
interface PluginContext {
    /** The resolved configuration for this plugin, from manifest defaults + user overrides. */
    val config: Map<String, String>

    /** Get a namespaced logger. Output is tagged with [pluginId:name]. */
    fun getLogger(name: String): PluginLogger

    /**
     * Access a service registered by the core application.
     * Only services explicitly exposed via the plugin API are available.
     * Returns null if the service is not available or the plugin lacks permission.
     */
    fun <T : Any> getService(serviceType: KClass<T>): T?

    /** The plugin's isolated storage directory: ~/.petrie-importer/plugins/{pluginId}/ */
    val dataDir: java.nio.file.Path

    /** Coroutine scope tied to the plugin's lifecycle. Cancelled on deactivation. */
    val scope: kotlinx.coroutines.CoroutineScope
}

interface PluginLogger {
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String, throwable: Throwable? = null)
    fun error(message: String, throwable: Throwable? = null)
}
```

**Plugin type interfaces**:

```kotlin
// ─── plugin-api module ───

interface ImageProcessorPlugin : PetriePlugin {
    fun process(image: ProcessedImage, config: Map<String, String>): ProcessedImage
}

interface MetadataWriterPlugin : PetriePlugin {
    fun enhanceMetadata(metadata: ImageMetadata, image: ProcessedImage): ImageMetadata
}

interface ImportSourcePlugin : PetriePlugin {
    fun scanSource(config: SourceConfig): List<ImageFile>
    fun supportsScheme(scheme: String): Boolean
}

interface ExportDestinationPlugin : PetriePlugin {
    fun export(files: List<ExportFile>, config: DestinationConfig): ExportResult
    fun testConnection(config: DestinationConfig): ConnectionTestResult
}

interface DetectorPlugin : PetriePlugin {
    fun detect(image: ProcessedImage, type: DetectionType): List<Detection>
    fun supportedTypes(): Set<DetectionType>
}

interface UIPlugin : PetriePlugin {
    fun composePanel(context: PluginContext): @Composable () -> Unit
    fun panelSlot(): PanelSlot
}

enum class PanelSlot {
    SETTINGS_PANEL,
    EDITOR_PANEL,
    IMPORT_PANEL,
    EXPORT_PANEL
}
```

---

### 2.5 Plugin Registry

The registry is the central coordination point. It is a **port** (interface) in the domain layer, with a concrete **adapter** implementation in infrastructure.

```kotlin
// ─── domain/port/PluginRegistryPort.kt ───

interface PluginRegistryPort {
    /** Return all activated plugins of the given type, in priority order. */
    fun <T : PetriePlugin> getPlugins(type: PluginType): List<T>

    /** Get a specific plugin by ID, regardless of state. */
    fun getPlugin(id: String): PetriePlugin?

    /** Check the current lifecycle state of a plugin. */
    fun getPluginState(id: String): PluginState

    /** Activate a discovered/validated plugin with optional config overrides. */
    fun activatePlugin(id: String, config: Map<String, String> = emptyMap())

    /** Deactivate an active plugin. Safe to call on already-deactivated plugins. */
    fun deactivatePlugin(id: String)

    /**
     * Scan the plugins directory, load manifests, validate, and register.
     * Called at app startup and when the user triggers "Reload Plugins".
     */
    fun discoverPlugins()

    /** Return metadata about all known plugins (for settings UI). */
    fun listPlugins(): List<PluginInfo>
}
```

The adapter implementation:

```kotlin
// ─── infrastructure/plugin/PluginRegistryAdapter.kt ───

class PluginRegistryAdapter(
    private val loader: ClassLoaderPluginLoader,
    private val manifestParser: PluginManifestParser,
    private val pluginDir: Path = Path.of(System.getProperty("user.home"), ".petrie-importer", "plugins")
) : PluginRegistryPort {

    private val plugins: MutableMap<String, PetriePlugin> = concurrentMapOf()
    private val states: MutableMap<String, PluginState> = concurrentMapOf()

    override fun discoverPlugins() {
        if (!Files.exists(pluginDir)) return

        Files.list(pluginDir).use { dirs ->
            dirs.filter { Files.isDirectory(it) }.forEach { pluginPath ->
                try {
                    val jarPath = findPluginJar(pluginPath)
                    val manifest = manifestParser.parse(pluginPath)
                    // Validation happens here — API version check, etc.
                    states[manifest.id] = PluginState.VALIDATED
                } catch (e: Exception) {
                    // Log and skip invalid plugins
                    states[extractId(pluginPath)] = PluginState.ERROR
                }
            }
        }
    }

    override fun <T : PetriePlugin> getPlugins(type: PluginType): List<T> {
        return plugins.values
            .filter { it.info.type == type && states[it.info.id] == PluginState.ACTIVATED }
            .sortedBy { it.info.id }
            .mapNotNull { it as? T }
    }

    // ... activatePlugin, deactivatePlugin implementations
}
```

---

### 2.6 Hook Point Integration

Hook points are the seam where core code delegates to plugins. **Every hook point call must be wrapped in a try/catch** — a plugin failure must never crash the host application.

#### PhotoScanExportService — Image processing pipeline

```kotlin
// BEFORE (simplified):
fun export(scan: PhotoScan): ExportResult {
    val processed = applyPerspectiveCorrection(scan)
    val metadata = assembleExif(scan)
    writeImage(processed, metadata)
}

// AFTER — with plugin hook points:
fun export(scan: PhotoScan): ExportResult {
    var processed = applyPerspectiveCorrection(scan)

    // Hook: after-perspective-correct
    processed = runPlugins(
        PluginType.IMAGE_PROCESSOR,
        hookPoint = "after-perspective-correct",
        input = processed
    ) { plugin, img -> plugin.process(img, plugin.info.configuration.toConfigMap()) }

    // Hook: before-export
    processed = runPlugins(
        PluginType.IMAGE_PROCESSOR,
        hookPoint = "before-export",
        input = processed
    ) { plugin, img -> plugin.process(img, plugin.info.configuration.toConfigMap()) }

    var metadata = assembleExif(scan)

    // Hook: before-exif-write
    metadata = runMetadataPlugins(
        hookPoint = "before-exif-write",
        metadata = metadata,
        image = processed
    )

    writeImage(processed, metadata)
}
```

```kotlin
// Generic hook point executor with error isolation:
private inline fun <T, R> runPlugins(
    type: PluginType,
    hookPoint: String,
    input: T,
    transform: (PetriePlugin, T) -> R
): T where R : T {
    var current = input
    val plugins = pluginRegistry.getPlugins<PetriePlugin>(type)
    for (plugin in plugins) {
        try {
            current = transform(plugin, current)
            plugin.getLogger("hook").info("Hook $hookPoint executed for ${plugin.info.id}")
        } catch (e: Exception) {
            plugin.getLogger("hook").error("Hook $hookPoint failed for ${plugin.info.id}", e)
            // Skip this plugin's output, continue with previous state
        }
    }
    return current
}
```

#### ImportExecutor — Import source and post-export hooks

```kotlin
// Hook: import-source-discovery
fun discoverSources(): List<ImageFile> {
    val importPlugins = pluginRegistry.getPlugins<ImportSourcePlugin>(PluginType.IMPORT_SOURCE)
    val pluginFiles = importPlugins.flatMap { plugin ->
        try {
            plugin.scanSource(SourceConfig(userConfig))
        } catch (e: Exception) {
            logger.error("Import source plugin ${plugin.info.id} failed", e)
            emptyList()
        }
    }
    return builtInSources + pluginFiles
}

// Hook: post-export
fun postExport(exportedFiles: List<ExportFile>) {
    val exportPlugins = pluginRegistry.getPlugins<ExportDestinationPlugin>(PluginType.EXPORT_DESTINATION)
    for (plugin in exportPlugins) {
        try {
            val result = plugin.export(exportedFiles, DestinationConfig(userConfig))
            if (!result.success) {
                logger.warn("Export destination ${plugin.info.id} reported failure: ${result.message}")
            }
        } catch (e: Exception) {
            logger.error("Export destination plugin ${plugin.info.id} failed", e)
        }
    }
}
```

#### PhotoScanWizardState — Detector hook

```kotlin
// Hook: photo-scan-detection
fun detectFaces(image: ProcessedImage): List<Detection> {
    val detectorPlugins = pluginRegistry.getPlugins<DetectorPlugin>(PluginType.DETECTOR)
        .filter { DetectionType.FACE in it.supportedTypes() }

    if (detectorPlugins.isNotEmpty()) {
        // Use first registered face detection plugin
        return try {
            detectorPlugins.first().detect(image, DetectionType.FACE)
        } catch (e: Exception) {
            logger.error("Detector plugin failed, falling back to built-in", e)
            builtInFaceDetector.detect(image)
        }
    }

    // Fallback to built-in ONNX detection
    return builtInFaceDetector.detect(image)
}
```

---

## 3. Plugin Security Model

### 3.1 Permissions

Each plugin **must declare** the permissions it requires in its manifest. The runtime enforces these before any privileged operation.

| Permission | Description | Checked By |
|------------|-------------|------------|
| `image.read` | Read image pixel data | `ProcessedImage` access guard |
| `image.write` | Write/transform image pixel data | `ProcessedImage` mutation guard |
| `metadata.read` | Read EXIF/IPTC metadata | `ImageMetadata` access guard |
| `metadata.write` | Write/modify EXIF/IPTC metadata | `ImageMetadata` mutation guard |
| `file.read` | Read files from source | `ImportSourcePlugin` framework |
| `file.write` | Write files to destination | `ExportDestinationPlugin` framework |
| `network.access` | Make outbound HTTP requests | `PluginContext.getService()` network service |
| `system.preferences` | Read app settings | `PluginContext.getService()` preferences service |
| `ui.panel` | Add UI panels | UI framework router |

**Enforcement strategy**: Permissions are checked at two levels:
1. **Load time**: The manifest must declare all permissions. Undeclared permissions are denied.
2. **Runtime**: Service access through `PluginContext.getService()` checks that the requesting plugin has the required permission. Direct access to internal classes is prevented by ClassLoader isolation.

If a plugin attempts an action it doesn't have permission for, a `PluginPermissionException` is thrown and caught by the hook point wrapper.

### 3.2 ClassLoader Isolation

Each plugin runs in its own `URLClassLoader` configured as follows:

```
                    ┌─────────────────────────┐
                    │   System ClassLoader     │
                    │   (JDK classes only)     │
                    └───────────┬─────────────┘
                                │
                    ┌───────────┴─────────────┐
                    │   Plugin API ClassLoader  │
                    │   (PetriePlugin, Context, │
                    │    type interfaces)       │
                    └───────────┬─────────────┘
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                 │
    ┌─────────┴─────────┐ ┌───┴───────────┐ ┌───┴─────────────┐
    │  Plugin A CL      │ │ Plugin B CL   │ │ Plugin C CL     │
    │  (watermark.jar)  │ │ (faces.jar)   │ │ (s3-export.jar)  │
    └───────────────────┘ └───────────────┘ └──────────────────┘
```

**Rules**:
- The parent of each plugin's ClassLoader is the **Plugin API ClassLoader** (not the application ClassLoader).
- Only `plugin-api` module classes are shared between plugins and the host.
- Plugins **cannot access**:
  - Infrastructure classes (adapters, services, repository implementations)
  - Other plugins' classes
  - Internal domain implementation details
- Plugins **can access**:
  - Standard library classes (via System ClassLoader parent)
  - Plugin API interfaces and data classes
  - Their own classes and bundled dependencies

**Dependency shadowing**: If a plugin bundles a dependency that conflicts with the host's version (e.g., Kotlinx Coroutines, kotlinx-serialization), the plugin's own ClassLoader takes precedence for that dependency. This allows plugins to use different library versions than the host.

### 3.3 Sandboxing

| Constraint | Mechanism | Rationale |
|------------|-----------|-----------|
| No classpath modification | Each plugin's ClassLoader is immutable after creation | Prevents injection of arbitrary code into the host |
| Network access gated | `network.access` permission + `PluginContext` network service | Plugins cannot open raw sockets. All network goes through an audited HTTP client. |
| File system restricted | Plugin data directory is `~/.petrie-importer/plugins/{pluginId}/` | Plugins cannot read/write arbitrary paths. `PluginContext.dataDir` provides a sandbox. |
| CPU bound by timeout | Each hook point has a configurable timeout (default: 30 seconds) | Prevents infinite loops and runaway computation |
| Cancellation support | `PluginContext.scope` provides a CoroutineScope cancelled on deactivation | Plugins should respect scope cancellation for long-running operations |
| UI constraints | `UIPlugin` can only compose into declared `PanelSlot` positions | Plugins cannot take over the entire window or navigate to arbitrary screens |

**Error isolation** (already shown in §2.6):
- Every hook point call is wrapped in `try/catch`
- Plugin errors are logged but never propagate to crash the host
- After 3 consecutive failures, the plugin is auto-deactivated (circuit breaker)

---

## 4. Implementation Plan

### Phase 1: Core Infrastructure (estimated 2 weeks)

**Goal**: The plugin loading mechanism works end-to-end. A "hello world" plugin can be loaded and activated.

| Task | Description |
|------|-------------|
| Create `plugin-api` module | Extract `PetriePlugin`, `PluginContext`, `PluginInfo`, all 6 type interfaces, `PluginLogger` into a separate Gradle module |
| Create `PluginRegistryPort` | Define the port interface in `domain/port/` |
| Create `ClassLoaderPluginLoader` | Infrastructure adapter that creates isolated ClassLoaders per plugin |
| Create `PluginManifestParser` | Parse `petrie-plugin.json` from JAR files into `PluginInfo` data classes |
| Create `PluginRegistryAdapter` | Wire registry to loader, implement `discoverPlugins()`, `activatePlugin()`, `deactivatePlugin()` |
| Wire into AppModule DI | Register `PluginRegistryPort` → `PluginRegistryAdapter` in the Koin/Dagger module |
| Integration test | Load a test plugin JAR, activate it, call a method, deactivate it |

### Phase 2: Hook Points (estimated 1 week)

**Goal**: Existing core services call plugins at defined hook points.

| Task | Description |
|------|-------------|
| Add hook point calls to `PhotoScanExportService` | Image processing pipeline (after-perspective-correct, before-export), metadata (before-exif-write) |
| Add hook point calls to `ImportExecutor` | Import source discovery, post-export |
| Add hook point calls to `PhotoScanWizardState` | Detector plugin dispatch |
| Create `runPlugins()` helper | Generic hook point executor with error isolation, timeout, and circuit breaker |
| Add plugin dispatch logging | Every hook call is logged with timing |

### Phase 3: Settings UI (estimated 1 week)

**Goal**: Users can manage plugins through the settings screen.

| Task | Description |
|------|-------------|
| Plugin management screen | List discovered plugins with name, type, state, and activate/deactivate toggle |
| Plugin configuration screen | Auto-generated form from manifest `configuration` spec (string fields, enum dropdowns, float sliders, boolean toggles) |
| Plugin status panel | Show plugin state, error messages, last activation time |
| Plugin log viewer | Show recent log entries from each plugin's logger |
| Add "Reload Plugins" button | Re-scan plugin directory and refresh |

### Phase 4: Bundled Reference Plugins (estimated 2 weeks each)

**Goal**: Ship 2-3 reference plugins that demonstrate the API and serve as templates for third-party developers.

| Plugin | Type | Description |
|--------|------|-------------|
| **Watermark Plugin** | `IMAGE_PROCESSOR` | Adds configurable text watermarks to exported images. The simplest possible reference implementation. |
| **EXIF GPS from Photo** | `METADATA_WRITER` | Reads GPS coordinates from the original photo's EXIF and injects them into the scanned image. Demonstrates metadata hook points. |
| **Custom Face Detector** | `DETECTOR` | Wraps an ONNX model file loaded from the plugin's data directory. Demonstrates the detector pipeline and model loading. |

---

## 5. Risks and Mitigations

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| **Plugin crashes host app** | High | Medium | Isolated ClassLoader + try/catch at every hook point + CircuitBreaker pattern (3 failures → auto-deactivate) |
| **Plugin steals user data** | Critical | Low-Medium | Permission model + no direct file access (only through `PluginContext` APIs) + sandboxed data directory |
| **Plugin API version mismatch** | Medium | High | `apiVersion` field in manifest. Reject incompatible plugins at load time. Maintain backward compatibility within a major version. |
| **Performance degradation** | Medium | Medium | Plugin execution times logged per hook. Configurable timeout per hook point (default: 30s). Circuit breaker on slow plugins. |
| **Complex composables from UIPlugin** | Medium | Medium | Restrict UI plugins to panel slots, not full screen navigation. Require `ui.panel` permission. Composable rendering is sandboxed — errors don't crash the host. |
| **ClassLoader leaks** | Low | Medium | Strong reference tracking in `PluginRegistryAdapter`. On deactivation, null out all references to the plugin and its ClassLoader. |
| **Dependency conflicts** | Medium | High | Isolated ClassLoaders allow different dependency versions per plugin. Document the classloading strategy. |
| **Plugin distribution / trust** | High | Low (future) | Phase 1 has no distribution mechanism. Future: signed manifests, community registry, reputation scoring. |

---

## 6. Directory Structure

### Runtime (user machine)

```
~/.petrie-importer/
├── plugins/
│   ├── watermark/
│   │   ├── petrie-plugin.json     # Extracted from JAR at discovery
│   │   └── plugin.jar             # The plugin JAR file
│   └── custom-faces/
│       ├── petrie-plugin.json
│       ├── plugin.jar
│       └── models/                # Plugin's own data directory
│           └── custom_face_model.onnx
├── settings.json                  # User preferences (includes plugin activation state)
└── cache/                         # Application cache
```

### Source code structure

```
petrie-file-importer/
├── src/main/kotlin/.../domain/
│   ├── model/plugin/
│   │   ├── PluginInfo.kt          # Plugin data classes
│   │   ├── PluginType.kt          # Enum: IMAGE_PROCESSOR, METADATA_WRITER, etc.
│   │   ├── PluginState.kt         # Enum: DISCOVERED, LOADED, VALIDATED, etc.
│   │   └── PluginConfigSpec.kt    # Configuration spec data classes
│   └── port/
│       └── PluginRegistryPort.kt  # Domain port interface
│
├── src/main/kotlin/.../infrastructure/plugin/
│   ├── ClassLoaderPluginLoader.kt # Creates isolated ClassLoaders
│   ├── PluginManifestParser.kt   # Parses petrie-plugin.json
│   ├── PluginRegistryAdapter.kt  # Implements PluginRegistryPort
│   └── PluginSecurityGuard.kt   # Permission enforcement
│
├── src/main/kotlin/.../ui/settings/
│   └── PluginSettingsScreen.kt   # Plugin management UI
│
└── plugin-api/                    # Separate Gradle module — published as artifact
    └── src/main/kotlin/.../plugin/
        ├── PetriePlugin.kt        # Base interface
        ├── PluginContext.kt       # Context provided to plugins
        ├── PluginLogger.kt        # Logging interface
        ├── ImageProcessorPlugin.kt
        ├── MetadataWriterPlugin.kt
        ├── ImportSourcePlugin.kt
        ├── ExportDestinationPlugin.kt
        ├── DetectorPlugin.kt
        └── UIPlugin.kt
```

---

## 7. Open Questions

1. **Hot-reload**: Should plugins be reloadable without restarting the app? This adds significant complexity (ClassLoader lifecycle management). **Recommendation**: Defer to Phase 5. Phase 1 requires app restart for plugin changes.

2. **Plugin ordering**: When multiple `ImageProcessorPlugin`s are registered at the same hook point, what determines execution order? **Recommendation**: Alphabetical by plugin ID initially. Add a `priority` field to the manifest in a later version.

3. **Async plugins**: Some plugins (network-based export destinations) will need async operation. Should hook points support `suspend` functions? **Recommendation**: Yes, but only for `ImportSourcePlugin` and `ExportDestinationPlugin` initially. Use `PluginContext.scope` for coroutine dispatch.

4. **Plugin repository**: Should we build a centralized plugin repository (like JetBrains Marketplace)? **Recommendation**: Out of scope for v2.0. Plugins are distributed as JARs and installed manually. A repository can be built later.

5. **Kotlin Multiplatform**: The plugin API should be JVM-only initially. If Petrie goes KMP, the plugin API would need platform-specific extensions. **Recommendation**: Keep annotations and types JVM-compatible. Add `expect/actual` bridges if KMP becomes a goal.

---

*This document is a living artifact. Update it as implementation decisions are made.*