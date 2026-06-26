package org.kryspetrie.fileimport.architecture

import com.lemonappdev.konsist.api.Konsist
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Architecture tests enforcing the hexagonal (Ports & Adapters) architecture of Petrie File
 * Importer.
 *
 * These tests use Konsist to inspect the codebase structure and AssertJ for assertions, verifying
 * that the codebase adheres to the dependency rules documented in docs/ARCHITECTURE.md:
 * - **Domain** layer has zero dependencies on application, infrastructure, or UI
 * - **Application** layer depends only on domain (and documented boundary converters)
 * - **Infrastructure** implements domain ports; does not depend on UI
 * - **UI** depends on domain and application use-case services; imports infrastructure only through
 *   documented exceptions; imports application algorithm services only through domain ports
 * - **Port naming**: interfaces in domain.port end with Port, Provider, or Generator
 * - **No javax.inject**: project uses Koin, not JSR-330
 *
 * @see <a href="../../../docs/ARCHITECTURE.md">Architecture documentation</a>
 */
@DisplayName("Hexagonal Architecture")
class HexagonalArchitectureKonsistTest {

    private val scope = Konsist.scopeFromProduction()

    // ── Domain Layer Purity ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Domain Layer")
    inner class DomainLayerTests {

        @Test
        @DisplayName("Domain must not import from infrastructure package")
        fun domainMustNotImportInfrastructure() {
            val domainFiles =
                scope.files.filter { file ->
                    file.packagee?.name?.startsWith("org.kryspetrie.fileimport.domain") == true
                }

            val violations =
                domainFiles.flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("org.kryspetrie.fileimport.infrastructure") }
                        .map { "${file.name} imports ${it.name}" }
                }

