package me.spica27.spicamusic.ui.player

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.spica27.spicamusic.core.preferences.LyricsStyleSettings
import me.spica27.spicamusic.feature.settings.domain.SettingsUseCases

/**
 * Holds the lyrics window options (font, size, blur, jiggle ...).
 * Shared by the lyrics window and its settings sheet through the Activity scope.
 */
@Stable
class LyricsStyleViewModel(
    private val settings: SettingsUseCases,
) : ViewModel() {
    val style: StateFlow<LyricsStyleSettings> =
        settings.lyricsStyle
            .stateIn(viewModelScope, SharingStarted.Eagerly, LyricsStyleSettings())

    /** Change one or more options: `update { it.copy(lyricsSize = 110) }`. */
    fun update(transform: (LyricsStyleSettings) -> LyricsStyleSettings) {
        viewModelScope.launch { settings.updateLyricsStyle(transform) }
    }

    /** Back to the defaults of the reference lyrics window. */
    fun reset() {
        viewModelScope.launch { settings.resetLyricsStyle() }
    }
}
