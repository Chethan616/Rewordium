/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package com.noxquill.rewordium.keyboard.florisboard.gestures

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles the [GlideTypingClassifier]. Basically responsible for linking [GlideTypingGesture.Detector]
 * with [GlideTypingClassifier].
 * Adapted from FlorisBoard for Rewordium AI Keyboard.
 */
class GlideTypingManager(context: Context) : GlideTypingGesture.Listener {
    companion object {
        private const val TAG = "GlideTypingManager"
        private const val MAX_SUGGESTION_COUNT = 8
        private const val PREVIEW_REFRESH_DELAY_MS = 16 // ~60fps
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var glideTypingClassifier = StatisticalGlideTypingClassifier(context)
    private var lastTime = System.currentTimeMillis()
    private var onSuggestionsListener: ((List<String>) -> Unit)? = null
    private var onCompleteListener: ((String?) -> Unit)? = null
    
    // Settings
    var showPreview = true
    var previewRefreshDelay = PREVIEW_REFRESH_DELAY_MS.toLong()

    override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
        Log.d(TAG, "Glide gesture complete with ${data.positions.size} points")
        updateSuggestionsAsync(MAX_SUGGESTION_COUNT, true) {
            glideTypingClassifier.clear()
        }
    }

    override fun onGlideCancelled() {
        Log.d(TAG, "Glide gesture cancelled")
        glideTypingClassifier.clear()
        onSuggestionsListener?.invoke(emptyList())
    }

    override fun onGlideAddPoint(point: GlideTypingGesture.Detector.Position) {
        glideTypingClassifier.addGesturePoint(point)

        val time = System.currentTimeMillis()
        if (showPreview && time - lastTime > previewRefreshDelay) {
            updateSuggestionsAsync(1, false) {}
            lastTime = time
        }
    }

    /**
     * Change the layout of the internal gesture classifier
     */
    fun setLayout(keyPositions: Map<String, GlideTypingClassifier.KeyPosition>) {
        if (keyPositions.isNotEmpty()) {
            glideTypingClassifier.setLayout(keyPositions)
            Log.d(TAG, "Layout updated with ${keyPositions.size} keys")
        }
    }
    
    /**
     * Set a listener to receive suggestions as they're generated
     */
    fun setOnSuggestionsListener(listener: (List<String>) -> Unit) {
        onSuggestionsListener = listener
    }
    
    /**
     * Set a listener to receive the final committed word
     */
    fun setOnCompleteListener(listener: (String?) -> Unit) {
        onCompleteListener = listener
    }

    /**
     * Asks gesture classifier for suggestions and passes them to listeners.
     * Also commits the most confident suggestion if [commit] is set.
     *
     * @param callback Called when this function completes.
     */
    private fun updateSuggestionsAsync(maxSuggestionsToShow: Int, commit: Boolean, callback: (Boolean) -> Unit) {
        scope.launch(Dispatchers.Default) {
            val suggestions = glideTypingClassifier.getSuggestions(MAX_SUGGESTION_COUNT, commit)

            withContext(Dispatchers.Main) {
                if (suggestions.isNotEmpty()) {
                    // Take up to maxSuggestionsToShow suggestions
                    val displaySuggestions = if (commit) {
                        suggestions.subList(1.coerceAtMost(suggestions.size), maxSuggestionsToShow.coerceAtMost(suggestions.size))
                    } else {
                        suggestions.subList(0, maxSuggestionsToShow.coerceAtMost(suggestions.size))
                    }
                    
                    onSuggestionsListener?.invoke(displaySuggestions)
                    
                    if (commit && suggestions.isNotEmpty()) {
                        val topSuggestion = suggestions.first()
                        Log.d(TAG, "Committing top suggestion: $topSuggestion")
                        onCompleteListener?.invoke(topSuggestion)
                    }
                    callback.invoke(true)
                } else {
                    onSuggestionsListener?.invoke(emptyList())
                    if (commit) {
                        onCompleteListener?.invoke(null)
                    }
                    callback.invoke(false)
                }
            }
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        glideTypingClassifier.clear()
    }
}