            assertThat(violations)
                .withFailMessage(
                    "Domain layer must not import from infrastructure.\nViolations:\n" +
                        violations.joinToString("\n")
                )
                .isEmpty()
        }

        @Test
        @DisplayName("Domain must not import from UI package")
        fun domainMustNotImportUI() {
            val domainFiles =
                scope.files.filter { file ->
                    file.packagee?.name?.startsWith("org.kryspetrie.fileimport.domain") == true
                }

            val violations =
                domainFiles.flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("org.kryspetrie.fileimport.ui") }
                        .map { "${file.name} imports ${it.name}" }
                }

            assertThat(violations)
                .withFailMessage(
                    "Domain layer must not import from UI.\nViolations:\n" +
                        violations.joinToString("\n")
                )
                .isEmpty()
        }

        @Test
        @DisplayName("Domain must not import from application package")
        fun domainMustNotImportApplication() {
            val domainFiles =
                scope.files.filter { file ->
                    file.packagee?.name?.startsWith("org.kryspetrie.fileimport.domain") == true
                }

            val violations =
                domainFiles.flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("org.kryspetrie.fileimport.application") }
                        .map { "${file.name} imports ${it.name}" }
                }

            assertThat(violations)
                .withFailMessage(
                    "Domain layer must not import from application.\nViolations:\n" +
                        violations.joinToString("\n")
                )
                .isEmpty()
        }

        @Test
        @DisplayName("Domain must not import from DI package")
        fun domainMustNotImportDI() {
            val domainFiles =
                scope.files.filter { file ->
                    file.packagee?.name?.startsWith("org.kryspetrie.fileimport.domain") == true
                }

            val violations =
                domainFiles.flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("org.kryspetrie.fileimport.di") }
                        .map { "${file.name} imports ${it.name}" }
                }

            assertThat(violations)
                .withFailMessage(
                    "Domain layer must not import from DI.\nViolations:\n" +
                        violations.joinToString("\n")
                )
                .isEmpty()
        }

        @Test
        @DisplayName("Domain must not import java.awt or javax.imageio")
        fun domainMustNotImportAWT() {
            val domainFiles =
                scope.files.filter { file ->
                    file.packagee?.name?.startsWith("org.kryspetrie.fileimport.domain") == true
                }

            val violations =
                domainFiles.flatMap { file ->
                    file.imports
                        .filter { import ->
                            import.name.startsWith("java.awt") ||
                                import.name.startsWith("javax.imageio")
                        }
                        .map { "${file.name} imports ${it.name}" }
                }

            assertThat(violations)
                .withFailMessage(
                    "Domain layer must not import java.awt or javax.imageio.\nViolations:\n" +
                        violations.joinToString("\n")
                )
                .isEmpty()
        }

        @Test
        @DisplayName("Domain must not import javax.inject")
        fun domainMustNotImportJavaxInject() {
            val domainFiles =
                scope.files.filter { file ->
                    file.packagee?.name?.startsWith("org.kryspetrie.fileimport.domain") == true
                }

            val violations =
                domainFiles.flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("javax.inject") }
                        .map { "${file.name} imports ${it.name}" }
                }

            assertThat(violations)
                .withFailMessage(
                    "Domain layer must not import javax.inject.\nViolations:\n" +
                        violations.joinToString("\n")
                )
                .isEmpty()
        }
    }

    // ── Application Layer Rules ────────────────────────────────────────────────

    @Nested
    @DisplayName("Application Layer")
    inner class ApplicationLayerTests {

        @Test
        @DisplayName("Application must not import from UI package")
        fun applicationMustNotImportUI() {
            val appFiles =
                scope.files.filter { file ->
                    file.packagee?.name?.startsWith("org.kryspetrie.fileimport.application") == true
                }

            val violations =
                appFiles.flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("org.kryspetrie.fileimport.ui") }
                        .map { "${file.name} imports ${it.name}" }
                }

            assertThat(violations)
                .withFailMessage(
                    "Application must not import from UI.\nViolations:\n" +
                        violations.joinToString("\n")
                )
                .isEmpty()
        }

        @Test
        @DisplayName("Application must not import javax.inject")
        fun applicationMustNotImportJavaxInject() {
            val appFiles =
                scope.files.filter { file ->
                    file.packagee?.name?.startsWith("org.kryspetrie.fileimport.application") == true
                }

            val violations =
                appFiles.flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("javax.inject") }
                        .map { "${file.name} imports ${it.name}" }
                }

            assertThat(violations)
                .withFailMessage(
                    "Application must not import javax.inject.\nViolations:\n" +
                        violations.joinToString("\n")
                )
                .isEmpty()
        }

        @Test
        @DisplayName("Application must not import from infrastructure package")
        fun applicationMustNotImportInfrastructure() {
            val appFiles =
                scope.files.filter { file ->
                    file.packagee?.name?.startsWith("org.kryspetrie.fileimport.application") == true
                }

            val violations =
                appFiles.flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("org.kryspetrie.fileimport.infrastructure") }
                        .map { "${file.name} imports ${it.name}" }
                }

            assertThat(violations)
                .withFailMessage(
                    "Application layer must not import from infrastructure.\nViolations:\n" +
                        violations.joinToString("\n")
                )
                .isEmpty()
        }

        /**
         * Application layer must not import java.awt or javax.imageio.
         *
         * All AWT-dependent pixel operations (crop, rotate, composite, JPEG write, image I/O)
         * are handled by [ImageProcessingPort] in the infrastructure layer. Application services
         * use [ProcessedImage] and domain ports exclusively.
         */
        @Test
        @DisplayName("Application must not import java.awt or javax.imageio")
        fun applicationMustNotImportAWT() {
            val appFiles =
                scope.files.filter { file ->
                    file.packagee?.name?.startsWith("org.kryspetrie.fileimport.application") == true
                }

            val violations =
                appFiles.flatMap { file ->
                    file.imports
                        .filter { import ->
                            import.name.startsWith("java.awt") ||
                                import.name.startsWith("javax.imageio")
                        }
                        .map { "${file.name} imports ${it.name}" }
                }

            assertThat(violations)
                .withFailMessage(
                    "Application layer must not import java.awt or javax.imageio. " +
                        "All AWT-dependent operations should go through ImageProcessingPort.\n" +
                        "Violations:\n${violations.joinToString("\n")}"
                )
                .isEmpty()
        }
    }

    // ── Infrastructure Layer Rules ──────────────────────────────────────────────

    @Nested
    @DisplayName("Infrastructure Layer")
    inner class InfrastructureLayerTests {

        @Test
        @DisplayName("Infrastructure must not import from UI package")
        fun infrastructureMustNotImportUI() {
            val infraFiles =
                scope.files.filter { file ->
                    file.packagee?.name?.startsWith("org.kryspetrie.fileimport.infrastructure") ==
                        true
                }

            val violations =
                infraFiles.flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("org.kryspetrie.fileimport.ui") }
                        .map { "${file.name} imports ${it.name}" }
                }

            assertThat(violations)
                .withFailMessage(
                    "Infrastructure must not import from UI.\nViolations:\n" +
                        violations.joinToString("\n")
                )
                .isEmpty()
        }

        @Test
        @DisplayName("Infrastructure must not import javax.inject")
        fun infrastructureMustNotImportJavaxInject() {
            val infraFiles =
                scope.files.filter { file ->
                    file.packagee?.name?.startsWith("org.kryspetrie.fileimport.infrastructure") ==
                        true
                }

            val violations =
                infraFiles.flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("javax.inject") }
                        .map { "${file.name} imports ${it.name}" }
                }

            assertThat(violations)
                .withFailMessage(
                    "Infrastructure must not import javax.inject.\nViolations:\n" +
                        violations.joinToString("\n")
                )
                .isEmpty()
        }
    }

    // ── UI Layer Rules ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("UI Layer")
    inner class UILayerTests {

        /**
         * Documented exceptions where UI is allowed to import infrastructure types directly.
         *
         * These are documented in docs/ARCHITECTURE.md as acceptable boundary crossings.
         */
        private val allowedInfrastructureImportsInUI: Set<String> = buildSet {
            // Adapter utilities & AWT bridge extensions
            add("org.kryspetrie.fileimport.infrastructure.adapter.Platform")
            add("org.kryspetrie.fileimport.infrastructure.adapter.AppPaths")
            add("org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage")
            add("org.kryspetrie.fileimport.infrastructure.adapter.toBufferedImage")
            add("org.kryspetrie.fileimport.infrastructure.adapter.ThumbnailExtractorAdapter")
            add("org.kryspetrie.fileimport.infrastructure.adapter.FilePathExt")
            add("org.kryspetrie.fileimport.infrastructure.adapter.correctPerspective")
            add("org.kryspetrie.fileimport.infrastructure.adapter.transformFaceRegionsFromSource")

            // Logging (documented boundary exception)
            add("org.kryspetrie.fileimport.infrastructure.logging.AppLogger")
            add("org.kryspetrie.fileimport.infrastructure.logging.OperationType")
        }

        /**
         * Application-layer services that the UI is allowed to import directly.
         *
         * These are use-case orchestration services accessed via Koin DI. Algorithm/detail services
         * (PerspectiveCorrectionService, FaceRegionTransformer, etc.) are now in infrastructure
         * and accessed through their domain port interfaces.
         */
        private val allowedApplicationImportsInUI: Set<String> = buildSet {
            // Use-case services (orchestration, no direct AWT coupling)
            add("org.kryspetrie.fileimport.application.ImportService")
            add("org.kryspetrie.fileimport.application.ReorganizeService")
            add("org.kryspetrie.fileimport.application.DuplicateScannerService")
            add("org.kryspetrie.fileimport.application.WatchFolderService")
            add("org.kryspetrie.fileimport.application.PhotoScanExportService")
            add("org.kryspetrie.fileimport.application.ScanService")
        }

        @Test
        @DisplayName("UI must not import infrastructure adapters except documented exceptions")
        fun uiMustNotImportInfrastructureAdaptersExceptExceptions() {
            val uiFiles =
                scope.files.filter { file ->
                    file.packagee?.name?.startsWith("org.kryspetrie.fileimport.ui") == true
                }

            val violations =
                uiFiles.flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("org.kryspetrie.fileimport.infrastructure") }
                        .filter { it.name !in allowedInfrastructureImportsInUI }
                        .map { "${file.name} imports ${it.name}" }
                }

            assertThat(violations)
                .withFailMessage(
                    "UI must not import infrastructure types except documented exceptions.\n" +
                        "See docs/ARCHITECTURE.md for the list of boundary exceptions.\n" +
                        "Violations:\n${violations.joinToString("\n")}"
                )
                .isEmpty()
        }

        @Test
        @DisplayName("UI application imports must be use-case services or documented exceptions")
        fun uiApplicationImportsMustBeUseCaseServicesOrDocumentedExceptions() {
            val uiFiles =
                scope.files.filter { file ->
                    file.packagee?.name?.startsWith("org.kryspetrie.fileimport.ui") == true
                }

            val violations =
                uiFiles.flatMap { file ->
                    file.imports
                        .filter { it.name.startsWith("org.kryspetrie.fileimport.application") }
                        .filter { it.name !in allowedApplicationImportsInUI }
                        .map { "${file.name} imports ${it.name}" }
                }

            assertThat(violations)
                .withFailMessage(
                    "UI may only import application use-case services or documented AWT " +
                        "boundary exceptions.\n" +
                        "Allowed: ${allowedApplicationImportsInUI.sorted().joinToString(", ")}\n" +
                        "Violations:\n${violations.joinToString("\n")}"
                )
                .isEmpty()
        }
    }

    // ── Port Naming Convention ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Port Naming Convention")
    inner class PortNamingTests {

        @Test
        @DisplayName("Port interfaces should be named ending with Port, Provider, or Generator")
        fun portInterfacesFollowNamingConvention() {
            val portInterfaces =
                scope.interfaces().filter { iface ->
                    iface.resideInPackage("org.kryspetrie.fileimport.domain.port..")
                }

            val invalidNames =
                portInterfaces
                    .map { it.name }
                    .filter { name ->
                        !name.endsWith("Port") &&
                            !name.endsWith("Provider") &&
                            !name.endsWith("Generator")
                    }

            assertThat(invalidNames)
                .withFailMessage(
                    "Port interfaces should end with 'Port', 'Provider', or 'Generator'. " +
                        "Invalid: $invalidNames"
                )
                .isEmpty()
        }
    }

    // ── No javax.inject ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No javax.inject")
    inner class NoJavaxInjectTests {

        @Test
        @DisplayName("No source file should import javax.inject")
        fun noSourceFileShouldImportJavaxInject() {
            val javaxImports =
                scope.imports
                    .filter { it.name.startsWith("javax.inject") }
                    .map { "${it.containingFile.name} → ${it.name}" }

            assertThat(javaxImports)
                .withFailMessage(
                    "Project uses Koin DI, not JSR-330. Found javax.inject imports:\n" +
                        javaxImports.joinToString("\n")
                )
                .isEmpty()
        }
    }
}