package app.s4h.nisafone.core.sharing

import app.s4h.nisafone.core.transcription.TranscriptionResult

data class ShareTarget(
    val id: String,
    val name: String,
    val packageName: String? = null
)

sealed class ShareResult {
    data object Success : ShareResult()
    data class Error(val message: String) : ShareResult()
    data object Cancelled : ShareResult()
}

interface ShareService {
    suspend fun shareText(text: String, title: String? = null): ShareResult
    suspend fun shareTranscription(result: TranscriptionResult): ShareResult
    suspend fun shareToApp(text: String, target: ShareTarget): ShareResult
    fun getAvailableTargets(): List<ShareTarget>

    /**
     * Compose an email with the given parameters.
     * Opens the device's email client with the email pre-populated.
     */
    suspend fun sendEmail(to: String, subject: String, body: String): ShareResult
}

expect fun createShareService(): ShareService
