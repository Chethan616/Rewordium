/**
 * Gesture Model Classes - High-Performance Data Structures
 * 
 * Optimized for zero-allocation gesture processing with
 * memory pool management and efficient serialization.
 */

package com.noxquill.rewordium.keyboard.gesture.model

import android.graphics.PointF
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * SwipePath - Represents a complete gesture path with optimized storage
 */
data class SwipePath(
    val id: Long,
    val startTime: Long = System.currentTimeMillis(),
    var startPoint: PointF? = null,
    var endPoint: PointF? = null
) {
    private val _points = mutableListOf<GesturePoint>()
    val points: List<PointF> get() = _points.map { PointF(it.x, it.y) }
    
    var endTime: Long = 0
    
    var isCompleted: Boolean = false
        private set
    
    // Enhanced gesture type support for professional features
    enum class GestureType {
        UNKNOWN,
        GLIDE_TYPING,     // Multi-key swipe for word formation
        SPACEBAR_CURSOR,  // Spacebar swipe for cursor movement
        LEGACY_GESTURE    // Backward compatibility
    }
    
    // Gesture classification
    var gestureType: GestureType = GestureType.UNKNOWN
    var isSpacebarGesture: Boolean = false // Legacy support
    
    // Glide typing properties
    var visitedKeys: MutableList<String> = mutableListOf()
    var currentWord: StringBuilder = StringBuilder()
    var predictedWords: MutableList<String> = mutableListOf()
    
    // Spacebar cursor control properties  
    var initialCursorPosition: Int = 0
    var currentCursorPosition: Int = 0
    var characterMovementCount: Int = 0
    var lastHapticPosition: Int = 0
    
    // Performance metrics
    var totalDistance: Float = 0f
        private set
    var averagePressure: Float = 0f
        private set
    var peakVelocity: Float = 0f
        private set
    
    private var lastPoint: GesturePoint? = null
    private var pressureSum: Float = 0f
    
    /**
     * Add point to gesture path with automatic metrics calculation
     */
    fun addPoint(x: Float, y: Float, pressure: Float, timestamp: Long) {
        val newPoint = GesturePoint(x, y, pressure, timestamp)
        
        // Calculate distance increment
        lastPoint?.let { last ->
            val distance = calculateDistance(last.x, last.y, x, y)
            totalDistance += distance
            
            // Calculate velocity
            val timeDelta = (timestamp - last.timestamp).coerceAtLeast(1)
            val velocity = distance / (timeDelta / 1000f) // pixels per second
            if (velocity > peakVelocity) {
                peakVelocity = velocity
            }
        }
        
        _points.add(newPoint)
        lastPoint = newPoint
        
        // Update pressure average
        pressureSum += pressure
        averagePressure = pressureSum / _points.size
    }
    
    /**
     * Add point using PointF (convenience method for professional features)
     */
    fun addPoint(point: PointF) {
        addPoint(point.x, point.y, 1.0f, System.nanoTime() / 1_000_000)
    }
    
    /**
     * Professional glide typing: Add visited key
     */
    fun addVisitedKey(key: String) {
        if (visitedKeys.isEmpty() || visitedKeys.last() != key) {
            visitedKeys.add(key)
            currentWord.append(key)
        }
    }
    
    /**
     * Professional spacebar: Update cursor movement with character-level precision
     */
    fun updateCursorMovement(newPosition: Int, shouldTriggerHaptic: Boolean): Boolean {
        val oldPosition = currentCursorPosition
        currentCursorPosition = newPosition
        
        if (oldPosition != newPosition) {
            characterMovementCount = kotlin.math.abs(newPosition - initialCursorPosition)
            
            // Trigger haptic feedback for each character movement
            if (shouldTriggerHaptic && kotlin.math.abs(newPosition - lastHapticPosition) >= 1) {
                lastHapticPosition = newPosition
                return true // Indicates haptic should be triggered
            }
        }
        return false
    }
    
    /**
     * Mark gesture as completed
     */
    fun complete() {
        endTime = System.currentTimeMillis()
        isCompleted = true
    }
    
    /**
     * Get gesture duration in milliseconds
     */
    fun getDuration(): Long = if (isCompleted) endTime - startTime else System.currentTimeMillis() - startTime
    
    /**
     * Get simplified path for performance (every nth point)
     */
    fun getSimplifiedPath(step: Int = 3): List<PointF> {
        return _points.filterIndexed { index, _ -> index % step == 0 }.map { PointF(it.x, it.y) }
    }
    
    /**
     * Calculate distance between two points
     */
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Internal gesture point with timestamp and pressure
     */
    private data class GesturePoint(
        val x: Float,
        val y: Float,
        val pressure: Float,
        val timestamp: Long
    )
}

/**
 * GestureEvent - Represents a single gesture input event
 */
data class GestureEvent(
    val id: Long,
    val type: EventType,
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class EventType {
        START,
        MOVE,
        END,
        CANCEL
    }
}

