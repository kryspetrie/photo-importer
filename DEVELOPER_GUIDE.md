# Compose Multiplatform Desktop Development Guide

## Introduction for Backend Developers

Welcome to Compose Multiplatform! As a Spring developer, you're already familiar with concepts like dependency injection, layered architecture, and testing. Compose Multiplatform brings similar principles to desktop UI development.

**Key Mindset Shift**: Instead of thinking in terms of HTML/CSS/JavaScript or Swing/AWT components, think in terms of **declarative UI**. You describe what the UI should look like for a given state, and Compose automatically updates the UI when the state changes.

## What is Compose Multiplatform?

**JetBrains Compose Multiplatform** is a modern declarative UI framework that allows you to build native desktop applications (Windows, macOS, Linux) using Kotlin. It's based on the same technology as Jetpack Compose for Android, but targets desktop platforms.

### Key Concepts

1. **Composables**: Functions annotated with `@Composable` that define UI elements
2. **State**: Data that drives the UI - when state changes, the UI automatically recomposes
3. **Recomposition**: The process of re-executing composables when state changes
4. **Material Design**: Built-in theming and components following Material Design 3

## Do You Need Android Studio?

**No!** For desktop-only development, **IntelliJ IDEA** is sufficient and recommended:

- **IntelliJ IDEA Community Edition** (free) - Fully supports Compose Multiplatform
- **IntelliJ IDEA Ultimate** - Additional features but not necessary
- **Android Studio** - Only needed if you're targeting Android/mobile platforms

### Recommended Setup

1. **Install IntelliJ IDEA** (Community or Ultimate)
2. **Install JDK 21** (required by this project)
3. **Install the Compose Multiplatform plugin** (usually bundled with recent IDEA versions)
4. **Enable Kotlin plugin** (bundled with IDEA)

## Development Workflow

### 1. Running the Application

```bash
# Run the desktop application with hot reload
./gradlew run

# Or use the convenience script
./photo-import.sh
```

**Hot Reload**: Compose Desktop supports live reload. When you change UI code, the application automatically refreshes without restarting. This is similar to Spring DevTools.

### 2. Building Native Installers

```bash
# Build for your current OS
./gradlew packageDmg      # macOS
./gradlew packageMsi      # Windows  
./gradlew packageDeb      # Linux

# Build uber JAR (requires Java on target machine)
./gradlew packageUberJarForCurrentOS
```

### 3. Testing UI Components

#### Unit Testing (Like Spring Tests)

Test pure functions and logic without UI:

```kotlin
// Example: Testing a config summary function
@Test
fun `should show folder pattern and original names for defaults`() {
    val config = ImportConfiguration()
    val summary = configSummary(config)
    
    assertThat(summary).contains("{yyyy-MM-dd}")
    assertThat(summary).contains("original names")
}
```

Run tests:
```bash
./gradlew test
```

#### UI Component Testing with Compose UI Test

Compose provides a testing framework similar to Spring's MockMvc but for UI:

```kotlin
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MyScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun testButtonClick() {
        // Set up the composable
        composeTestRule.setContent {
            MyButtonScreen(onClick = { /* verify */ })
        }
        
        // Find and interact with UI elements
        composeTestRule
            .onNodeWithText("Click Me")
            .performClick()
            
        // Verify results
        composeTestRule
            .onNodeWithText("Clicked!")
            .assertExists()
    }
}
```

**Note**: The current project uses JUnit 5 for unit tests. UI component tests require additional setup with `compose.ui.test.junit4`.

### 4. Previewing Components (Like WidgetBook)

**Compose Preview** is the standard way to preview components during development - similar to WidgetBook or Storybook:

```kotlin
@Composable
fun MyButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(text)
    }
}

// Preview for IntelliJ
@Preview(showBackground = true)
@Composable
fun MyButtonPreview() {
    PetrieTheme {
        MyButton("Click Me", onClick = {})
    }
}

// Preview with dark theme
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MyButtonDarkPreview() {
    PetrieTheme {
        MyButton("Click Me", onClick = {})
    }
}
```

**To view previews in IntelliJ**:
1. Look for the "Preview" button in the gutter next to `@Preview` functions
2. Click it to see a live preview of your component
3. Multiple previews show as tabs

### 5. Debugging and Inspection

#### Layout Inspector

Compose Desktop doesn't have a built-in layout inspector like Android Studio, but you can:

1. **Use println debugging** for state values
2. **Enable composition logging**:
   ```kotlin
   System.setProperty("compose.compiler.metrics", "true")
   ```

3. **Use IntelliJ Debugger** - Set breakpoints in composable functions

#### State Debugging

```kotlin
@Composable
fun MyScreen() {
    var count by remember { mutableStateOf(0) }
    
    // Log state changes
    LaunchedEffect(count) {
        println("Count changed to: $count")
    }
    
    Text("Count: $count")
}
```

