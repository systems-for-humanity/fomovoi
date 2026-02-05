package app.s4h.fomovoi.app.di

import app.s4h.fomovoi.core.audio.AudioRecorder
import app.s4h.fomovoi.core.audio.createAudioRecorder
import app.s4h.fomovoi.core.data.local.DatabaseDriverFactory
import app.s4h.fomovoi.core.sharing.ShareService
import app.s4h.fomovoi.core.sharing.createShareService
import app.s4h.fomovoi.core.transcription.TranscriptionService
import app.s4h.fomovoi.core.transcription.createTranscriptionService
import app.s4h.fomovoi.feature.recording.IosTitlePrefixRepository
import app.s4h.fomovoi.feature.recording.TitlePrefixRepository
import app.s4h.fomovoi.feature.settings.EmailSettingsRepository
import app.s4h.fomovoi.feature.settings.IosEmailSettingsRepository
import app.s4h.fomovoi.feature.settings.IosSettingsViewModel
import app.s4h.fomovoi.feature.settings.SettingsViewModelInterface
import org.koin.dsl.module

val iosModule = module {
    // Platform-specific implementations
    single<AudioRecorder> { createAudioRecorder() }
    single<TranscriptionService> { createTranscriptionService() }
    single<ShareService> { createShareService() }

    // Title prefix repository
    single<TitlePrefixRepository> { IosTitlePrefixRepository() }

    // Email settings repository
    single<EmailSettingsRepository> { IosEmailSettingsRepository() }

    // Settings ViewModel
    single<SettingsViewModelInterface> { IosSettingsViewModel(get()) }

    // Database driver
    single { DatabaseDriverFactory() }
}
