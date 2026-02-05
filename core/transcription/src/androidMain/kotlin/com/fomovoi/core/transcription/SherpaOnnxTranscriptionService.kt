package com.fomovoi.core.transcription

import android.content.Context
import android.util.Log
import co.touchlab.kermit.Logger
import com.fomovoi.core.audio.AudioChunk
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Transcription service using Sherpa-ONNX for continuous, on-device speech recognition.
 * Supports multiple languages with models downloaded on first use.
 */
fun createSherpaOnnxTranscriptionService(context: Context): TranscriptionService {
    return SherpaOnnxTranscriptionService(context)
}

class SherpaOnnxTranscriptionService(
    private val context: Context
) : TranscriptionService {

    companion object {
        private const val TAG = "SherpaOnnxTranscription"
        private const val MODELS_BASE_DIR = "sherpa-onnx-models"
        private const val PREFS_NAME = "transcription_prefs"
        private const val PREF_LANGUAGE = "selected_language"
    }

    private val logger = Logger.withTag("SherpaOnnxTranscriptionService")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private val utterances = mutableListOf<Utterance>()
    private var sessionStartTime: Long = 0
    private var lastText: String = ""

    private val _state = MutableStateFlow(TranscriptionState.IDLE)
    override val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TranscriptionEvent>(extraBufferCapacity = 64)
    override val events: Flow<TranscriptionEvent> = _events.asSharedFlow()

    private val _currentSpeaker = MutableStateFlow<Speaker?>(null)
    override val currentSpeaker: StateFlow<Speaker?> = _currentSpeaker.asStateFlow()

    private val _currentLanguage = MutableStateFlow(loadSavedLanguage())
    override val currentLanguage: StateFlow<SpeechLanguage> = _currentLanguage.asStateFlow()

    override val availableLanguages: List<SpeechLanguage> = SpeechLanguage.entries

    private val speakers = mutableMapOf<String, Speaker>()

    override val handlesAudioInternally: Boolean = false

    private fun loadSavedLanguage(): SpeechLanguage {
        val code = prefs.getString(PREF_LANGUAGE, null)
        return code?.let { SpeechLanguage.fromCode(it) } ?: SpeechLanguage.default
    }

    private fun saveLanguage(language: SpeechLanguage) {
        prefs.edit().putString(PREF_LANGUAGE, language.code).apply()
    }

    override suspend fun initialize() {
        Log.d(TAG, "initialize() called")
        logger.d { "Initializing Sherpa-ONNX transcription service" }
        _state.value = TranscriptionState.INITIALIZING

        try {
            val language = _currentLanguage.value
            initializeForLanguage(language)

            _state.value = TranscriptionState.READY
            Log.d(TAG, "Sherpa-ONNX transcription service initialized for ${language.displayName}")
            logger.d { "Transcription service initialized" }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize: ${e.message}", e)
            logger.e(e) { "Failed to initialize Sherpa-ONNX" }
            _state.value = TranscriptionState.ERROR
            _events.emit(TranscriptionEvent.Error("Failed to initialize: ${e.message}"))
        }
    }

    private suspend fun initializeForLanguage(language: SpeechLanguage) {
        val modelDir = File(context.filesDir, "$MODELS_BASE_DIR/${language.modelId}")

        if (!areModelsReady(modelDir, language)) {
            Log.d(TAG, "Models not found or incomplete for ${language.displayName}, downloading...")
            downloadModels(modelDir, language)
        }

        // Release existing recognizer if any
        recognizer?.release()
        recognizer = null

        val config = createRecognizerConfig(modelDir, language)
        recognizer = OnlineRecognizer(config = config)
        Log.d(TAG, "OnlineRecognizer created for ${language.displayName}")

        // Initialize default speaker
        val defaultSpeaker = Speaker(id = "speaker_1", label = "Speaker 1")
        speakers[defaultSpeaker.id] = defaultSpeaker
        _currentSpeaker.value = defaultSpeaker
    }

    override suspend fun setLanguage(language: SpeechLanguage) {
        if (language == _currentLanguage.value) return

        Log.d(TAG, "Switching language to ${language.displayName}")
        val wasTranscribing = _state.value == TranscriptionState.TRANSCRIBING

        if (wasTranscribing) {
            stopTranscription()
        }

        _state.value = TranscriptionState.INITIALIZING
        _currentLanguage.value = language
        saveLanguage(language)

        try {
            initializeForLanguage(language)
            _state.value = TranscriptionState.READY
            _events.emit(TranscriptionEvent.Error("Switched to ${language.displayName}"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch language: ${e.message}", e)
            _state.value = TranscriptionState.ERROR
            _events.emit(TranscriptionEvent.Error("Failed to switch language: ${e.message}"))
        }
    }

    private fun areModelsReady(modelDir: File, language: SpeechLanguage): Boolean {
        if (!modelDir.exists()) return false
        return language.modelFiles.all { modelFile ->
            val file = File(modelDir, modelFile.name)
            file.exists() && file.length() == modelFile.expectedSize
        }
    }

    private suspend fun downloadModels(modelDir: File, language: SpeechLanguage) = withContext(Dispatchers.IO) {
        modelDir.mkdirs()

        // Clean up any partial downloads
        language.modelFiles.forEach { modelFile ->
            val file = File(modelDir, modelFile.name)
            if (file.exists() && file.length() != modelFile.expectedSize) {
                Log.d(TAG, "Removing incomplete file: ${modelFile.name}")
                file.delete()
            }
        }

        val totalSize = language.totalSize
        var downloadedSize = 0L

        language.modelFiles.forEach { modelFile ->
            val file = File(modelDir, modelFile.name)
            if (!file.exists()) {
                Log.d(TAG, "Downloading ${modelFile.name}...")
                val progressPercent = (downloadedSize * 100 / totalSize).toInt()
                val sizeMB = totalSize / 1_000_000
                scope.launch {
                    _events.emit(TranscriptionEvent.Error(
                        "Downloading ${language.displayName} model: $progressPercent% (~${sizeMB}MB)"
                    ))
                }

                downloadFile(
                    url = "${language.baseUrl}/${modelFile.name}",
                    destination = file,
                    expectedSize = modelFile.expectedSize
                )

                Log.d(TAG, "Downloaded ${modelFile.name}")
            }
            downloadedSize += modelFile.expectedSize
        }

        scope.launch {
            _events.emit(TranscriptionEvent.Error("${language.displayName} model ready"))
        }
    }

    private fun downloadFile(url: String, destination: File, expectedSize: Long) {
        val tempFile = File(destination.parent, "${destination.name}.tmp")

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "Fomovoi-Android")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                    }

                    if (totalBytesRead != expectedSize) {
                        throw Exception("Download incomplete: got $totalBytesRead bytes, expected $expectedSize")
                    }
                }
            }

            if (!tempFile.renameTo(destination)) {
                throw Exception("Failed to rename temp file")
            }

        } catch (e: Exception) {
            tempFile.delete()
            throw Exception("Failed to download from $url: ${e.message}", e)
        }
    }

    private fun createRecognizerConfig(modelDir: File, language: SpeechLanguage): OnlineRecognizerConfig {
        val modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = File(modelDir, language.encoderFile.name).absolutePath,
                decoder = File(modelDir, language.decoderFile.name).absolutePath,
                joiner = File(modelDir, language.joinerFile.name).absolutePath
            ),
            tokens = File(modelDir, language.tokensFile.name).absolutePath,
            numThreads = 2,
            debug = false,
            provider = "cpu",
            modelType = "zipformer"
        )

        val endpointConfig = EndpointConfig(
            rule1 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 2.4f, minUtteranceLength = 0f),
            rule2 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = 1.2f, minUtteranceLength = 0f),
            rule3 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 0f, minUtteranceLength = 20f)
        )

        return OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = modelConfig,
            endpointConfig = endpointConfig,
            enableEndpoint = true,
            decodingMethod = "greedy_search",
            maxActivePaths = 4
        )
    }

    override suspend fun startTranscription() {
        Log.d(TAG, "startTranscription() called, current state: ${_state.value}")
        if (_state.value != TranscriptionState.READY && _state.value != TranscriptionState.IDLE) {
            Log.w(TAG, "Cannot start transcription in state: ${_state.value}")
            return
        }

        if (recognizer == null) {
            Log.e(TAG, "Recognizer not initialized")
            _events.emit(TranscriptionEvent.Error("Recognizer not initialized"))
            return
        }

        Log.d(TAG, "Starting transcription in ${_currentLanguage.value.displayName}")
        utterances.clear()
        lastText = ""
        sessionStartTime = System.currentTimeMillis()

        stream = recognizer?.createStream()
        Log.d(TAG, "Created new stream: ${stream != null}")

        _state.value = TranscriptionState.TRANSCRIBING
    }

    override suspend fun processAudioChunk(chunk: AudioChunk) {
        val currentStream = stream ?: return
        val currentRecognizer = recognizer ?: return

        if (_state.value != TranscriptionState.TRANSCRIBING) return

        val samples = FloatArray(chunk.data.size / 2)
        for (i in samples.indices) {
            val low = chunk.data[i * 2].toInt() and 0xFF
            val high = chunk.data[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            samples[i] = sample / 32768.0f
        }

        currentStream.acceptWaveform(samples, chunk.sampleRate)

        while (currentRecognizer.isReady(currentStream)) {
            currentRecognizer.decode(currentStream)
        }

        val result = currentRecognizer.getResult(currentStream)
        val text = result.text.trim()

        if (text.isNotEmpty() && text != lastText) {
            Log.d(TAG, "Partial result: $text")
            scope.launch {
                _events.emit(TranscriptionEvent.PartialResult(text))
            }
        }

        if (currentRecognizer.isEndpoint(currentStream)) {
            if (text.isNotEmpty()) {
                Log.d(TAG, "Final result: $text")
                val currentTime = System.currentTimeMillis() - sessionStartTime
                val speaker = _currentSpeaker.value ?: return

                val utterance = Utterance(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    speaker = speaker,
                    startTimeMs = currentTime - 1000,
                    endTimeMs = currentTime
                )
                utterances.add(utterance)

                scope.launch {
                    _events.emit(TranscriptionEvent.FinalResult(utterance))
                }
            }

            currentRecognizer.reset(currentStream)
            lastText = ""
        } else {
            lastText = text
        }
    }

    override suspend fun stopTranscription(): TranscriptionResult? {
        Log.d(TAG, "stopTranscription() called, utterances.size=${utterances.size}")
        logger.d { "Stopping transcription" }

        val currentStream = stream
        val currentRecognizer = recognizer
        if (currentStream != null && currentRecognizer != null && lastText.isNotEmpty()) {
            val currentTime = System.currentTimeMillis() - sessionStartTime
            val speaker = _currentSpeaker.value
            if (speaker != null) {
                val utterance = Utterance(
                    id = UUID.randomUUID().toString(),
                    text = lastText,
                    speaker = speaker,
                    startTimeMs = currentTime - 1000,
                    endTimeMs = currentTime
                )
                utterances.add(utterance)
                scope.launch {
                    _events.emit(TranscriptionEvent.FinalResult(utterance))
                }
            }
        }

        _state.value = TranscriptionState.READY
        stream = null
        lastText = ""

        Log.d(TAG, "After stopping, utterances.size=${utterances.size}")
        if (utterances.isEmpty()) {
            Log.d(TAG, "No utterances to return, returning null")
            return null
        }

        val fullText = utterances.joinToString(" ") { it.text }
        val durationMs = utterances.lastOrNull()?.endTimeMs ?: 0

        return TranscriptionResult(
            id = UUID.randomUUID().toString(),
            utterances = utterances.toList(),
            fullText = fullText,
            durationMs = durationMs,
            createdAt = Clock.System.now(),
            isComplete = true
        )
    }

    override suspend fun setSpeakerLabel(speakerId: String, label: String) {
        speakers[speakerId]?.let { speaker ->
            val updated = speaker.copy(label = label)
            speakers[speakerId] = updated
            if (_currentSpeaker.value?.id == speakerId) {
                _currentSpeaker.value = updated
            }
        }
    }

    override fun release() {
        Log.d(TAG, "Releasing Sherpa-ONNX transcription service")
        logger.d { "Releasing transcription service" }
        scope.cancel()
        stream = null
        recognizer?.release()
        recognizer = null
    }
}