## Architecture Overview

This project follows **Hexagonal Architecture** (Ports and Adapters), which you'll find familiar as a Spring developer:

```
┌─────────────────────────────────────────────────────────────┐
│                    UI Layer (Compose Desktop)                │
│  - @Composable functions                                     │
│  - State management (mutableStateOf, remember)              │
│  - User interaction handling                                 │
├─────────────────────────────────────────────────────────────┤
│                    Application Layer                         │
│  - ImportService, ReorganizeService, etc.                   │
│  - Use cases and business logic orchestration               │
│  - Similar to Spring @Service layer                         │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                            │
│  - Models (data classes)                                     │
│  - Ports (interfaces) - like Spring repositories            │
│  - Business rules                                            │
├─────────────────────────────────────────────────────────────┤
│                   Infrastructure Layer                       │
│  - Adapters (implementations of ports)                       │
│  - File I/O, EXIF extraction, Settings storage              │
│  - Similar to Spring repository implementations             │
└─────────────────────────────────────────────────────────────┘
```

### Dependency Injection with Koin

Similar to Spring's `@Autowired`, this project uses **Koin** for dependency injection:

```kotlin
// In a composable - inject dependencies
@Composable
fun ImportScreen() {
    val importService = koinInject<ImportService>()
    val namingPort = koinInject<NamingPort>()
    
    // Use services...
}
```

**Koin vs Spring**:
- Koin is simpler, compile-time safe, and designed for Kotlin
- No reflection overhead (unlike Spring)
- Perfect for desktop/mobile applications
- Configuration is in code (AppModule.kt), not annotations

## Compose Fundamentals

### Composable Functions

```kotlin
@Composable
fun Greeting(name: String) {
    Text(text = "Hello, $name!")
}
```

**Rules**:
- Must be annotated with `@Composable`
- Can call other composables
- Should be side-effect free (no direct file I/O, network calls, etc.)
- Can be called from other composables or the composition root

### State Management

```kotlin
@Composable
fun Counter() {
    // State that survives recomposition
    var count by remember { mutableStateOf(0) }
    
    Column {
        Text("Count: $count")
        Button(onClick = { count++ }) {
            Text("Increment")
        }
    }
}
```

**Key Concepts**:
- `remember`: Keeps state across recompositions
- `mutableStateOf`: Creates observable state
- `by` delegate: Unwraps the state value automatically
- When state changes, Compose automatically recomposes affected UI

### State Hoisting

Move state up to make components more testable and reusable:

```kotlin
// Stateless composable (easier to test)
@Composable
fun CounterDisplay(count: Int, onIncrement: () -> Unit) {
    Text("Count: $count")
    Button(onClick = onIncrement) {
        Text("Increment")
    }
}

// Stateful composable (holds state)
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }
    CounterDisplay(count = count, onIncrement = { count++ })
}
```

### Side Effects

Use special composables for side effects:

```kotlin
@Composable
fun MyScreen() {
    // Run once when composable enters composition
    LaunchedEffect(Unit) {
        // Load data, start coroutines
        loadData()
    }
    
    // Run when dependency changes
    LaunchedEffect(userId) {
        // Fetch user data when ID changes
        fetchUser(userId)
    }
    
    // Clean up when leaving composition
    DisposableEffect(Unit) {
        // Setup
        val listener = createListener()
        onDispose {
            // Cleanup
            listener.dispose()
        }
    }
}
```

### Collections and Lists

```kotlin
@Composable
fun ImageList(images: List<ImageFile>) {
    LazyColumn { // Like RecyclerView but declarative
        items(images) { image ->
            ImageItem(image = image)
        }
    }
}
```

## Testing Strategy

### 1. Unit Tests (Domain Layer)

Test pure business logic without UI or external dependencies:

```kotlin
@Test
fun `should calculate correct folder path from pattern`() {
    val config = ImportConfiguration(folderPattern = "{yyyy}/{MM}")
    val metadata = ImageMetadata(date = LocalDate.of(2024, 3, 15))
    
    val result = namingPort.generateFolderPath(config, metadata)
    
    assertThat(result).isEqualTo("2024/03")
}
```

### 2. Service Tests (Application Layer)

Test application services with mocked ports:

```kotlin
@Test
fun `should import files and update history`() = runTest {
    // Given
    val mockRepo = mockk<ImageRepositoryPort>()
    val service = ImportService(mockRepo, ...)
    
    every { mockRepo.save(any()) } returns Unit
    
    // When
    val result = service.import(images, config)
    
    // Then
    verify { mockRepo.save(any()) }
    assertThat(result.successCount).isEqualTo(images.size)
}
```

### 3. UI Component Tests

