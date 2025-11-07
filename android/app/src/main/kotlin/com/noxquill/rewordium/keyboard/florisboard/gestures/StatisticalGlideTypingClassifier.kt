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
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Simplified statistical glide typing classifier for Rewordium AI Keyboard.
 * This is a placeholder that will be expanded with the full algorithm later.
 * 
 * Based on FlorisBoard's implementation which classifies gestures by comparing 
 * them with an "ideal gesture".
 */
class StatisticalGlideTypingClassifier(private val context: Context) : GlideTypingClassifier {
    
    private val gesturePoints = mutableListOf<GlideTypingGesture.Detector.Position>()
    private var keyPositions: Map<String, GlideTypingClassifier.KeyPosition> = emptyMap()
    private val visitedKeys = mutableListOf<String>()
    
    companion object {
        private const val TAG = "StatisticalGlideTyping"
        private const val MIN_GESTURE_DISTANCE = 50f // Minimum distance for a valid gesture
    }
    
    override fun addGesturePoint(position: GlideTypingGesture.Detector.Position) {
        gesturePoints.add(position)
        
        // Find nearest key to this position
        val nearestKey = findNearestKey(position.x, position.y)
        if (nearestKey != null && (visitedKeys.isEmpty() || visitedKeys.last() != nearestKey)) {
            visitedKeys.add(nearestKey)
            Log.d(TAG, "Visited key: $nearestKey")
        }
    }
    
    override fun setLayout(keyPositions: Map<String, GlideTypingClassifier.KeyPosition>) {
        this.keyPositions = keyPositions
        Log.d(TAG, "Layout set with ${keyPositions.size} keys")
    }
    
    override fun initGestureFromPointerData(pointerData: GlideTypingGesture.Detector.PointerData) {
        clear()
        pointerData.positions.forEach { position ->
            addGesturePoint(position)
        }
    }
    
    override fun getSuggestions(maxSuggestionCount: Int, gestureCompleted: Boolean): List<String> {
        if (visitedKeys.isEmpty()) {
            return emptyList()
        }
        
        // Simple suggestion based on visited keys
        val word = visitedKeys.joinToString("")
        Log.d(TAG, "Generated suggestion: $word from keys: $visitedKeys")
        
        // TODO: Implement proper statistical matching with dictionary
        // For now, just return the concatenated keys as a single suggestion
        return if (word.isNotEmpty()) {
            listOf(word)
        } else {
            emptyList()
        }
    }
    
    override fun clear() {
        gesturePoints.clear()
        visitedKeys.clear()
    }
    
    /**
     * Find the nearest key to the given coordinates.
     */
    private fun findNearestKey(x: Float, y: Float): String? {
        var nearestKey: String? = null
        var minDistance = Float.MAX_VALUE
        
        for ((key, pos) in keyPositions) {
            val dx = x - pos.centerX
            val dy = y - pos.centerY
            val distance = sqrt(dx * dx + dy * dy)
            
            // Check if point is within key bounds (with some tolerance)
            val withinX = abs(dx) <= pos.width / 2 + 20
            val withinY = abs(dy) <= pos.height / 2 + 20
            
            if (withinX && withinY && distance < minDistance) {
                minDistance = distance
                nearestKey = key
            }
        }
        
        return nearestKey
    }
}
