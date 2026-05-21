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

package com.noxquill.rewordium.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.os.UserManagerCompat
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceModel
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.ime.ai.AIManager
import com.noxquill.rewordium.keyboard.ime.ai.SmartReplyEngine
import com.noxquill.rewordium.keyboard.ime.clipboard.ClipboardManager
import com.noxquill.rewordium.keyboard.ime.core.SubtypeManager
import com.noxquill.rewordium.keyboard.ime.dictionary.DictionaryManager
import com.noxquill.rewordium.keyboard.ime.editor.EditorInstance
import com.noxquill.rewordium.keyboard.ime.keyboard.KeyboardManager
import com.noxquill.rewordium.keyboard.ime.media.emoji.FlorisEmojiCompat
import com.noxquill.rewordium.keyboard.ime.nlp.NlpManager
import com.noxquill.rewordium.keyboard.ime.text.gestures.GlideTypingManager
import com.noxquill.rewordium.keyboard.ime.theme.ThemeManager
import com.noxquill.rewordium.keyboard.lib.cache.CacheManager
import com.noxquill.rewordium.keyboard.lib.crashutility.CrashUtility
import com.noxquill.rewordium.keyboard.lib.devtools.Flog
import com.noxquill.rewordium.keyboard.lib.devtools.LogTopic
import com.noxquill.rewordium.keyboard.lib.devtools.flogError
import com.noxquill.rewordium.keyboard.lib.ext.ExtensionManager
import dev.patrickgold.jetpref.datastore.runtime.initAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.libnative.dummyAdd

/**
 * Singleton holder for ReBoard keyboard functionality. This class is designed to work as a library
 * component within a host application (like Flutter), rather than being the Application class itself.
 * 
 * It extends ContextWrapper so it can be used wherever a Context is expected.
 * 
 * Call [FlorisApplication.init] early in your application lifecycle (e.g., in MainActivity.onCreate)
 * to initialize the keyboard components.
 */
class FlorisApplication private constructor(context: Context) : ContextWrapper(context.applicationContext) {
    
    companion object {
        @Volatile
        private var instance: FlorisApplication? = null

        init {
            try {
                System.loadLibrary("fl_native")
            } catch (_: Exception) {
            }
        }

        /**
         * Initialize the FlorisApplication singleton. Call this from your Application or MainActivity.
         * If already initialized, this is a no-op.
         */
        @JvmStatic
        fun init(context: Context): FlorisApplication {
            return instance ?: synchronized(this) {
                instance ?: FlorisApplication(context.applicationContext).also {
                    instance = it
                    it.onCreate()
                }
            }
        }

        /**
         * Get the FlorisApplication instance, initializing it if necessary.
         */
        @JvmStatic
        fun getInstance(context: Context): FlorisApplication {
            return instance ?: init(context)
        }

        /**
         * Get the FlorisApplication instance, or null if not initialized.
         */
        @JvmStatic
        fun getInstanceOrNull(): FlorisApplication? = instance
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val scope = CoroutineScope(Dispatchers.Default)
    val preferenceStoreLoaded = MutableStateFlow(false)

    val cacheManager = lazy { CacheManager(this) }
    val clipboardManager = lazy { ClipboardManager(this) }
    val editorInstance = lazy { EditorInstance(this) }
    val extensionManager = lazy { ExtensionManager(this) }
    val glideTypingManager = lazy { GlideTypingManager(this) }
    val keyboardManager = lazy { KeyboardManager(this) }
    val nlpManager = lazy { NlpManager(this) }
    val subtypeManager = lazy { SubtypeManager(this) }
    val themeManager = lazy { ThemeManager(this) }
    val aiManager = lazy { AIManager(this) }
    val smartReplyEngine = lazy { SmartReplyEngine(this) }
    val ghostTextManager = lazy { com.noxquill.rewordium.keyboard.ime.editor.GhostTextManager(this) }

    private fun onCreate() {
        try {
            Flog.install(
                context = this,
                isFloggingEnabled = BuildConfig.DEBUG,
                flogTopics = LogTopic.ALL,
                flogLevels = Flog.LEVEL_ALL,
                flogOutputs = Flog.OUTPUT_CONSOLE,
            )
            CrashUtility.install(this)
            FlorisEmojiCompat.init(this)
            flogError { "dummy result: ${dummyAdd(3, 4)}" }

            if (!UserManagerCompat.isUserUnlocked(this)) {
                cacheDir?.deleteContentsRecursively()
                extensionManager.value.init()
                registerReceiver(BootComplete(), IntentFilter(Intent.ACTION_USER_UNLOCKED))
                return
            }

            initInternal()
        } catch (e: Exception) {
            CrashUtility.stageException(e)
            return
        }
    }

    internal fun initInternal() {
        cacheDir?.deleteContentsRecursively()
        scope.launch {
            val result = FlorisPreferenceStore.initAndroid(
                context = this@FlorisApplication,
                datastoreName = FlorisPreferenceModel.NAME,
            )
            Log.i("PREFS", result.toString())
            preferenceStoreLoaded.value = true
        }
        extensionManager.value.init()
        clipboardManager.value.initializeForContext(this)
        DictionaryManager.init(this)
    }

    private inner class BootComplete : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                try {
                    unregisterReceiver(this)
                } catch (e: Exception) {
                    flogError { e.toString() }
                }
                mainHandler.post { initInternal() }
            }
        }
    }
}

/**
 * Get the FlorisApplication singleton instance from any Context.
 * Will initialize the singleton if not already initialized.
 */
private fun Context.florisApplication(): FlorisApplication {
    return FlorisApplication.getInstance(this)
}

fun Context.appContext() = lazyOf(this.florisApplication())

fun Context.cacheManager() = this.florisApplication().cacheManager

fun Context.clipboardManager() = this.florisApplication().clipboardManager

fun Context.editorInstance() = this.florisApplication().editorInstance

fun Context.extensionManager() = this.florisApplication().extensionManager

fun Context.glideTypingManager() = this.florisApplication().glideTypingManager

fun Context.keyboardManager() = this.florisApplication().keyboardManager

fun Context.nlpManager() = this.florisApplication().nlpManager

fun Context.subtypeManager() = this.florisApplication().subtypeManager

fun Context.themeManager() = this.florisApplication().themeManager

fun Context.aiManager() = this.florisApplication().aiManager
fun Context.smartReplyEngine() = this.florisApplication().smartReplyEngine
fun Context.ghostTextManager() = this.florisApplication().ghostTextManager
