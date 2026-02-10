package app.s4h.nisafone.feature.recording

import androidx.compose.runtime.Composable

@Composable
actual fun KeepScreenOn() {
    // No-op on Android — foreground service keeps the process alive
}
