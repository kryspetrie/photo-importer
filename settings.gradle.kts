pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/release")
    }
}

rootProject.name = "petrie-file-importer"

// Composite build: photo-metadata-editor library modules (ExifTool-backed metadata I/O)
includeBuild("../photo-metadata-editor")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/release")
        maven("https://jitpack.io")
    }
}
