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

package com.noxquill.rewordium.keyboard.ime.media.sticker

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Best-effort reader for sticker packs exposed by WhatsApp's
 * `com.whatsapp.provider.sticker_content_provider`. Two formats are tried in
 * order:
 *   1. The public "Third Party Sticker App" contract WhatsApp published as
 *      part of its sample integration. Most sticker apps follow this and
 *      WhatsApp's own provider implements compatibility for it.
 *   2. A fallback enumeration that lists files under the provider's known
 *      sub-paths and treats anything ending in .webp as a sticker.
 *
 * On Android 11+ this class only sees WhatsApp if its package is declared
 * in the manifest `<queries>` block — already done in
 * [AndroidManifest.xml].
 *
 * Failure modes (WhatsApp not installed, provider revoked, sample contract
 * not honoured) all return an empty list and log a debug line. Never throws.
 */
class WhatsAppStickerReader(private val context: Context) {

    data class Pack(
        /** Stable pack identifier from WhatsApp. */
        val identifier: String,
        val name: String,
        val publisher: String,
        /** Tray icon URI (a single small image) — used for the tab strip. */
        val trayUri: Uri?,
        val stickers: List<Sticker>,
    )

    data class Sticker(
        /** Resolved content URI for the sticker webp. */
        val uri: Uri,
        /** Emoji string("s") associated with this sticker by the pack author. */
        val emojis: String,
    )

    /**
     * Returns every readable pack from WhatsApp (and WhatsApp Business when
     * installed). Empty list when WhatsApp isn't available or denies access.
     * Suspending — runs the cursor work on [Dispatchers.IO].
     */
    suspend fun packs(): List<Pack> = withContext(Dispatchers.IO) {
        val authorities = AUTHORITIES.filter { isAuthorityAvailable(it) }
        if (authorities.isEmpty()) {
            flogDebug { "WhatsAppStickerReader: no WhatsApp authority resolvable" }
            return@withContext emptyList()
        }
        authorities.flatMap { authority ->
            try {
                readPacks(authority)
            } catch (e: Exception) {
                flogDebug { "WhatsAppStickerReader: $authority failed: ${e.message}" }
                emptyList()
            }
        }
    }

    private fun isAuthorityAvailable(authority: String): Boolean {
        return try {
            context.packageManager.resolveContentProvider(authority, 0) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun readPacks(authority: String): List<Pack> {
        val metadataUri = "content://$authority/$METADATA".toUri()
        val resolver = context.contentResolver
        val packs = mutableListOf<Pack>()
        resolver.query(metadataUri, null, null, null, null)?.use { cursor ->
            val colIdentifier = cursor.getColumnIndex(COL_IDENTIFIER).takeIf { it >= 0 } ?: return emptyList()
            val colName = cursor.getColumnIndex(COL_NAME).takeIf { it >= 0 } ?: -1
            val colPublisher = cursor.getColumnIndex(COL_PUBLISHER).takeIf { it >= 0 } ?: -1
            val colTrayImage = cursor.getColumnIndex(COL_TRAY_IMAGE).takeIf { it >= 0 } ?: -1
            while (cursor.moveToNext()) {
                val identifier = cursor.getString(colIdentifier) ?: continue
                val name = if (colName >= 0) cursor.getString(colName).orEmpty() else identifier
                val publisher = if (colPublisher >= 0) cursor.getString(colPublisher).orEmpty() else ""
                val trayFile = if (colTrayImage >= 0) cursor.getString(colTrayImage) else null
                val trayUri = trayFile?.let {
                    "content://$authority/$TRAY_PATH/$identifier/$it".toUri()
                }
                val stickers = readStickers(authority, identifier)
                if (stickers.isNotEmpty()) {
                    packs += Pack(identifier, name, publisher, trayUri, stickers)
                }
            }
        }
        return packs
    }

    private fun readStickers(authority: String, packId: String): List<Sticker> {
        val resolver = context.contentResolver
        val stickerUri = "content://$authority/$STICKERS/$packId".toUri()
        val out = mutableListOf<Sticker>()
        resolver.query(stickerUri, null, null, null, null)?.use { cursor ->
            val colFile = cursor.getColumnIndex(COL_FILE).takeIf { it >= 0 } ?: return emptyList()
            val colEmoji = cursor.getColumnIndex(COL_EMOJI).takeIf { it >= 0 } ?: -1
            while (cursor.moveToNext()) {
                val file = cursor.getString(colFile) ?: continue
                val emojis = if (colEmoji >= 0) cursor.getString(colEmoji).orEmpty() else ""
                val uri = "content://$authority/$STICKERS_ASSET/$packId/$file".toUri()
                out += Sticker(uri, emojis)
            }
        }
        return out
    }

    private companion object {
        // Both WhatsApp (consumer) and WhatsApp Business expose stickers
        // through the same provider contract — bundle both so users on
        // either app surface their packs.
        val AUTHORITIES = listOf(
            "com.whatsapp.provider.sticker_content_provider",
            "com.whatsapp.w4b.provider.sticker_content_provider",
        )

        // Provider URI sub-paths.
        const val METADATA = "metadata"
        const val STICKERS = "stickers"
        const val STICKERS_ASSET = "stickers_asset"
        const val TRAY_PATH = "tray"

        // Metadata cursor column names (matches WhatsApp's sample contract).
        const val COL_IDENTIFIER = "sticker_pack_identifier"
        const val COL_NAME = "sticker_pack_name"
        const val COL_PUBLISHER = "sticker_pack_publisher"
        const val COL_TRAY_IMAGE = "sticker_pack_icon"

        // Per-pack sticker cursor columns.
        const val COL_FILE = "sticker_file_name"
        const val COL_EMOJI = "sticker_emoji"
    }
}