Test UI components in isolation:

```kotlin
@Test
fun `clicking import button starts import`() {
    var importStarted = false
    
    composeTestRule.setContent {
        ImportScreen(
            settings = testSettings,
            onImportStart = { importStarted = true }
        )
    }
    
    composeTestRule
        .onNodeWithText("Start Import")
        .performClick()
    
    assertThat(importStarted).isTrue()
}
```

### 4. Integration Tests

Test complete workflows:

```kotlin
@Test
fun `full import workflow with real file system`() {
    // Create temp directories
    val source = tempDir.resolve("source").apply { mkdirs() }
    val dest = tempDir.resolve("dest").apply { mkdirs() }
    
    // Create test images
    createTestImage(source, "IMG_001.jpg")
    
    // Run import
    val service = ImportService(...)
    val result = service.import(source, dest, config)
    
    // Verify files were moved
    assertThat(dest.listFiles()).hasSize(1)
}
```

## Common Patterns and Best Practices

### 1. Separation of Concerns

```kotlin
// ❌ Bad: Mixing UI and business logic
@Composable
fun ImportScreen() {
    val files = File(sourcePath).listFiles() // Direct file I/O in UI
    // ...
}

// ✅ Good: Business logic in service, UI just displays
@Composable
fun ImportScreen() {
    val importService = koinInject<ImportService>()
    val images by importService.images.collectAsState()
    // UI only displays state and triggers actions
}
```

### 2. Error Handling

```kotlin
@Composable
fun ImportScreen() {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        try {
            importService.loadImages()
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }
    
    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Error") },
            text = { Text(error) }
        )
    }
}
```

### 3. Loading States

```kotlin
@Composable
fun ImportScreen() {
    var isLoading by remember { mutableStateOf(false) }
    
    Box {
        if (isLoading) {
            CircularProgressIndicator()
        }
        
        // Main content
        Column {
            // ...
        }
    }
}
```

### 4. Responsive Layouts

```kotlin
@Composable
fun ResponsiveLayout() {
    var windowSize by rememberWindowSizeClass()
    
    when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            // Mobile-like layout
            Column { /* ... */ }
        }
        WindowWidthSizeClass.Medium -> {
            // Tablet layout
            Row { /* ... */ }
        }
        WindowWidthSizeClass.Expanded -> {
            // Desktop layout
            Row { /* ... */ }
        }
    }
}
```

## Debugging Tips

### 1. Recomposition Counting

Add this to detect excessive recompositions:

```kotlin
@Composable
fun MyComposable() {
    var recomposeCount by remember { mutableStateOf(0) }
    recomposeCount++
    println("MyComposable recomposed: $recomposeCount times")
    
    // ... rest of composable
}
```

### 2. State Inspection

```kotlin
@Composable
fun MyScreen() {
    var state by remember { mutableStateOf(MyState()) }
    
    // Log state changes
    LaunchedEffect(state) {
        println("State changed: $state")
    }
}
```

### 3. Using IntelliJ Debugger

- Set breakpoints in composable functions
- Inspect state values in the debugger
- Step through recomposition

## Performance Best Practices

### 1. Stable Parameters

```kotlin
// ❌ Bad: Lambda recreated on every recomposition
Button(onClick = { 
    doSomething(param1, param2) 
}) { }

// ✅ Good: Stable lambda
val onClick = remember { { doSomething(param1, param2) } }
Button(onClick = onClick) { }
```

### 2. Lazy Lists for Large Collections

```kotlin
// ❌ Bad: Renders all items at once
Column {
    images.forEach { image ->
        ImageItem(image)
    }
}

// ✅ Good: Only renders visible items
LazyColumn {
    items(images) { image ->
        ImageItem(image)
    }
}
```

### 3. Derived State

```kotlin
// ❌ Bad: Expensive calculation on every recomposition
val filteredImages = images.filter { it.isSelected }

// ✅ Good: Only recalculates when dependencies change
val filteredImages by remember(images) { 
    derivedStateOf { images.filter { it.isSelected } }
}
```

## Migration from Spring Concepts

| Spring Concept | Compose Equivalent |
|---------------|-------------------|
| `@Controller` | `@Composable` functions |
| `@Service` | Service classes (same!) |
| `@Repository` | Port interfaces + Adapter implementations |
| `@Autowired` | `koinInject()` |
| `@Configuration` | Koin modules (`appModule`) |
| Thymeleaf/Templates | Compose UI declarations |
| Spring Boot DevTools | Compose hot reload |
| MockMvc | Compose UI Test |
| Application Properties | SettingsAdapter with JSON |

## Common Gotchas

### 1. Don't Call Composables Conditionally

```kotlin
// ❌ Bad
if (condition) {
    MyComposable()
}

// ✅ Good
if (condition) {
    Box {
        MyComposable()
    }
}
```

