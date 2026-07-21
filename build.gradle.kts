plugins {
    kotlin("jvm") version "2.3.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
    id("org.jetbrains.compose") version "1.11.0-beta01"
    kotlin("plugin.serialization") version "2.3.10"
    id("com.ncorti.ktfmt.gradle") version "0.25.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

ktfmt {
    // Google style (4-space indent) matches our current formatting
    kotlinLangStyle()
    // Remove unused imports automatically
    removeUnusedImports = true
}

group = "org.kryspetrie.fileimport"

version = "1.0.0"

kotlin { jvmToolchain(21) }

compose.desktop {
    application {
        mainClass = "org.kryspetrie.fileimport.PetrieFileImporterAppKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "Petrie Image Importer"
            packageVersion = "1.0.0"
            description = "Cross-platform photo and video importer"
            vendor = "Kryspetrie"

            // Bundled JRE modules — includes everything needed by the app
            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.naming",
                "java.sql",
                "java.xml",
                "jdk.unsupported",
            )

            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
                debMaintainer = "kryspetrie"
                appCategory = "Photography"
            }
            macOS {
                iconFile.set(project.file("src/main/resources/icon.icns"))
                bundleID = "org.kryspetrie.fileimport"
                appCategory = "public.app-category.photography"
            }
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
                menuGroup = "Petrie Image Importer"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.ui)
    implementation(compose.uiTooling)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.foundation)
    implementation(compose.runtime)

    implementation("com.github.ajalt.clikt:clikt:4.2.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.drewnoakes:metadata-extractor:2.19.0")
    implementation("org.imgscalr:imgscalr-lib:4.2")
    implementation("commons-codec:commons-codec:1.17.1")
    implementation("org.xerial:sqlite-jdbc:3.49.0.0")
    implementation("org.boofcv:boofcv-feature:1.2.2")
    implementation("com.microsoft.onnxruntime:onnxruntime:1.21.0")

    implementation("org.apache.commons:commons-imaging:1.0-alpha3")
    implementation("javax.inject:javax.inject:1")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("io.insert-koin:koin-core:4.0.0")
    implementation("io.insert-koin:koin-core-coroutines:4.0.0")
    implementation("io.insert-koin:koin-compose:4.0.0")
    implementation("org.jline:jline:3.27.1")

    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.21.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.junit.platform:junit-platform-launcher:1.11.4")

    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.lemonappdev:konsist:0.17.3")
    testImplementation("org.jetbrains.compose.ui:ui-test-junit4:1.11.0-beta01")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.3.10")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.11.4") {
        because("ComposeTestRule requires JUnit 4 rule support within JUnit 5")
    }

    tasks.test {
        useJUnitPlatform { excludeTags("UiComponentTest", "integration") }
        testLogging { showStandardStreams = true }
        // ONNX models (orientation detection ~350MB) require additional heap
        jvmArgs("-Xmx2g")
    }

    tasks.register<Test>("uiTest") {
        description = "Runs UI component tests (Compose rendering tests)"
        group = "verification"
        useJUnitPlatform { includeTags("UiComponentTest") }
        testLogging { showStandardStreams = true }
        classpath = sourceSets["test"].runtimeClasspath
        testClassesDirs = sourceSets["test"].output.classesDirs
    }

    tasks.register<Test>("integrationTest") {
        description = "Runs integration tests that make real network calls"
        group = "verification"
        useJUnitPlatform { includeTags("integration") }
        testLogging { showStandardStreams = true }
        classpath = sourceSets["test"].runtimeClasspath
        testClassesDirs = sourceSets["test"].output.classesDirs
    }

    tasks.register<JavaExec>("runMapTileTest") {
        description = "Launches a standalone window to test OsmMapView tile rendering"
        group = "verification"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("org.kryspetrie.fileimport.ui.screens.wizard.metadata.MapTileRenderTestAppKt")
        dependsOn("classes")
    }

    tasks.register<JavaExec>("runLocationPickerTest") {
        description =
            "Launches a standalone window to test the full LocationPickerContent (search + map)"
        group = "verification"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set(
            "org.kryspetrie.fileimport.ui.screens.wizard.metadata.LocationPickerTestAppKt"
        )
        dependsOn("classes")
    }

    tasks.register<JavaExec>("generateIcons") {
        description = "Generates application icon files for native packaging"
        group = "build setup"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("org.kryspetrie.fileimport.ui.util.GenerateIconsKt")
        dependsOn("classes")
        val iconFile = project.file("src/main/resources/icon.png")
        outputs.file(iconFile)
        onlyIf { !iconFile.exists() }
    }

    tasks
        .matching { it.name.startsWith("package") || it.name.startsWith("createDistributable") }
        .configureEach { dependsOn("generateIcons") }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(file("detekt.yml"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { jvmTarget = "21" }
