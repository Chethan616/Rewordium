package com.noxquill.rewordium.keyboard.util

import android.view.View
import java.util.ArrayDeque

/**
 * Simple view recycling pool to reduce GC pressure
 * Inspired by FlorisBoard's view management
 */
class ViewPool<T : View>(
    private val maxPoolSize: Int = 20,
    private val factory: () -> T
) {
    private val pool = ArrayDeque<T>(maxPoolSize)
    
    /**
     * Acquire a view from the pool or create a new one
     */
    fun acquire(): T {
        return pool.pollFirst() ?: factory()
    }
    
    /**
     * Return a view to the pool for reuse
     */
    fun release(view: T) {
        if (pool.size < maxPoolSize) {
            // Reset view state before adding to pool
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            view.translationX = 0f
            view.translationY = 0f
            view.rotation = 0f
            pool.offerLast(view)
        }
    }
    
    /**
     * Clear the entire pool
     */
    fun clear() {
        pool.clear()
    }
    
    /**
     * Get current pool size
     */
    fun size(): Int = pool.size
    
    /**
     * Object pool for reusable objects (like MotionEvent copies)
     * Nested class to allow ViewPool.ObjectPool syntax
     */
    class ObjectPool<T>(
        private val maxPoolSize: Int = 50,
        private val factory: () -> T,
        private val reset: (T) -> Unit = {}
    ) {
        private val pool = ArrayDeque<T>(maxPoolSize)
        
        fun acquire(): T {
            return pool.pollFirst() ?: factory()
        }
        
        fun release(obj: T) {
            if (pool.size < maxPoolSize) {
                reset(obj)
                pool.offerLast(obj)
            }
        }
        
        fun clear() {
            pool.clear()
        }
    }
}
