package app.s4h.fomovoi.feature.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

class IosTitlePrefixRepository : TitlePrefixRepository {

    companion object {
        private const val KEY_PREFIXES = "title_prefixes"
        private const val KEY_SELECTED_PREFIX = "selected_prefix"
        private val DEFAULT_PREFIXES = listOf("Recording", "Meeting", "Interview", "Note")
    }

    private val defaults = NSUserDefaults.standardUserDefaults

    private val _prefixes = MutableStateFlow(DEFAULT_PREFIXES)
    override val prefixes: StateFlow<List<String>> = _prefixes.asStateFlow()

    private val _selectedPrefix = MutableStateFlow(DEFAULT_PREFIXES.first())
    override val selectedPrefix: StateFlow<String> = _selectedPrefix.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val savedPrefixes = defaults.stringArrayForKey(KEY_PREFIXES)
        if (savedPrefixes != null && savedPrefixes.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            _prefixes.value = (savedPrefixes as List<String>)
        }
        val savedSelected = defaults.stringForKey(KEY_SELECTED_PREFIX)
        if (!savedSelected.isNullOrBlank()) {
            _selectedPrefix.value = savedSelected
        }
    }

    override fun addPrefix(prefix: String) {
        if (prefix.isNotBlank() && prefix !in _prefixes.value) {
            val newPrefixes = _prefixes.value + prefix
            _prefixes.value = newPrefixes
            defaults.setObject(newPrefixes, KEY_PREFIXES)
            selectPrefix(prefix)
        }
    }

    override fun removePrefix(prefix: String) {
        if (prefix in _prefixes.value && _prefixes.value.size > 1) {
            val newPrefixes = _prefixes.value - prefix
            _prefixes.value = newPrefixes
            defaults.setObject(newPrefixes, KEY_PREFIXES)
            if (_selectedPrefix.value == prefix) {
                selectPrefix(newPrefixes.first())
            }
        }
    }

    override fun selectPrefix(prefix: String) {
        if (prefix in _prefixes.value) {
            _selectedPrefix.value = prefix
            defaults.setObject(prefix, KEY_SELECTED_PREFIX)
        }
    }
}
