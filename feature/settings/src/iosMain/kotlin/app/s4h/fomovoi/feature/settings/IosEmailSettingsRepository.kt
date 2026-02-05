package app.s4h.fomovoi.feature.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

class IosEmailSettingsRepository : EmailSettingsRepository {

    companion object {
        private const val KEY_AUTO_EMAIL_ENABLED = "auto_email_enabled"
        private const val KEY_EMAIL_ADDRESS = "email_address"
    }

    private val defaults = NSUserDefaults.standardUserDefaults

    private val _autoEmailEnabled = MutableStateFlow(false)
    override val autoEmailEnabled: StateFlow<Boolean> = _autoEmailEnabled.asStateFlow()

    private val _emailAddress = MutableStateFlow("")
    override val emailAddress: StateFlow<String> = _emailAddress.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _autoEmailEnabled.value = defaults.boolForKey(KEY_AUTO_EMAIL_ENABLED)
        _emailAddress.value = defaults.stringForKey(KEY_EMAIL_ADDRESS) ?: ""
    }

    override fun setAutoEmailEnabled(enabled: Boolean) {
        _autoEmailEnabled.value = enabled
        defaults.setBool(enabled, KEY_AUTO_EMAIL_ENABLED)
    }

    override fun setEmailAddress(address: String) {
        _emailAddress.value = address
        defaults.setObject(address, KEY_EMAIL_ADDRESS)
    }
}
