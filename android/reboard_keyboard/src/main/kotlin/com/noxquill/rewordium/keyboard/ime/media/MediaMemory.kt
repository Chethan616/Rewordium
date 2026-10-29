/*
 * Copyright (C) 2026 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.ime.media

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the media panels remember on this device: recent searches for each kind of search, and recently
 * sent emoticons. Nothing is recorded in incognito mode, matching the rest of the keyboard.
 */
object MediaMemory {
    private const val PREFS = "media_panel_memory"
    private const val MAX_SEARCHES = 8
    private const val MAX_EMOTICONS = 24
    private const val SEPARATOR = "\u001F"

    private val _version = MutableStateFlow(0)

    /** Bumps whenever something is remembered or forgotten, so open panels can re-read. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun recentSearches(context: Context, kind: String): List<String> = read(context, "search_$kind")

    fun recordSearch(context: Context, kind: String, query: String, incognito: Boolean) {
        val q = query.trim()
        if (incognito || q.length < 2) return
        push(context, "search_$kind", q, MAX_SEARCHES, ignoreCase = true)
    }

    fun forgetSearch(context: Context, kind: String, query: String) {
        write(context, "search_$kind", read(context, "search_$kind").filterNot { it.equals(query, ignoreCase = true) })
    }

    fun recentEmoticons(context: Context): List<String> = read(context, "emoticons")

    fun recordEmoticon(context: Context, emoticon: String, incognito: Boolean) {
        if (incognito) return
        push(context, "emoticons", emoticon, MAX_EMOTICONS, ignoreCase = false)
    }

    private fun push(context: Context, key: String, value: String, max: Int, ignoreCase: Boolean) {
        val next = listOf(value) + read(context, key).filterNot { it.equals(value, ignoreCase) }
        write(context, key, next.take(max))
    }

    private fun read(context: Context, key: String): List<String> =
        prefs(context).getString(key, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

    private fun write(context: Context, key: String, values: List<String>) {
        prefs(context).edit { putString(key, values.joinToString(SEPARATOR)) }
        _version.value++
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
