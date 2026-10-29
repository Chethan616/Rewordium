package com.noxquill.rewordium.data

import android.content.Context
import com.noxquill.rewordium.keyboard.app.ReboardSettingsBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Whether the keyboard engine is running in the app's process, and the
 * keyboard themes it offers — kept for the whole run, so the Keys page draws
 * its content straight away on every visit instead of waiting on the engine
 * again each time.
 */
object KeyboardEngine {
    private val lock = Mutex()
    private val _ready = MutableStateFlow<Boolean?>(null)

    /** `null` while starting, then whether the engine is usable on this device. */
    val ready: StateFlow<Boolean?> = _ready.asStateFlow()

    private val _themes = MutableStateFlow<List<ReboardSettingsBridge.ThemeOption>>(emptyList())
    val themes: StateFlow<List<ReboardSettingsBridge.ThemeOption>> = _themes.asStateFlow()

    /** Starts the engine once (a failed start is retried next time). */
    suspend fun ensure(context: Context): Boolean = lock.withLock {
        if (_ready.value == true) return true
        ReboardSettingsBridge.awaitReady(context.applicationContext).also { _ready.value = it }
    }

    /** Re-reads the installed keyboard themes; the last list stays on screen meanwhile. */
    suspend fun refreshThemes(context: Context) {
        if (!ensure(context)) return
        runCatching { ReboardSettingsBridge.themes(context.applicationContext) }.onSuccess { _themes.value = it }
    }
}
