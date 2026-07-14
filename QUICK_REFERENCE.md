# Quick Reference Guide

## Common Development Tasks

### Running the Application

```bash
# Run with hot reload
./gradlew run

# Or use convenience script
./photo-import.sh
```

### Building Installers

```bash
# macOS
./gradlew packageDmg

# Windows
./gradlew packageMsi

# Linux
./gradlew packageDeb

# All platforms (via scripts)
./build-installers.sh
```

**macOS Architecture Note**: The GitHub Actions workflow builds separate installers for:
- **Intel Macs** (x86_64) - Built on `macos-26-intel` runner
- **Apple Silicon Macs** (arm64) - Built on `macos-26` runner

Download the correct version for your Mac from GitHub Releases.

### Running Tests

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests "org.kryspetrie.fileimport.ui.components.DropTargetTest"

# With coverage
./gradlew test jacocoTestReport
```

### Code Formatting

```bash
# Check formatting
./gradlew ktfmtCheck

# Auto-format
./gradlew ktfmtFormatMain
./gradlew ktfmtFormatTest
```

## Compose UI Patterns

### Adding a New Screen

1. **Create screen composable** in `src/main/kotlin/org/kryspetrie/fileimport/ui/screens/`:

```kotlin
@Composable
fun MyNewScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    // Screen implementation
}
```

2. **Add tab to navigation** in `ui/PetrieFileImporterApp.kt`:

```kotlin
private enum class AppTab(val label: String, val icon: ImageVector) {
    // ... existing tabs ...
    MY_NEW_FEATURE("My Feature", Icons.Default.Star)
}

// In when statement:
when (currentTab) {
    // ... existing cases ...
    AppTab.MY_NEW_FEATURE ->
        MyNewScreen(settings = settings, onSettingsChange = onSettingsChange)
}
```

3. **Add preview** for development:

```kotlin
@Preview(showBackground = true)
@Composable
fun MyNewScreenPreview() {
    PetrieTheme {
        MyNewScreen(
            settings = AppSettings(),
            onSettingsChange = {}
        )
    }
}
```

### Creating a Reusable Component

```kotlin
@Composable
fun MyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    ) {
        Text(text)
    }
}