### 2. Don't Use Mutable Collections Directly

```kotlin
// ❌ Bad: Changes won't trigger recomposition
var items = mutableListOf<String>()

// ✅ Good: Use immutable collections with state
var items by remember { mutableStateOf(listOf<String>()) }
items = items + "new item"
```

### 3. Remember Coroutines Scope

```kotlin
// ❌ Bad: Scope recreated on every recomposition
val scope = CoroutineScope(Dispatchers.Main)

// ✅ Good: Remember the scope
val scope = rememberCoroutineScope()
```

## Resources

### Official Documentation
- [Compose Multiplatform Documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform.html)
- [Compose API Reference](https://developer.android.com/reference/kotlin/androidx/compose/package-summary)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)

### Learning Resources
- [Compose for Web Developers](https://developer.android.com/courses/pathways/compose-for-web-developers)
- [JetBrains Compose Tutorial](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-quickstart.html)

### Community
- [Kotlin Slack](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up)
- [Compose Multiplatform GitHub](https://github.com/JetBrains/compose-multiplatform)

## Next Steps

1. **Run the application**: `./gradlew run`
2. **Explore the code**: Start with `PetrieFileImporterApp.kt`
3. **Make a small change**: Modify a label or color, see hot reload
4. **Add a preview**: Add `@Preview` to a composable
5. **Write a test**: Add a unit test for a pure function
6. **Build an installer**: `./gradlew packageDmg` (or your OS)

Remember: Compose is declarative. Think "UI is a function of state" and you'll be productive quickly!

## Photo Scan Feature

The Photo Scan feature allows importing physical photographs that have been photographed on a solid background. See the Photo Scan section in README.md for documentation.

### Quick Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      Photo Scan Workflow                         │
├─────────────────────────────────────────────────────────────────┤
│  DETECTING → CORNER_EDITING → METADATA_EDITING → EXPORTING     │
└─────────────────────────────────────────────────────────────────┘
```

### Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `PhotoScanState` | `domain/model/` | Workflow state management |
| `PhotoScanDetectorService` | `application/` | Edge detection for photos |
| `PerspectiveCorrectionService` | `application/` | Bilinear interpolation |
| `PhotoScanExportService` | `application/` | JPEG export with metadata |
| `PhotoScanScreen` | `ui/screens/` | Main orchestration |
| `PhotoScanPreviewScreen` | `ui/screens/` | Corner editing canvas |

### Domain Models

```kotlin
// Photo detection result
data class DetectedPhoto(
    val id: String,
    val topLeft: PhotoCorner,
    val topRight: PhotoCorner,
    val bottomLeft: PhotoCorner,
    val bottomRight: PhotoCorner,
    val configuration: PhotoScanConfiguration
)

// Metadata override
data class PhotoScanConfiguration(
    val dateYear: Int? = null,
    val dateMonth: Int? = null,
    val dateDay: Int? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null
)
```

### Testing Photo Scan

```bash
# Run all photo scan tests
./gradlew test --tests "*PhotoScan*"

# Run specific service tests
./gradlew test --tests "*PhotoScanDetectorServiceTest*"
./gradlew test --tests "*PerspectiveCorrectionServiceTest*"
./gradlew test --tests "*PhotoScanExportServiceTest*"
./gradlew test --tests "*PhotoScanStateTest*"
```

### Adding a New Feature to Photo Scan

1. **Add domain model**: Update `PhotoScanModels.kt` or `PhotoScanConfiguration.kt`
2. **Add state management**: Update `PhotoScanState.kt` with new methods
3. **Implement service logic**: Create or update service in `application/`
4. **Add UI**: Create composable in `ui/screens/`
5. **Register in DI**: Update `di/AppModule.kt`
6. **Add tests**: Create test in `src/test/kotlin/`

### Example: Adding a New Workflow Step

```kotlin
// 1. Add step to enum in PhotoScanState.kt
enum class Step {
    DETECTING,
    CORNER_EDITING,
    METADATA_EDITING,
    EXPORTING,
    COMPLETE,
    NEW_STEP  // <-- Add here
}

// 2. Add state and handlers
fun proceedToNewStep() {
    step.value = Step.NEW_STEP
}

// 3. Add UI in PhotoScanScreen.kt
when (scanState.step.value) {
    PhotoScanState.Step.NEW_STEP -> NewStepScreen(...)
}

// 4. Add transition logic
when (event) {
    is ScanEvent.ProceedToNewStep -> scanState.proceedToNewStep()
}

// 5. Add test
@Test
fun shouldTransitionToNewStep() {
    scanState.proceedToNewStep()
    assertThat(scanState.step.value).isEqualTo(PhotoScanState.Step.NEW_STEP)
}
```
