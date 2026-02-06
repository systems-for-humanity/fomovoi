package app.s4h.nisafone.app

import androidx.compose.ui.window.ComposeUIViewController
import app.s4h.nisafone.app.di.commonModule
import app.s4h.nisafone.app.di.iosModule
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController { App() }
}

private fun initKoin() {
    startKoin {
        modules(commonModule, iosModule)
    }
}
