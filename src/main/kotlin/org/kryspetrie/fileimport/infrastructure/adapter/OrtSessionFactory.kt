package org.kryspetrie.fileimport.infrastructure.adapter

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger

/**
 * Creates ONNX Runtime sessions with GPU acceleration when available.
 *
 * OnnxRuntime supports multiple execution providers (EPs) for hardware acceleration:
 *
 * | Platform | Provider | Backend    | Status                       |
 * |----------|----------|------------|------------------------------|
 * | macOS    | CoreML   | Metal/ANE  | Included in standard JAR     |
 * | Windows  | DirectML | DX12       | Included in standard JAR     |
 * | Linux    | CUDA     | NVIDIA GPU | Requires onnxruntime_gpu JAR |
 * | Any      | CPU      | —          | Always available             |
 *
 * This factory tries providers in platform-specific order and falls back gracefully:
 * 1. **macOS**: CoreML → CPU
 * 2. **Windows**: DirectML → CPU
 * 3. **Linux**: CUDA → CPU
 * 4. **Other**: CPU only
 *
 * When a GPU EP fails to initialize (missing driver, no hardware, library mismatch), the factory
 * silently falls back to CPU so the application always works.
 *
 * ## Session options lifecycle
 *
 * Each attempt to enable a GPU provider creates a fresh [SessionOptions] — if the provider fails,
 * the tainted options are discarded. This avoids the problem where a failed
 * `addCoreML()`/`addCUDA()`/`addDirectML()` call leaves options in an inconsistent state (ONNX
 * Runtime doesn't support removing a provider from options).
 *
 * ## Single responsibility
 *
 * Every ONNX session in the app should be created through this factory. This ensures consistent
 * GPU-usage behavior across all models (detection, pose, corner regression, face detection) and
 * centralizes session configuration (thread count, optimization level).
 *
 * @param env Shared ONNX Runtime environment
 * @param appLogger Optional logger for diagnostic output about EP selection
 */
class OrtSessionFactory(
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment(),
    private val appLogger: AppLogger? = null,
) {

    /**
     * Which execution provider was successfully enabled, or [Provider.CPU] for software fallback.
     *
     * Set lazily on first session creation and then cached. Accessing before any session is created
     * returns [Provider.NONE].
     */
    var activeProvider: Provider = Provider.NONE
        private set

    /** Whether GPU acceleration has been successfully initialized. */
    val isGpuAvailable: Boolean
        get() = activeProvider != Provider.NONE && activeProvider != Provider.CPU

    /**
     * Creates a new ONNX Runtime session with the best available execution provider.
     *
     * The model bytes are passed directly to ONNX Runtime — no temporary files. Session options are
     * configured with:
     * - **Graph optimization**: ALL_OPT (maximum)
     * - **Intra-op threads**: half of available CPUs, capped at 8
     * - **Inter-op threads**: 1 (sequential graph execution; models are small)
     * - **Execution provider**: best available for the current platform
     *
     * @param modelBytes ONNX model bytes (loaded from [ModelResourcePort])
     * @return Configured ONNX session ready for inference
     * @throws OrtException if session creation fails even on CPU (broken model, no runtime)
     */
    fun createSession(modelBytes: ByteArray): OrtSession {
        val cpuCount = Runtime.getRuntime().availableProcessors()
        val intraThreads = (cpuCount / 2).coerceIn(1, 8)

        // First session: try GPU providers in platform-specific priority order
        if (activeProvider == Provider.NONE) {
            val candidates = providerPriority()
            for (provider in candidates) {
                val result = tryCreateWithProvider(modelBytes, provider, intraThreads)
                if (result != null) {
                    activeProvider = provider
                    appLogger?.info(
                        "OrtSessionFactory: Enabled ${provider.displayName} execution provider " +
                            "(intra=$intraThreads, inter=1)"
                    )
                    return result
                }
            }
            // All GPU providers failed — use CPU
            activeProvider = Provider.CPU
            appLogger?.info(
                "OrtSessionFactory: No GPU provider available, using CPU " +
                    "(intra=$intraThreads, inter=1)"
            )
        }

        // Provider already determined — create session accordingly
        return if (isGpuAvailable) {
            tryCreateWithProvider(modelBytes, activeProvider, intraThreads)
                ?: createCpuSessionInternal(modelBytes, intraThreads).also {
                    activeProvider = Provider.CPU
                }
        } else {
            createCpuSessionInternal(modelBytes, intraThreads)
        }
    }

    /**
     * Creates a session using only the CPU execution provider.
     *
     * Useful for testing or when GPU is explicitly disabled.
     */
    fun createCpuSession(modelBytes: ByteArray): OrtSession {
        val cpuCount = Runtime.getRuntime().availableProcessors()
        val intraThreads = (cpuCount / 2).coerceIn(1, 8)
        if (activeProvider == Provider.NONE) {
            activeProvider = Provider.CPU
        }
        return createCpuSessionInternal(modelBytes, intraThreads)
    }

    // ── Internal ──────────────────────────────────────────────────────

    /** Platform-specific provider priority order. */
    private fun providerPriority(): List<Provider> =
        when {
            Platform.isMac -> listOf(Provider.CORE_ML)
            Platform.isWindows -> listOf(Provider.DIRECT_ML)
            Platform.isLinux -> listOf(Provider.CUDA)
            else -> emptyList()
        }

    /**
     * Try to create a session with a specific GPU provider.
     *
     * Returns null if the provider is unavailable (throws [OrtException]). Creates fresh
     * [SessionOptions] for each attempt — if the `addXxx()` call throws, the options are discarded
     * cleanly.
     */
    private fun tryCreateWithProvider(
        modelBytes: ByteArray,
        provider: Provider,
        intraThreads: Int,
    ): OrtSession? {
        return try {
            val opts = SessionOptions()
            opts.setOptimizationLevel(OptLevel.ALL_OPT)
            opts.setIntraOpNumThreads(intraThreads)
            opts.setInterOpNumThreads(1)

            when (provider) {
                Provider.CORE_ML -> opts.addCoreML()
                Provider.CUDA -> opts.addCUDA()
                Provider.DIRECT_ML -> opts.addDirectML(0)
                else -> return null
            }

            env.createSession(modelBytes, opts)
        } catch (e: OrtException) {
            appLogger?.info(
                "OrtSessionFactory: ${provider.displayName} not available: " +
                    "${e.message?.take(120)}"
            )
            null
        }
    }

    /** Create a CPU-only session with standard options. */
    private fun createCpuSessionInternal(modelBytes: ByteArray, intraThreads: Int): OrtSession {
        val opts = SessionOptions()
        opts.setOptimizationLevel(OptLevel.ALL_OPT)
        opts.setIntraOpNumThreads(intraThreads)
        opts.setInterOpNumThreads(1)
        return env.createSession(modelBytes, opts)
    }

    /**
     * GPU execution providers supported by ONNX Runtime.
     *
     * Ordered by platform priority for the try-enable sequence.
     */
    enum class Provider(val displayName: String) {
        /** No provider selected yet (before first session creation) */
        NONE("None"),
        /** Default CPU execution */
        CPU("CPU"),
        /** Apple CoreML (Metal/ANE) for macOS */
        CORE_ML("CoreML"),
        /** NVIDIA CUDA for Linux */
        CUDA("CUDA"),
        /** DirectML (DX12) for Windows */
        DIRECT_ML("DirectML"),
    }
}
