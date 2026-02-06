package app.s4h.nisafone.app

import androidx.compose.runtime.Composable
import app.s4h.nisafone.app.navigation.AppNavigation
import app.s4h.nisafone.app.theme.NisafoneTheme

@Composable
fun App() {
    NisafoneTheme {
        AppNavigation()
    }
}
