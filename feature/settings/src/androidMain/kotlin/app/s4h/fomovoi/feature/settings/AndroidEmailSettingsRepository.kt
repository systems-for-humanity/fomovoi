package app.s4h.fomovoi.feature.settings

import android.content.Context
import android.os.StrictMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidEmailSettingsRepository(context: Context) : EmailSettingsRepository {

    companion object {
        private const val PREFS_NAME = "email_settings"
        private const val PREF_AUTO_EMAIL_ENABLED = "auto_email_enabled"
        private const val PREF_EMAIL_ADDRESS = "email_address"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _autoEmailEnabled = MutableStateFlow(false)
    override val autoEmailEnabled: StateFlow<Boolean> = _autoEmailEnabled.asStateFlow()

    private val _emailAddress = MutableStateFlow("")
    override val emailAddress: StateFlow<String> = _emailAddress.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val oldPolicy = StrictMode.allowThreadDiskReads()
        try {
            _autoEmailEnabled.value = prefs.getBoolean(PREF_AUTO_EMAIL_ENABLED, false)
            _emailAddress.value = prefs.getString(PREF_EMAIL_ADDRESS, "") ?: ""
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }

    override fun setAutoEmailEnabled(enabled: Boolean) {
        _autoEmailEnabled.value = enabled
        prefs.edit().putBoolean(PREF_AUTO_EMAIL_ENABLED, enabled).apply()
    }

    override fun setEmailAddress(address: String) {
        _emailAddress.value = address
        prefs.edit().putString(PREF_EMAIL_ADDRESS, address).apply()
    }
}