// Preview
@Preview
@Composable
fun MyButtonPreview() {
    PetrieTheme {
        MyButton(text = "Click Me", onClick = {})
    }
}
```

### State Management Pattern

```kotlin
@Composable
fun MyScreen() {
    // State that survives recomposition
    var count by remember { mutableStateOf(0) }
    
    // Derived state
    val doubledCount by remember(count) { derivedStateOf { count * 2 } }
    
    // Side effects
    LaunchedEffect(count) {
        // Run when count changes
        logCount(count)
    }
    
    // UI
    Text("Count: $count, Doubled: $doubledCount")
    Button(onClick = { count++ }) {
        Text("Increment")
    }
}
```

### Injecting Dependencies

```kotlin
@Composable
fun MyScreen() {
    // Inject services
    val importService = koinInject<ImportService>()
    val namingPort = koinInject<NamingPort>()
    
    // Use in coroutine
    val scope = rememberCoroutineScope()
    
    Button(onClick = {
        scope.launch {
            val result = importService.scanSource("/path")
            // Update UI
        }
    }) {
        Text("Scan")
    }
}
```

### Handling Long-Running Operations

```kotlin
@Composable
fun MyScreen() {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    
    fun startOperation() {
        scope.launch {
            isLoading = true
            error = null
            try {
                result = someSuspendFunction()
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }
    
    Column {
        if (isLoading) {
            CircularProgressIndicator()
        }
        
        error?.let { Text("Error: $it", color = Color.Red) }
        result?.let { Text("Result: $it") }
        
        Button(onClick = { startOperation() }) {
            Text("Start")
        }
    }
}
```

### Working with Flows

```kotlin
@Composable
fun MyScreen() {
    val service = koinInject<ImportService>()
    
    // Collect StateFlow as state
    val progress by service.importProgress.collectAsState()
    
    // Collect Flow in LaunchedEffect
    var items by remember { mutableStateOf<List<String>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        someFlow().collect { newItems ->
            items = newItems
        }
    }
    
    LinearProgressIndicator(progress = progress.percentage)
    Text("${progress.currentFile}")
}
```

## Testing Patterns

### Unit Test (Pure Function)

```kotlin
@Test
fun `should generate correct folder path`() {
    val config = ImportConfiguration(folderPattern = "{yyyy}/{MM}")
    val metadata = ImageMetadata(date = LocalDate.of(2024, 3, 15))
    
    val result = namingPort.generateFolderPath(config, metadata)
    
    assertThat(result).isEqualTo("2024/03")
}
```

### Service Test with Mocks

```kotlin
@Test
fun `should import files successfully`() = runTest {
    // Given
    val mockRepo = mockk<ImageRepositoryPort>()
    val service = ImportService(mockRepo, ...)
    
    every { mockRepo.copyFile(any(), any()) } returns Unit
    
    // When
    val result = service.executeImport(images, dest, config)
    
    // Then
    assertThat(result.successCount).isEqualTo(images.size)
    verify(exactly = images.size) { mockRepo.copyFile(any(), any()) }
}
```

### UI Component Test

```kotlin
@Test
fun `clicking button triggers callback`() {
    var clicked = false
    
    composeTestRule.setContent {
        MyButton(text = "Click", onClick = { clicked = true })
    }
    
    composeTestRule
        .onNodeWithText("Click")
        .performClick()
    
    assertThat(clicked).isTrue()
}
```

## Debugging Tips

### Check Recomposition

```kotlin
@Composable
fun MyComposable() {
    var recomposeCount by remember { mutableStateOf(0) }
    recomposeCount++
    println("MyComposable recomposed: $recomposeCount")
    // ...
}
```

### Log State Changes

```kotlin
LaunchedEffect(myState) {
    println("State changed: $myState")
}
```

### Inspect Theme Colors

```kotlin
@Composable
fun DebugColors() {
    Column {
        Text("Primary: ${MaterialTheme.colorScheme.primary}")
        Text("Background: ${MaterialTheme.colorScheme.background}")
    }
}
```

## File Structure

```
src/
├── main/kotlin/org/kryspetrie/fileimport/
│   ├── PetrieFileImporterApp.kt    # Main entry point
│   ├── di/AppModule.kt              # Dependency injection
│   ├── ui/
│   │   ├── PetrieFileImporterApp.kt # Main UI composable
│   │   ├── theme/Theme.kt           # Theme configuration
│   │   ├── screens/                 # Screen composables
│   │   └── components/              # Reusable components
│   ├── application/                 # Application layer services
│   ├── domain/
│   │   ├── model/                   # Data classes
│   │   └── port/                    # Interfaces (ports)
│   └── infrastructure/
│       └── adapter/                 # Implementations (adapters)
└── test/kotlin/                     # Tests
```

## Common Gradle Commands

```bash
# Build
./gradlew build

# Run
./gradlew run

# Test
./gradlew test

# Format
./gradlew ktfmtFormatMain

# Clean
./gradlew clean

# Build specific installer
./gradlew packageDmg      # macOS
./gradlew packageMsi      # Windows
./gradlew packageDeb      # Linux

# Build uber JAR
./gradlew packageUberJarForCurrentOS

# Dependencies
./gradlew dependencies
./gradlew dependencyUpdates  # Check for updates
```

## Keyboard Shortcuts (IntelliJ)

- **Cmd/Ctrl + Shift + A**: Search actions
- **Cmd/Ctrl + B**: Go to declaration
- **Cmd/Ctrl + Alt + L**: Reformat code
- **Cmd/Ctrl + /**: Toggle comment
- **Shift + Shift**: Search everywhere
- **Cmd/Ctrl + F12**: File structure
- **Cmd/Ctrl + E**: Recent files

## Getting Help

1. **Check KDoc**: Hover over symbols in IntelliJ
2. **DEVELOPER_GUIDE.md**: Comprehensive guide for Spring developers
3. **Compose Docs**: https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform.html
4. **Kotlin Slack**: https://kotlinlang.slack.com
