package app.s4h.nisafone.feature.recording

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun ManageRecordingService(isRecording: Boolean) {
    val context = LocalContext.current

    DisposableEffect(isRecording) {
        if (isRecording) {
            val serviceClass = Class.forName("app.s4h.nisafone.android.RecordingService")
            val intent = Intent(context, serviceClass)
            context.startForegroundService(intent)
        } else {
            try {
                val serviceClass = Class.forName("app.s4h.nisafone.android.RecordingService")
                val intent = Intent(context, serviceClass)
                context.stopService(intent)
            } catch (_: ClassNotFoundException) {
                // Service class not available
            }
        }
        onDispose {
            try {
                val serviceClass = Class.forName("app.s4h.nisafone.android.RecordingService")
                val intent = Intent(context, serviceClass)
                context.stopService(intent)
            } catch (_: ClassNotFoundException) {
                // Service class not available
            }
        }
    }
}
