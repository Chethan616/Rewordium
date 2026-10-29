/*
 * Copyright (C) 2021-2025 The ReBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.noxquill.rewordium.keyboard.ime.input

import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.staticCompositionLocalOf
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.ime.keyboard.KeyData
import com.noxquill.rewordium.keyboard.ime.text.key.KeyCode
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.systemServiceOrNull
import org.florisboard.lib.android.systemVibratorOrNull
import org.florisboard.lib.android.vibrate
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

val LocalInputFeedbackController = staticCompositionLocalOf<InputFeedbackController> { error("not init") }

/**
 * Input feedback controller is responsible to process and perform audio and haptic
 * feedback for user interactions based on the system and floris preferences.
 */
class InputFeedbackController private constructor(private val ims: InputMethodService) {
    companion object {
        private const val MIN_STEP_TICK_GAP_MS = 14L

        fun new(ims: InputMethodService) = InputFeedbackController(ims)
    }

    private val prefs by FlorisPreferenceStore

    private val audioManager = ims.systemServiceOrNull(AudioManager::class)
    private val vibrator = ims.systemVibratorOrNull()
    private val contentResolver = ims.contentResolver
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var systemAudioEnabled: Boolean = false
    private var systemHapticEnabled: Boolean = false

    fun updateSystemPrefsState() {
        systemAudioEnabled = systemPref(Settings.System.SOUND_EFFECTS_ENABLED)
        systemHapticEnabled = systemPref(Settings.System.HAPTIC_FEEDBACK_ENABLED)
    }

