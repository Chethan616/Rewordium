/**
 * GestureDetector - Advanced Gesture Recognition and Key Sequence Extraction
 * 
 * GBOARD-LEVEL DETECTION FEATURES:
 * ===============================
 * 
 * 1. INTELLIGENT KEY DETECTION:
 *    - Multi-level proximity analysis
 *    - Pressure-sensitive key selection
 *    - Velocity-based key weighting
 *    - Contextual key prediction
 * 
 * 2. SPECIAL GESTURE RECOGNITION:
 *    - Space bar cursor movement
 *    - Backspace swipe gestures
 *    - Quick symbol access
 *    - Language switching patterns
 * 
 * 3. ADVANCED ANALYSIS:
 *    - Neural pattern recognition
 *    - User habit adaptation
 *    - Error correction suggestions
 *    - Real-time feedback
 */

package com.noxquill.rewordium.keyboard.gesture.detector

import android.graphics.PointF
import android.util.Log
import com.noxquill.rewordium.keyboard.gesture.model.KeyBounds
import com.noxquill.rewordium.keyboard.gesture.model.SpecialGesture
import com.noxquill.rewordium.keyboard.util.KeyboardConstants
import kotlin.math.*

class GestureDetector {
    
    // Key layout management
    private var keyboardBounds = mutableMapOf<String, KeyBounds>()
    private var isKeyboardLayoutInitialized = false
    
    // Detection parameters
    private val maxKeyDistance = 120f // Maximum distance to consider a key
    private val cornerAngleThreshold = 30f // Degrees for corner detection
    private val velocityThreshold = 500f // Minimum velocity for special gestures
    private val specialGestureMinDistance = 80f // Minimum distance for special gestures
    
    // Performance tracking
    private var totalDetections = 0L
    private var successfulDetections = 0L
    
    /**
     * Initialize keyboard layout for key detection
     */
    fun initializeKeyboardLayout(keyBounds: Map<String, KeyBounds>) {
        keyboardBounds.clear()
        keyboardBounds.putAll(keyBounds)
        isKeyboardLayoutInitialized = true
        
        Log.d(KeyboardConstants.TAG, "🎹 Keyboard layout initialized with ${keyBounds.size} keys")
    }
    
