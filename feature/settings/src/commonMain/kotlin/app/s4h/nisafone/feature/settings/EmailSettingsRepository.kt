package app.s4h.nisafone.feature.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing auto-email settings.
 */
interface EmailSettingsRepository {
    val autoEmailEnabled: StateFlow<Boolean>
    val emailAddress: StateFlow<String>

    fun setAutoEmailEnabled(enabled: Boolean)
    fun setEmailAddress(address: String)
}