    fun keyPress(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatKeyPress.get()) performAudioFeedback(data, 1.0)
        if (prefs.inputFeedback.hapticFeatKeyPress.get()) performHapticFeedback(data, 1.0)
    }

    fun keyLongPress(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatKeyLongPress.get()) performAudioFeedback(data, 0.7)
        if (prefs.inputFeedback.hapticFeatKeyLongPress.get()) performHapticFeedback(data, 0.4)
    }

    fun keyRepeatedAction(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatKeyRepeatedAction.get()) performAudioFeedback(data, 0.4)
        if (prefs.inputFeedback.hapticFeatKeyRepeatedAction.get()) performHapticFeedback(data, 0.05)
    }

    fun gestureSwipe(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatGestureSwipe.get()) performAudioFeedback(data, 0.7)
        if (prefs.inputFeedback.hapticFeatGestureSwipe.get()) performHapticFeedback(data, 0.4)
    }

    fun gestureMovingSwipe(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatGestureMovingSwipe.get()) performAudioFeedback(data, 0.4)
        if (prefs.inputFeedback.hapticFeatGestureMovingSwipe.get()) performHapticFeedback(data, 0.05)
    }

    /**
     * One step of a sliding gesture (the space bar moving the cursor, the delete key selecting): a crisp
     * segment tick, the same feel as a Material 3 slider stepping in the app's settings.
     */
    fun gestureStep(data: KeyData = TextKeyData.UNSPECIFIED) {
        if (prefs.inputFeedback.audioFeatGestureMovingSwipe.get()) performAudioFeedback(data, 0.4)
        if (prefs.inputFeedback.hapticFeatGestureMovingSwipe.get()) performTick(Tick.STEP)
    }

    /** A sliding gesture took hold: the cursor starts following the finger. */
    fun gestureEngaged() {
        if (prefs.inputFeedback.hapticFeatGestureMovingSwipe.get()) performTick(Tick.ENGAGE)
    }

    /** A light tick for tabs, chips and segmented choices in the keyboard's panels. */
    fun uiTick() {
        if (prefs.inputFeedback.hapticFeatKeyPress.get()) performTick(Tick.UI)
    }

    /** A long press opened something in a panel (emoji variants, a sticker's actions). */
    fun uiReveal() {
        if (prefs.inputFeedback.hapticFeatKeyPress.get()) performTick(Tick.ENGAGE)
    }

    private enum class Tick { STEP, ENGAGE, UI }

    @Volatile
    private var lastStepTickAt = 0L

    /**
     * Ticks use the system's tuned effects when touch feedback is on (exactly what the settings sliders
     * play), and the motor's own tick primitives otherwise, scaled by the vibration-strength setting.
     */
    private fun performTick(kind: Tick) {
        val vibrator = vibrator ?: return
        if (!prefs.inputFeedback.hapticEnabled.get()) return
        if (prefs.inputFeedback.hapticActivationMode.get() ==
            InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS && !systemHapticEnabled) return
        if (kind == Tick.STEP) {
            // A fast slide can step many times a frame; the motor can't render ticks closer than this.
            val now = SystemClock.uptimeMillis()
            if (now - lastStepTickAt < MIN_STEP_TICK_GAP_MS) return
            lastStepTickAt = now
        }
        scope.launch {
            val view = ims.window?.window?.decorView
            if (view != null && systemHapticEnabled && AndroidVersion.ATLEAST_API34_U) {
                view.performHapticFeedback(
                    if (kind == Tick.ENGAGE) {
                        HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                    } else {
                        HapticFeedbackConstants.SEGMENT_TICK
                    },
                )
                return@launch
            }
            val primitive = if (kind == Tick.ENGAGE) {
                VibrationEffect.Composition.PRIMITIVE_CLICK
            } else {
                VibrationEffect.Composition.PRIMITIVE_TICK
            }
            val strength = (prefs.inputFeedback.hapticVibrationStrength.get() / 100f).coerceIn(0.3f, 1f)
            val scale = if (kind == Tick.ENGAGE) strength else strength * 0.85f
            when {
                AndroidVersion.ATLEAST_API30_R && vibrator.areAllPrimitivesSupported(primitive) -> {
                    vibrator.vibrate(VibrationEffect.startComposition().addPrimitive(primitive, scale).compose())
                }
                AndroidVersion.ATLEAST_API29_Q -> {
                    vibrator.vibrate(
                        VibrationEffect.createPredefined(
                            if (kind == Tick.ENGAGE) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK,
                        ),
                    )
                }
                else -> vibrator.vibrate(duration = 8, strength = prefs.inputFeedback.hapticVibrationStrength.get())
            }
        }
    }

    private fun systemPref(id: String): Boolean {
        if (contentResolver == null) return false
        return Settings.System.getInt(contentResolver, id, 0) != 0
    }

    private fun performAudioFeedback(data: KeyData, factor: Double) {
        if (audioManager == null) return
        if (!prefs.inputFeedback.audioEnabled.get()) return
        if (prefs.inputFeedback.audioActivationMode.get() ==
            InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS && !systemAudioEnabled) return

        scope.launch {
            val volume = (prefs.inputFeedback.audioVolume.get() * factor) / 100.0
            val effect = when (data.code) {
                KeyCode.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyCode.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                KeyCode.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            if (volume in 0.01..1.00) {
                flogDebug { "Perform audio with volume=$volume and effect=$effect" }
                audioManager.playSoundEffect(effect, volume.toFloat())
            }
        }
    }

    private fun performHapticFeedback(data: KeyData, factor: Double) {
        if (vibrator == null) return
        if (!prefs.inputFeedback.hapticEnabled.get()) return
        if (prefs.inputFeedback.hapticActivationMode.get() ==
            InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS && !systemHapticEnabled) return

        scope.launch {
            if (prefs.inputFeedback.hapticVibrationMode.get() == HapticVibrationMode.USE_HAPTIC_FEEDBACK_INTERFACE) {
                val view = ims.window?.window?.decorView ?: return@launch
                val hfc = if (factor < 1.0 && AndroidVersion.ATLEAST_API27_O_MR1) {
                    HapticFeedbackConstants.TEXT_HANDLE_MOVE
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                }
                view.performHapticFeedback(hfc,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                return@launch
            }

            vibrator.vibrate(
                duration = prefs.inputFeedback.hapticVibrationDuration.get(),
                strength = prefs.inputFeedback.hapticVibrationStrength.get(),
                factor = factor,
            )
        }
    }
}