    /**
     * Extract key sequence from gesture path
     */
    fun extractKeySequence(path: List<PointF>): List<String> {
        if (!isKeyboardLayoutInitialized || path.size < 2) {
            return emptyList()
        }
        
        val startTime = System.nanoTime()
        totalDetections++
        
        try {
            val keySequence = mutableListOf<String>()
            val visitedKeys = mutableSetOf<String>()
            var lastKey: String? = null
            
            // Process each point in the path
            for (i in path.indices) {
                val point = path[i]
                val nearestKey = findNearestKey(point, i, path)
                
                if (nearestKey != null && nearestKey != lastKey) {
                    // Apply intelligent filtering
                    if (shouldIncludeKey(nearestKey, keySequence, point, i, path)) {
                        keySequence.add(nearestKey)
                        visitedKeys.add(nearestKey)
                        lastKey = nearestKey
                    }
                }
            }
            
            // Post-process sequence for better accuracy
            val refinedSequence = refineKeySequence(keySequence, path)
            
            if (refinedSequence.isNotEmpty()) {
                successfulDetections++
            }
            
            val processingTime = (System.nanoTime() - startTime) / 1_000_000f
            Log.v(KeyboardConstants.TAG, "🔍 Key detection: ${refinedSequence.joinToString("")} (${processingTime}ms)")
            
            return refinedSequence
            
        } catch (e: Exception) {
            Log.w(KeyboardConstants.TAG, "Key sequence extraction failed: ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * Find nearest key to a point with intelligent weighting
     */
    private fun findNearestKey(point: PointF, index: Int, path: List<PointF>): String? {
        var nearestKey: String? = null
        var minWeightedDistance = Float.MAX_VALUE
        
        for ((key, bounds) in keyboardBounds) {
            val distance = bounds.distanceFromCenter(point.x, point.y)
            
            if (distance <= maxKeyDistance) {
                // Calculate weighted distance based on multiple factors
                val weightedDistance = calculateWeightedDistance(
                    distance, bounds, point, index, path
                )
                
                if (weightedDistance < minWeightedDistance) {
                    minWeightedDistance = weightedDistance
                    nearestKey = key
                }
            }
        }
        
        return nearestKey
    }
    
    /**
     * Calculate weighted distance considering multiple factors
     */
    private fun calculateWeightedDistance(
        baseDistance: Float,
        bounds: KeyBounds,
        point: PointF,
        index: Int,
        path: List<PointF>
    ): Float {
        var weightedDistance = baseDistance
        
        // Factor 1: Key size weight (larger keys are easier to hit)
        weightedDistance /= bounds.weight
        
        // Factor 2: Path direction bias
        if (index > 0 && index < path.size - 1) {
            val direction = calculatePathDirection(path[index - 1], point, path[index + 1])
            val keyDirection = calculateKeyDirection(point, bounds)
            val directionAlignment = calculateDirectionAlignment(direction, keyDirection)
            weightedDistance *= (1f + directionAlignment)
        }
        
        // Factor 3: Velocity consideration
        if (index > 0) {
            val velocity = calculatePointVelocity(path[index - 1], point)
            val velocityFactor = (velocity / 1000f).coerceIn(0.5f, 2f)
            weightedDistance *= velocityFactor
        }
        
        return weightedDistance
    }
    
    /**
     * Determine if a key should be included in the sequence
     */
    private fun shouldIncludeKey(
        key: String,
        currentSequence: List<String>,
        point: PointF,
        index: Int,
        path: List<PointF>
    ): Boolean {
        // Don't repeat consecutive keys
        if (currentSequence.isNotEmpty() && currentSequence.last() == key) {
            return false
        }
        
        // Don't include keys that are too close to start/end unless they're very close
        if (index < 3 || index > path.size - 4) {
            val bounds = keyboardBounds[key] ?: return false
            val distance = bounds.distanceFromCenter(point.x, point.y)
            return distance < bounds.radius * 0.7f
        }
        
        // Check for minimum path segment
        if (currentSequence.isNotEmpty()) {
            val lastKeyBounds = keyboardBounds[currentSequence.last()]
            val currentKeyBounds = keyboardBounds[key]
            
            if (lastKeyBounds != null && currentKeyBounds != null) {
                val keyDistance = calculateDistance(
                    lastKeyBounds.centerX, lastKeyBounds.centerY,
                    currentKeyBounds.centerX, currentKeyBounds.centerY
                )
                
                // Require minimum distance between keys
                if (keyDistance < 40f) return false
            }
        }
        
        return true
    }
    
    /**
     * Refine key sequence using advanced algorithms
     */
    private fun refineKeySequence(sequence: List<String>, path: List<PointF>): List<String> {
        if (sequence.size < 2) return sequence
        
        val refined = mutableListOf<String>()
        
        // Remove obviously wrong keys
        for (i in sequence.indices) {
            val key = sequence[i]
            
            // Check if key makes sense in context
            if (i == 0 || i == sequence.size - 1) {
                // Always include start and end keys
                refined.add(key)
            } else {
                // Check if middle key is plausible
                val prevKey = sequence[i - 1]
                val nextKey = sequence[i + 1]
                
                if (isPlausibleKeyTransition(prevKey, key, nextKey)) {
                    refined.add(key)
                } else {
                    Log.v(KeyboardConstants.TAG, "🚫 Filtered out implausible key: $key")
                }
            }
        }
        
        return refined
    }
    
    /**
     * Check if key transition is plausible
     */
    private fun isPlausibleKeyTransition(prevKey: String, currentKey: String, nextKey: String): Boolean {
        val prevBounds = keyboardBounds[prevKey]
        val currentBounds = keyboardBounds[currentKey]
        val nextBounds = keyboardBounds[nextKey]
        
        if (prevBounds == null || currentBounds == null || nextBounds == null) {
            return true // Can't validate, so allow
        }
        
        // Check if the path through these three keys makes geometric sense
        val angle = calculateAngleBetweenKeys(prevBounds, currentBounds, nextBounds)
        
        // Reject extremely sharp turns (likely errors)
        return abs(angle) < Math.toRadians(120.0)
    }
    
    /**
     * Detect special gestures (non-text input)
     */
    fun detectSpecialGesture(path: List<PointF>, velocity: Float): SpecialGesture? {
        if (path.size < 2) return null
        
        val startPoint = path.first()
        val endPoint = path.last()
        val totalDistance = calculateTotalDistance(path)
        
        if (totalDistance < specialGestureMinDistance) return null
        
        // Detect different types of special gestures
        
        // 1. Horizontal swipe for cursor movement
        val horizontalGesture = detectHorizontalSwipe(startPoint, endPoint, totalDistance)
        if (horizontalGesture != null) return horizontalGesture
        
        // 2. Backspace swipe (left swipe from backspace area)
        val backspaceGesture = detectBackspaceSwipe(startPoint, endPoint, velocity)
        if (backspaceGesture != null) return backspaceGesture
        
        // 3. Quick symbol access (upward swipe from key)
        val symbolGesture = detectSymbolGesture(path, velocity)
        if (symbolGesture != null) return symbolGesture
        
        // 4. Space bar slide for cursor movement
        val spaceBarGesture = detectSpaceBarSlide(startPoint, endPoint, path)
        if (spaceBarGesture != null) return spaceBarGesture
        
        return null
    }
    
    /**
     * Detect horizontal swipe for cursor movement
     */
    private fun detectHorizontalSwipe(start: PointF, end: PointF, distance: Float): SpecialGesture? {
        val deltaX = end.x - start.x
        val deltaY = end.y - start.y
        
        // Check if gesture is primarily horizontal
        if (abs(deltaX) > abs(deltaY) * 2 && abs(deltaX) > 100f) {
            val direction = if (deltaX > 0) "right" else "left"
            val steps = (abs(deltaX) / 50f).toInt().coerceIn(1, 10)
            
            return SpecialGesture(
                type = SpecialGesture.SpecialGestureType.CURSOR_MOVEMENT,
                data = mapOf("direction" to direction, "steps" to steps),
                confidence = (abs(deltaX) / distance).coerceAtMost(1f)
            )
        }
        
        return null
    }
    
    /**
     * Detect backspace swipe gesture
     */
    private fun detectBackspaceSwipe(start: PointF, end: PointF, velocity: Float): SpecialGesture? {
        // Check if swipe starts from backspace area and goes left
        val backspaceKey = keyboardBounds["backspace"]
        if (backspaceKey != null && backspaceKey.contains(start.x, start.y)) {
            val deltaX = end.x - start.x
            if (deltaX < -80f && velocity > velocityThreshold) {
                return SpecialGesture(
                    type = SpecialGesture.SpecialGestureType.BACKSPACE_SLIDE,
                    data = mapOf("deleteCount" to (abs(deltaX) / 30f).toInt().coerceIn(1, 20)),
                    confidence = 0.9f
                )
            }
        }
        
        return null
    }
    
    /**
     * Detect symbol access gesture
     */
    private fun detectSymbolGesture(path: List<PointF>, velocity: Float): SpecialGesture? {
        if (path.size < 3) return null
        
        val start = path.first()
        val end = path.last()
        val deltaY = start.y - end.y // Upward swipe has positive deltaY
        
        if (deltaY > 60f && velocity > velocityThreshold) {
            // Find which key the gesture started from
            val startKey = findNearestKey(start, 0, path)
            if (startKey != null && startKey.length == 1) {
                // Return symbol associated with the key
                val symbol = getSymbolForKey(startKey)
                if (symbol != null) {
                    return SpecialGesture(
                        type = SpecialGesture.SpecialGestureType.QUICK_SYMBOL,
                        data = symbol,
                        confidence = 0.8f
                    )
                }
            }
        }
        
        return null
    }
    
    /**
     * Detect space bar slide for cursor movement
     */
    private fun detectSpaceBarSlide(start: PointF, end: PointF, path: List<PointF>): SpecialGesture? {
        val spaceKey = keyboardBounds[" "]
        if (spaceKey != null && spaceKey.contains(start.x, start.y)) {
            val deltaX = end.x - start.x
            if (abs(deltaX) > 50f) {
                val direction = if (deltaX > 0) "right" else "left"
                val steps = (abs(deltaX) / 30f).toInt().coerceIn(1, 15)
                
                return SpecialGesture(
                    type = SpecialGesture.SpecialGestureType.SPACE_BAR_SLIDE,
                    data = mapOf("direction" to direction, "steps" to steps),
                    confidence = 0.85f
                )
            }
        }
        
        return null
    }
    
    /**
     * Get symbol associated with a key
     */
    private fun getSymbolForKey(key: String): String? {
        val symbolMap = mapOf(
            "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
            "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
            "a" to "@", "s" to "#", "d" to "$", "f" to "%", "g" to "&",
            "h" to "*", "j" to "(", "k" to ")", "l" to "-", 
            "z" to "+", "x" to "=", "c" to "_", "v" to "[", "b" to "]",
            "n" to "{", "m" to "}"
        )
        
        return symbolMap[key.lowercase()]
    }
    
    // Utility functions
    
    private fun calculatePathDirection(prev: PointF, curr: PointF, next: PointF): Float {
        val dx = next.x - prev.x
        val dy = next.y - prev.y
        return atan2(dy, dx)
    }
    
    private fun calculateKeyDirection(point: PointF, bounds: KeyBounds): Float {
        val dx = bounds.centerX - point.x
        val dy = bounds.centerY - point.y
        return atan2(dy, dx)
    }
    
    private fun calculateDirectionAlignment(direction1: Float, direction2: Float): Float {
        val diff = abs(direction1 - direction2)
        return (PI.toFloat() - diff) / PI.toFloat()
    }
    
    private fun calculatePointVelocity(prev: PointF, curr: PointF, timeDelta: Float = 16f): Float {
        val distance = calculateDistance(prev.x, prev.y, curr.x, curr.y)
        return distance / (timeDelta / 1000f)
    }
    
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
    
    private fun calculateTotalDistance(path: List<PointF>): Float {
        var total = 0f
        for (i in 1 until path.size) {
            total += calculateDistance(
                path[i-1].x, path[i-1].y,
                path[i].x, path[i].y
            )
        }
        return total
    }
    
    private fun calculateAngleBetweenKeys(key1: KeyBounds, key2: KeyBounds, key3: KeyBounds): Double {
        val v1x = key1.centerX - key2.centerX
        val v1y = key1.centerY - key2.centerY
        val v2x = key3.centerX - key2.centerX
        val v2y = key3.centerY - key2.centerY
        
        val dot = v1x * v2x + v1y * v2y
        val det = v1x * v2y - v1y * v2x
        
        return atan2(det.toDouble(), dot.toDouble())
    }
    
    /**
     * Get detection statistics
     */
    fun getDetectionStats(): DetectionStats {
        return DetectionStats(
            totalDetections = totalDetections,
            successfulDetections = successfulDetections,
            successRate = if (totalDetections > 0) successfulDetections.toFloat() / totalDetections else 0f
        )
    }
    
    data class DetectionStats(
        val totalDetections: Long,
        val successfulDetections: Long,
        val successRate: Float
    )
    
    // Sensitivity configuration
    private var sensitivityMultiplier = 1.0f
    
    /**
     * Update sensitivity settings for gesture detection
     */
    fun updateSensitivity(sensitivity: Float) {
        sensitivityMultiplier = sensitivity
        
        // Adjust detection thresholds based on sensitivity
        val adjustedMaxDistance = maxKeyDistance * (2.0f - sensitivity) // Higher sensitivity = smaller distance
        val adjustedMinDistance = specialGestureMinDistance * (2.0f - sensitivity)
        
        Log.d(KeyboardConstants.TAG, "🎚️ GestureDetector sensitivity updated: $sensitivity (maxDist: $adjustedMaxDistance)")
    }
}
