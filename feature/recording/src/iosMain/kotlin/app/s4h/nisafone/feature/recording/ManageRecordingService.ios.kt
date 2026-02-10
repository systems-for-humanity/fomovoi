package app.s4h.nisafone.feature.recording

import androidx.compose.runtime.Composable

@Composable
actual fun ManageRecordingService(isRecording: Boolean) {
    // No-op on iOS — background audio is handled by the audio session category
}
