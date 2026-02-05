package com.fomovoi.core.transcription

import com.fomovoi.core.audio.AudioChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TranscriptionService {
    val state: StateFlow<TranscriptionState>
    val events: Flow<TranscriptionEvent>
    val currentSpeaker: StateFlow<Speaker?>

    /**
     * Whether this transcription service handles audio input internally.
     * If true, the caller should NOT start a separate AudioRecorder as it
     * would conflict with the transcription service's internal audio capture.
     */
    val handlesAudioInternally: Boolean
        get() = false

    suspend fun initialize()
    suspend fun startTranscription()
    suspend fun processAudioChunk(chunk: AudioChunk)
    suspend fun stopTranscription(): TranscriptionResult?
    suspend fun setSpeakerLabel(speakerId: String, label: String)
    fun release()
}

expect fun createTranscriptionService(): TranscriptionService
