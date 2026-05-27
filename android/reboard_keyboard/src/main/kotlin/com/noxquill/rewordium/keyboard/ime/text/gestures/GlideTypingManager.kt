/*
 * Copyright (C) 2025 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.ime.text.gestures

import android.content.Context
import com.noxquill.rewordium.keyboard.BuildConfig
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.ime.nlp.WordSuggestionCandidate
import com.noxquill.rewordium.keyboard.ime.nlp.engine.LatinImeNative
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKey
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.nlpManager
import com.noxquill.rewordium.keyboard.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Handles the [GlideTypingClassifier]. Basically responsible for linking [GlideTypingGesture.Detector]
 * with [GlideTypingClassifier].
 */
class GlideTypingManager(context: Context) : GlideTypingGesture.Listener {
    companion object {
        private const val MAX_SUGGESTION_COUNT = 6
        // Number of mid-gesture preview candidates shown while the user is still swiping.
        // Top-3 lets the user see live where the gesture is converging.
        private const val PARTIAL_PREVIEW_COUNT = 3
    }

    private val prefs by FlorisPreferenceStore
    private val keyboardManager by context.keyboardManager()
    private val nlpManager by context.nlpManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    // Phase 5f: dispatch on ENABLE_NATIVE_GLIDE. When the flag is on AND the
    // native library loaded successfully, route gestures through AOSP's
    // Suggest pipeline via NativeGlideTypingClassifier. Otherwise keep the
    // hand-rolled Kotlin shape matcher so the keyboard still works if the
    // native build is missing or rolled back. Default-off until on-device
    // calibration confirms parity.
    private var glideTypingClassifier: GlideTypingClassifier =
        if (BuildConfig.ENABLE_NATIVE_GLIDE && LatinImeNative.ensureLoaded()) {
            NativeGlideTypingClassifier(context)
        } else {
            StatisticalGlideTypingClassifier(context)
        }
    private var lastTime = System.currentTimeMillis()

    init {
        // Adaptive learned swipe typing: listen for "vocabulary changed
        // enough to warrant a rebuild" signals from the suggestion provider.
        // The provider debounces these (~every 10 new learned words), so
        // the expensive Pruner rebuild here amortizes well.
        scope.launch {
            nlpManager.wordDataDirtyFlow.collect { subtype ->
                glideTypingClassifier.setWordData(subtype, force = true)
            }
        }
    }

    override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
        updateSuggestionsAsync(MAX_SUGGESTION_COUNT, true) {
            glideTypingClassifier.clear()
        }
    }

    override fun onGlideCancelled() {
        glideTypingClassifier.clear()
    }

    override fun onGlideAddPoint(point: GlideTypingGesture.Detector.Position) {
        // Forward the point along with its eventTime so the classifier can do velocity-aware scoring.
        this.glideTypingClassifier.addGesturePoint(point)

        val time = System.currentTimeMillis()
        if (prefs.glide.showPreview.get() && time - lastTime > prefs.glide.previewRefreshDelay.get()) {
            val n = if (BuildConfig.ENABLE_PARTIAL_GESTURE_PREDICTIONS) PARTIAL_PREVIEW_COUNT else 1
            updateSuggestionsAsync(n, false) {}
            lastTime = time
        }
    }

    /**
     * Change the layout of the internal gesture classifier
     */
    fun setLayout(keys: List<TextKey>) {
        if (keys.isNotEmpty()) {
            val subtype = subtypeManager.activeSubtype
            glideTypingClassifier.setLayout(keys, subtype)
            if (!glideTypingClassifier.ready) {
                glideTypingClassifier.setWordData(subtype)
            }
        }
    }

    /**
     * Asks gesture classifier for suggestions and then passes that on to the smartbar.
     * Also commits the most confident suggestion if [commit] is set. All happens on an async executor.
     * NB: only fetches [MAX_SUGGESTION_COUNT] suggestions.
     *
     * @param callback Called when this function completes. Takes a boolean, which indicates if suggestions
     * were successfully set.
     */
    private fun updateSuggestionsAsync(maxSuggestionsToShow: Int, commit: Boolean, callback: (Boolean) -> Unit) {
        if (!glideTypingClassifier.ready) {
            glideTypingClassifier.setWordData(subtypeManager.activeSubtype)
            if (!glideTypingClassifier.ready) {
                callback.invoke(false)
                return
            }
        }

        scope.launch(Dispatchers.Default) {
            val suggestions = glideTypingClassifier.getSuggestions(MAX_SUGGESTION_COUNT, commit)

            withContext(Dispatchers.Main) {
                val startIndex = if (commit) 1 else 0
                val endIndex = min(suggestions.size, startIndex + maxSuggestionsToShow)
                val suggestionList = buildList {
                    if (startIndex < endIndex) {
                        suggestions.subList(startIndex, endIndex)
                            .map { keyboardManager.fixCase(it.toString()) }
                            .forEach { add(WordSuggestionCandidate(it, confidence = 1.0)) }
                    }
                }

                nlpManager.suggestDirectly(suggestionList)
                if (commit && suggestions.isNotEmpty()) {
                    keyboardManager.commitGesture(suggestions.first().toString())
                }
                callback.invoke(true)
            }
        }
    }
}