/**
 * GestureResult - Final result of gesture processing
 */
data class GestureResult(
    val type: Type,
    val text: String,
    val confidence: Float,
    val specialAction: SpecialGesture? = null,
    val alternatives: List<String> = emptyList(),
    val keySequence: List<String> = emptyList(),
    val gestureDuration: Long = 0,
    val gestureDistance: Float = 0f,
    val metadata: Map<String, Any> = emptyMap()
) {
    enum class Type {
        TEXT_INPUT,
        GLIDE_WORD,          // Professional glide typing result
        CURSOR_MOVEMENT,     // Professional spacebar cursor control
        SPACEBAR_TAP,        // Single spacebar tap
        DELETE_GESTURE,
        SPECIAL_ACTION
    }
    
    val isHighConfidence: Boolean get() = confidence >= 0.8f
    val isValid: Boolean get() = text.isNotEmpty() || specialAction != null
}

/**
 * WordPrediction - Single word prediction with confidence
 */
data class WordPrediction(
    val word: String,
    val confidence: Float,
    val source: PredictionSource = PredictionSource.DICTIONARY,
    val frequency: Int = 0
) {
    enum class PredictionSource {
        DICTIONARY,
        USER_HISTORY,
        CONTEXT,
        ML_MODEL
    }
}

/**
 * SpecialGesture - Represents non-text gestures with companion object for constants
 */
data class SpecialGesture(
    val type: SpecialGestureType,
    val data: Any? = null,
    val confidence: Float = 1.0f
) {
    enum class SpecialGestureType {
        CURSOR_MOVEMENT,
        DELETE_WORD,
        QUICK_SYMBOL,
        LANGUAGE_SWITCH,
        CAPS_LOCK,
        VOICE_INPUT,
        SPACE_BAR_SLIDE,
        BACKSPACE_SLIDE
    }
    
    companion object {
        // Professional gesture constants for new features
        val GLIDE_TYPING = SpecialGesture(SpecialGestureType.QUICK_SYMBOL, "glide_typing")
        val SPACEBAR_CURSOR = SpecialGesture(SpecialGestureType.CURSOR_MOVEMENT, "spacebar_cursor")
        val SPACE = SpecialGesture(SpecialGestureType.SPACE_BAR_SLIDE, " ")
    }
}

/**
 * KeyBounds - Represents the touchable area of a keyboard key
 */
data class KeyBounds(
    val key: String,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val weight: Float = 1.0f // For prediction weighting
) {
    val left: Float get() = centerX - width / 2
    val right: Float get() = centerX + width / 2
    val top: Float get() = centerY - height / 2
    val bottom: Float get() = centerY + height / 2
    val radius: Float get() = kotlin.math.min(width, height) / 2
    
    /**
     * Calculate distance from point to key center
     */
    fun distanceFromCenter(x: Float, y: Float): Float {
        val dx = x - centerX
        val dy = y - centerY
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Check if point is within key bounds
     */
    fun contains(x: Float, y: Float): Boolean {
        return x >= left && x <= right && y >= top && y <= bottom
    }
    
    /**
     * Get weighted distance (closer = lower value)
     */
    fun getWeightedDistance(x: Float, y: Float): Float {
        val distance = distanceFromCenter(x, y)
        return distance / weight
    }
}

/**
 * GestureConfiguration - Configuration for gesture recognition
 */
data class GestureConfiguration(
    val minSwipeDistance: Float = 40f,
    val maxSwipeVelocity: Float = 3000f,
    val gestureTimeoutMs: Long = 2000L,
    val pathSmoothingFactor: Float = 0.4f,
    val predictionConfidenceThreshold: Float = 0.8f,
    val maxAlternatives: Int = 3,
    val enableSpecialGestures: Boolean = true,
    val enableRealTimePreview: Boolean = true,
    val debugMode: Boolean = false
) {
    companion object {
        fun gboardLevel(): GestureConfiguration = GestureConfiguration(
            minSwipeDistance = 30f,
            maxSwipeVelocity = 4000f,
            pathSmoothingFactor = 0.5f,
            predictionConfidenceThreshold = 0.75f,
            enableRealTimePreview = true
        )
        
        fun performanceOptimized(): GestureConfiguration = GestureConfiguration(
            minSwipeDistance = 50f,
            maxSwipeVelocity = 2500f,
            pathSmoothingFactor = 0.3f,
            predictionConfidenceThreshold = 0.85f,
            enableRealTimePreview = false
        )
    }
}

/**
 * Object pool for PointF to reduce allocations
 */
object PointFPool {
    private val pool = ConcurrentLinkedQueue<PointF>()
    private val maxPoolSize = 1000
    
    fun obtain(): PointF {
        return pool.poll() ?: PointF()
    }
    
    fun release(point: PointF) {
        if (pool.size < maxPoolSize) {
            point.set(0f, 0f)
            pool.offer(point)
        }
    }
    
    fun releaseAll(points: List<PointF>) {
        points.forEach { release(it) }
    }
}
