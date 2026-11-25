package tv.own.owntv.ui.theme

import kotlinx.coroutines.flow.first
import tv.own.owntv.BuildConfig
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom

internal object MovioAppearanceDefaults {
    val fontSize: Int get() = if (BuildConfig.MOVIOXP_RELEASE) 95 else UiFontScale.DEFAULT
    val screenZoom: Int get() = if (BuildConfig.MOVIOXP_RELEASE) 85 else UiZoom.DEFAULT

    suspend fun applyMissing(settings: SettingsRepository) {
        val saved = settings.exportSettings()
        if (!saved.has("ui_zoom_percent")) settings.setUiZoomPercent(screenZoom)
        if (!saved.has("font_size_percent")) {
            val font = settings.fontCustomization.first()
            settings.setFontCustomization(font.copy(sizePercent = fontSize))
        }
    }
}
