package app.s4h.nisafone.feature.settings

import app.s4h.nisafone.core.transcription.LanguageHint
import app.s4h.nisafone.core.transcription.SpeechModel
import app.s4h.nisafone.core.transcription.SpeechModelType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * iOS implementation of SettingsViewModel.
 * iOS uses the native SFSpeechRecognizer which doesn't require model management.
 */
class IosSettingsViewModel(
    private val emailSettingsRepository: EmailSettingsRepository
) : SettingsViewModelInterface {

    private val _uiState = MutableStateFlow(SettingsUiState())
    override val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeEmailSettings()
    }

    private fun observeEmailSettings() {
        // In a real implementation, this would use coroutines to collect from the flows
        _uiState.update {
            it.copy(
                autoEmailEnabled = emailSettingsRepository.autoEmailEnabled.value,
                autoEmailAddress = emailSettingsRepository.emailAddress.value
            )
        }
    }

    override fun loadModels() {
        // iOS uses native SFSpeechRecognizer, no model management needed
        _uiState.update {
            it.copy(
                models = emptyList(),
                error = null
            )
        }
    }

    override fun downloadModel(model: SpeechModel) {
        // Not supported on iOS
    }

    override fun deleteModel(model: SpeechModel) {
        // Not supported on iOS
    }

    override fun selectModel(model: SpeechModel) {
        // Not supported on iOS
    }

    override fun setFilter(type: SpeechModelType?) {
        _uiState.update { it.copy(filterByType = type) }
    }

    override fun setLanguageHint(hint: LanguageHint) {
        // Not supported by iOS SFSpeechRecognizer
        _uiState.update { it.copy(languageHint = hint) }
    }

    override fun setTranslateToEnglish(translate: Boolean) {
        // Not supported by iOS SFSpeechRecognizer
        _uiState.update { it.copy(translateToEnglish = translate) }
    }

    override fun setAutoEmailEnabled(enabled: Boolean) {
        emailSettingsRepository.setAutoEmailEnabled(enabled)
        _uiState.update { it.copy(autoEmailEnabled = enabled) }
    }

    override fun setAutoEmailAddress(address: String) {
        emailSettingsRepository.setEmailAddress(address)
        _uiState.update { it.copy(autoEmailAddress = address) }
    }

    override fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
