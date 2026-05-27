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

package com.noxquill.rewordium.keyboard.ime.nlp.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug

/**
 * Pulls display names from the system contacts provider and yields each
 * name-token (typically a first or last name) as a string. Used by
 * [LatinLanguageProvider.preload] to seed the native dict with the user's
 * personal name vocabulary so suggestions / glide can surface contact
 * names alongside common dictionary words.
 *
 * Permission is checked at the call site; a missing READ_CONTACTS grant
 * returns an empty sequence. Names are tokenized on whitespace and stripped
 * of anything outside `[A-Za-z']` — emoji, brackets, parenthetical
 * annotations, numeric suffixes etc. drop out so we don't pollute the dict
 * with garbage tokens.
 *
 * Single-pass cursor read; called once per IME process at preload. Cost is
 * dominated by IPC into ContactsProvider2; expect ~50–150ms for a few
 * thousand contacts on a midrange device.
 */
object ContactsLoader {

    private val TOKEN_PATTERN = Regex("^[A-Za-z][A-Za-z']*$")
    private const val MIN_TOKEN_LEN = 2
    private const val MAX_TOKEN_LEN = 30

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Read every contact display name, tokenize, and return a unique set of
     * name tokens (lowercased). Returns empty set when permission is missing
     * or the cursor can't be opened — callers must treat empty as "no-op",
     * not as "no contacts".
     */
    fun loadNameTokens(context: Context): Set<String> {
        if (!hasPermission(context)) return emptySet()
        val tokens = HashSet<String>(256)
        runCatching {
            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                null,
                null,
                null,
            ) ?: return@runCatching
            cursor.use { c ->
                val col = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                if (col < 0) return@use
                while (c.moveToNext()) {
                    val name = c.getString(col) ?: continue
                    for (raw in name.split(Regex("\\s+"))) {
                        val tok = raw.trim().lowercase()
                        if (tok.length < MIN_TOKEN_LEN || tok.length > MAX_TOKEN_LEN) continue
                        if (!TOKEN_PATTERN.matches(tok)) continue
                        tokens.add(tok)
                    }
                }
            }
        }.onFailure { e ->
            flogDebug { "ContactsLoader: query failed: $e" }
        }
        flogDebug { "ContactsLoader: extracted ${tokens.size} unique name tokens" }
        return tokens
    }
}
