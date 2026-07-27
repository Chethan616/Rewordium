package com.noxquill.rewordium.keyboard.app

import android.content.Context
import com.noxquill.rewordium.keyboard.ime.input.HapticVibrationMode
import com.noxquill.rewordium.keyboard.ime.keyboard.SpaceBarMode
import com.noxquill.rewordium.keyboard.ime.smartbar.CandidatesDisplayMode

object QuickSettingsHelper {
    private val Context.florisPrefs by FlorisPreferenceStore

    fun getQuickSettings(context: Context): Map<String, Any> {
        val prefs = context.florisPrefs
        return mapOf(
            // Appearance
            "themeMode" to prefs.theme.mode.get().name,
            "appearanceColor" to (if (prefs.theme.accentColor.get() == androidx.compose.ui.graphics.Color.Unspecified) "default" else prefs.theme.accentColor.get().value.toULong().toString(16)),
            // Haptics
            "hapticVibrationMode" to prefs.inputFeedback.hapticVibrationMode.get().name,
            "audioEnabled" to prefs.inputFeedback.audioEnabled.get(),
            "hapticEnabled" to prefs.inputFeedback.hapticEnabled.get(),
            // Glide
            "glideEnabled" to prefs.glide.enabled.get(),
            "glideTrailWidth" to prefs.glide.trailWidth.get().toDouble(),
            // Keyboard
            "autoCapitalization" to prefs.correction.autoCapitalization.get(),
            "doubleSpacePeriod" to prefs.correction.doubleSpacePeriod.get(),
            "numberRow" to prefs.keyboard.numberRow.get(),
            // Smartbar
            "smartbarEnabled" to prefs.smartbar.enabled.get(),
            "experimentalRoundedSmartbar" to prefs.keyboard.experimentalRoundedSmartbar.get(),
            "experimentalRoundedSmartbarRadius" to prefs.keyboard.experimentalRoundedSmartbarRadius.get(),
            // Suggestion display mode (Candidates)
            "suggestionDisplayMode" to prefs.suggestion.displayMode.get().name,
            // Height
            "heightFactorPortrait" to prefs.keyboard.heightFactorPortrait.get(),
            "heightFactorLandscape" to prefs.keyboard.heightFactorLandscape.get(),
            // Spacebar
            "spaceBarMode" to prefs.keyboard.spaceBarMode.get().name,
            "spaceBarCustomLabel" to prefs.keyboard.spaceBarCustomLabel.get(),
            // Clipboard
            "clipboardHistoryEnabled" to prefs.clipboard.historyEnabled.get(),
            "clipboardSuggestionEnabled" to prefs.clipboard.suggestionEnabled.get(),
            "clipboardSuggestionTimeout" to prefs.clipboard.suggestionTimeout.get(),
            // Contacts / spelling
            "spellingUseContacts" to prefs.spelling.useContacts.get(),
        )
    }

    suspend fun updateQuickSetting(context: Context, key: String, value: Any) {
        val prefs = context.florisPrefs
        when (key) {
            "themeMode" -> prefs.theme.mode.set(com.noxquill.rewordium.keyboard.ime.theme.ThemeMode.valueOf(value as String))
            "appearanceColor" -> {
                val hexStr = value as String
                try {
                    if (hexStr == "default") {
                        prefs.theme.accentColor.set(androidx.compose.ui.graphics.Color.Unspecified)
                    } else {
                        prefs.theme.accentColor.set(androidx.compose.ui.graphics.Color(java.lang.Long.parseUnsignedLong(hexStr, 16)))
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors for now
                }
            }
            "hapticVibrationMode" -> prefs.inputFeedback.hapticVibrationMode.set(HapticVibrationMode.valueOf(value as String))
            "audioEnabled" -> prefs.inputFeedback.audioEnabled.set(value as Boolean)
            "hapticEnabled" -> {
                val enabled = value as Boolean
                prefs.inputFeedback.hapticEnabled.set(enabled)
                if (enabled) {
                    prefs.inputFeedback.hapticActivationMode.set(com.noxquill.rewordium.keyboard.ime.input.InputFeedbackActivationMode.IGNORE_SYSTEM_SETTINGS)
                }
            }
            "glideEnabled" -> prefs.glide.enabled.set(value as Boolean)
            "glideTrailWidth" -> prefs.glide.trailWidth.set((value as Double).toFloat())
            "autoCapitalization" -> prefs.correction.autoCapitalization.set(value as Boolean)
            "doubleSpacePeriod" -> prefs.correction.doubleSpacePeriod.set(value as Boolean)
            "numberRow" -> prefs.keyboard.numberRow.set(value as Boolean)
            "smartbarEnabled" -> prefs.smartbar.enabled.set(value as Boolean)
            "experimentalRoundedSmartbar" -> prefs.keyboard.experimentalRoundedSmartbar.set(value as Boolean)
            "experimentalRoundedSmartbarRadius" -> prefs.keyboard.experimentalRoundedSmartbarRadius.set(value as Int)
            "suggestionDisplayMode" -> prefs.suggestion.displayMode.set(CandidatesDisplayMode.valueOf(value as String))
            "heightFactorPortrait" -> prefs.keyboard.heightFactorPortrait.set(value as Int)
            "heightFactorLandscape" -> prefs.keyboard.heightFactorLandscape.set(value as Int)
            "spaceBarMode" -> prefs.keyboard.spaceBarMode.set(SpaceBarMode.valueOf(value as String))
            "spaceBarCustomLabel" -> prefs.keyboard.spaceBarCustomLabel.set(value as String)
            "clipboardHistoryEnabled" -> prefs.clipboard.historyEnabled.set(value as Boolean)
            "clipboardSuggestionEnabled" -> prefs.clipboard.suggestionEnabled.set(value as Boolean)
            "clipboardSuggestionTimeout" -> prefs.clipboard.suggestionTimeout.set(value as Int)
            "spellingUseContacts" -> prefs.spelling.useContacts.set(value as Boolean)
        }
    }
}
