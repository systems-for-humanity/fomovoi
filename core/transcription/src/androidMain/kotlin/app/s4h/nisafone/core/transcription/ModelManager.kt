package app.s4h.nisafone.core.transcription

import android.content.Context
import android.os.StrictMode
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages speech recognition model downloads and storage.
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val MODELS_DIR = "speech-models"
        private const val PREFS_NAME = "model_manager_prefs"
        private const val PREF_SELECTED_MODEL = "selected_model"
    }

    private val prefs by lazy {
        val oldPolicy = StrictMode.allowThreadDiskReads()
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }

    private val modelsBaseDir by lazy {
        val oldPolicy = StrictMode.allowThreadDiskReads()
        try {
            File(context.filesDir, MODELS_DIR).also { it.mkdirs() }
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }

    private val discoveryService = ModelDiscoveryService()

    private val _downloadingModels = MutableStateFlow<Set<String>>(emptySet())
    val downloadingModels: StateFlow<Set<String>> = _downloadingModels.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    // Cached discovered models
    private var discoveredModels: List<SpeechModel>? = null

    /**
     * Discover available models from Hugging Face.
     * Results are cached after first call.
     * When offline, falls back to catalog + any locally downloaded models.
     */
    suspend fun discoverModels(): List<SpeechModel> {
        discoveredModels?.let { return it }

        return try {
            val models = discoveryService.discoverModels()
            if (models.isEmpty()) {
                // Discovery swallows network errors and returns empty list
                Log.w(TAG, "Discovery returned no models, using offline fallback")
                val fallback = buildOfflineFallback()
                discoveredModels = fallback
                fallback
            } else {
                discoveredModels = models
                Log.d(TAG, "Discovered ${models.size} models from Hugging Face")
                models
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discover models, using fallback catalog: ${e.message}")
            val fallback = buildOfflineFallback()
            discoveredModels = fallback
            fallback
        }
    }

    /**
     * Build a model list for offline use by combining the static catalog
     * with any previously downloaded models found on disk.
     */
    private fun buildOfflineFallback(): List<SpeechModel> {
        val catalogModels = SpeechModelCatalog.allModels.associateBy { it.id }.toMutableMap()

        // Scan the models directory for downloaded models not in the catalog
        if (modelsBaseDir.exists()) {
            modelsBaseDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val modelId = dir.name
                if (modelId !in catalogModels) {
                    val model = reconstructModelFromDisk(modelId, dir)
                    if (model != null) {
                        catalogModels[modelId] = model
                        Log.d(TAG, "Found offline model: ${model.displayName}")
                    }
                }
            }
        }

        return catalogModels.values.toList()
    }

    /**
     * Reconstruct a SpeechModel from its on-disk directory.
     * Parses the model ID to determine type and language.
     */
    private fun reconstructModelFromDisk(modelId: String, dir: File): SpeechModel? {
        // Model IDs follow the pattern: whisper-{size}[.{lang}]
        val suffix = modelId.removePrefix("whisper-")
        if (suffix == modelId) return null // not a whisper model

        val isEnglish = suffix.endsWith(".en")
        val language = if (isEnglish) SpeechLanguage.ENGLISH else SpeechLanguage.MULTILINGUAL
        val sizeStr = if (isEnglish) suffix.removeSuffix(".en") else suffix

        val type = when {
            sizeStr == "tiny" -> SpeechModelType.WHISPER_TINY
            sizeStr == "base" -> SpeechModelType.WHISPER_BASE
            sizeStr == "small" -> SpeechModelType.WHISPER_SMALL
            sizeStr == "medium" -> SpeechModelType.WHISPER_MEDIUM
            sizeStr.startsWith("large") -> SpeechModelType.WHISPER_LARGE
            sizeStr == "turbo" -> SpeechModelType.WHISPER_TURBO
            sizeStr.startsWith("distil") -> SpeechModelType.WHISPER_DISTIL
            else -> SpeechModelType.WHISPER_OTHER
        }

        val files = dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.map { ModelFile(it.name, it.length()) }
            ?: return null

        if (files.isEmpty()) return null

        val repoName = "sherpa-onnx-whisper-$suffix"
        return SpeechModel(
            id = modelId,
            type = type,
            language = language,
            baseUrl = "https://huggingface.co/csukuangfj/$repoName/resolve/main",
            files = files
        )
    }

    /**
     * Get only locally downloaded models. No network call.
     * Scans disk for downloaded models and returns them with isDownloaded status.
     */
    fun getLocalModels(): List<SpeechModel> {
        val allModels = buildOfflineFallback()
        return allModels
            .map { model -> model.copy(isDownloaded = isModelDownloaded(model)) }
            .filter { it.isDownloaded }
    }

    /**
     * Get all available models with their download status.
     * Uses cached discovered models or falls back to static catalog.
     * Always includes locally downloaded models even if not in the discovered list.
     */
    fun getAvailableModels(): List<SpeechModel> {
        val models = discoveredModels ?: SpeechModelCatalog.allModels
        val modelsWithStatus = models.map { model ->
            model.copy(isDownloaded = isModelDownloaded(model))
        }

        // Merge in any downloaded models not in the discovered/catalog list
        if (discoveredModels != null) {
            val knownIds = modelsWithStatus.map { it.id }.toSet()
            val extraLocal = getLocalModels().filter { it.id !in knownIds }
            return modelsWithStatus + extraLocal
        }

        return modelsWithStatus
    }

    /**
     * Check if a model is fully downloaded.
     */
    fun isModelDownloaded(model: SpeechModel): Boolean {
        val modelDir = getModelDir(model)
        if (!modelDir.exists()) return false
        return model.files.all { file ->
            val localFile = File(modelDir, file.name)
            localFile.exists() && localFile.length() == file.expectedSize
        }
    }

    /**
     * Get the directory for a specific model.
     */
    fun getModelDir(model: SpeechModel): File {
        return File(modelsBaseDir, model.id)
    }

    /**
     * Download a model.
     */
    suspend fun downloadModel(
        model: SpeechModel,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isModelDownloaded(model)) {
            return@withContext Result.success(Unit)
        }

        _downloadingModels.update { it + model.id }
        _downloadProgress.update { it + (model.id to 0f) }

        try {
            val modelDir = getModelDir(model)
            modelDir.mkdirs()

            // Clean up partial downloads
            model.files.forEach { file ->
                val localFile = File(modelDir, file.name)
                if (localFile.exists() && localFile.length() != file.expectedSize) {
                    Log.d(TAG, "Removing incomplete file: ${file.name}")
                    localFile.delete()
                }
            }

            val totalSize = model.totalSizeBytes
            var downloadedSize = 0L

            model.files.forEach { file ->
                val localFile = File(modelDir, file.name)
                if (!localFile.exists()) {
                    Log.d(TAG, "Downloading ${file.name} for ${model.displayName}...")

                    downloadFile(
                        url = "${model.baseUrl}/${file.name}",
                        destination = localFile,
                        expectedSize = file.expectedSize,
                        onBytesDownloaded = { bytes ->
                            downloadedSize += bytes
                            val progress = downloadedSize.toFloat() / totalSize
                            _downloadProgress.update { it + (model.id to progress) }
                            onProgress(progress)
                        }
                    )

                    Log.d(TAG, "Downloaded ${file.name}")
                } else {
                    downloadedSize += file.expectedSize
                }
            }

            _downloadProgress.update { it + (model.id to 1f) }
            onProgress(1f)

            Log.d(TAG, "Model ${model.displayName} download complete")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model ${model.displayName}: ${e.message}", e)
            Result.failure(e)
        } finally {
            _downloadingModels.update { it - model.id }
        }
    }

    private fun downloadFile(
        url: String,
        destination: File,
        expectedSize: Long,
        onBytesDownloaded: (Long) -> Unit
    ) {
        val tempFile = File(destination.parent, "${destination.name}.tmp")
        val maxRetries = 3
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                // Check if we can resume from partial download
                val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

                if (existingBytes == expectedSize) {
                    // Already downloaded completely
                    if (!tempFile.renameTo(destination)) {
                        throw Exception("Failed to rename temp file")
                    }
                    return
                }

                Log.d(TAG, "Download attempt $attempt for ${destination.name} (resuming from $existingBytes bytes)")

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 120_000 // Increased timeout
                connection.setRequestProperty("User-Agent", "Nisafone-Android")

                // Resume support
                if (existingBytes > 0) {
                    connection.setRequestProperty("Range", "bytes=$existingBytes-")
                }

                val responseCode = connection.responseCode
                val isResuming = responseCode == 206 // Partial content

                if (responseCode != 200 && responseCode != 206) {
                    throw Exception("HTTP error: $responseCode")
                }

                val startBytes = if (isResuming) existingBytes else 0L

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile, isResuming).use { output ->
                        val buffer = ByteArray(32768) // Larger buffer
                        var bytesRead: Int
                        var totalBytesRead = startBytes

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (!isResuming || totalBytesRead > existingBytes) {
                                onBytesDownloaded(bytesRead.toLong())
                            }
                        }

                        if (totalBytesRead != expectedSize) {
                            throw Exception("Download incomplete: got $totalBytesRead bytes, expected $expectedSize")
                        }
                    }
                }

                if (!tempFile.renameTo(destination)) {
                    throw Exception("Failed to rename temp file")
                }

                Log.d(TAG, "Successfully downloaded ${destination.name}")
                return // Success!

            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Download attempt $attempt failed: ${e.message}")

                if (attempt < maxRetries) {
                    // Wait before retry (exponential backoff)
                    Thread.sleep((1000 * attempt).toLong())
                }
            }
        }

        // All retries failed
        tempFile.delete()
        throw Exception("Failed to download after $maxRetries attempts: ${lastException?.message}", lastException)
    }

    /**
     * Delete a downloaded model.
     */
    fun deleteModel(model: SpeechModel): Boolean {
        val modelDir = getModelDir(model)
        return if (modelDir.exists()) {
            modelDir.deleteRecursively()
        } else {
            true
        }
    }

    /**
     * Get the currently selected model ID.
     */
    fun getSelectedModelId(): String? {
        return prefs.getString(PREF_SELECTED_MODEL, null)
    }

    /**
     * Set the selected model.
     */
    fun setSelectedModel(model: SpeechModel) {
        prefs.edit().putString(PREF_SELECTED_MODEL, model.id).apply()
    }

    /**
     * Get the currently selected model, or null if none selected or not downloaded.
     */
    fun getSelectedModel(): SpeechModel? {
        val modelId = getSelectedModelId() ?: return null
        // First try discovered models, then fall back to catalog
        val models = discoveredModels ?: SpeechModelCatalog.allModels
        val model = models.find { it.id == modelId } ?: return null
        return if (isModelDownloaded(model)) model else null
    }

    /**
     * Get total storage used by downloaded models.
     */
    fun getTotalStorageUsed(): Long {
        return modelsBaseDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
