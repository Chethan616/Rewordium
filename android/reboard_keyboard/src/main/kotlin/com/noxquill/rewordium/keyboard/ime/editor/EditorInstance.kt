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

package com.noxquill.rewordium.keyboard.ime.editor

import android.content.ClipDescription
import android.content.ContentUris
import android.content.Context
import android.view.KeyEvent
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.noxquill.rewordium.keyboard.FlorisImeService
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.appContext
import com.noxquill.rewordium.keyboard.clipboardManager
import com.noxquill.rewordium.keyboard.ghostTextManager
import com.noxquill.rewordium.keyboard.ime.ImeUiMode
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardFileStorage
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardItem
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ItemType
import com.noxquill.rewordium.keyboard.ime.input.InputShiftState
import com.noxquill.rewordium.keyboard.ime.keyboard.IncognitoMode
import com.noxquill.rewordium.keyboard.ime.keyboard.KeyboardMode
import com.noxquill.rewordium.keyboard.ime.nlp.SuggestionCandidate
import com.noxquill.rewordium.keyboard.ime.text.composing.Appender
import com.noxquill.rewordium.keyboard.ime.text.composing.Composer
import com.noxquill.rewordium.keyboard.ime.text.key.KeyVariation
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.lib.ext.ExtensionComponentName
import com.noxquill.rewordium.keyboard.nlpManager
import com.noxquill.rewordium.keyboard.subtypeManager
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.florisboard.lib.android.showShortToastSync

class EditorInstance(context: Context) : AbstractEditorInstance(context) {
    companion object {
        private const val SPACE = " "
    }

    private val prefs by FlorisPreferenceStore
    private val appContext by context.appContext()
    private val clipboardManager by context.clipboardManager()
    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()
    private val nlpManager by context.nlpManager()
    private val ghostTextManager by context.ghostTextManager()

    private val activeState get() = keyboardManager.activeState
    val autoSpace = AutoSpaceState()
    val phantomSpace = PhantomSpaceState()
    val massSelection = MassSelectionState()

    /**
     * Length (in chars) of the text inserted by the last [commitGesture] call,
     * or 0 if the most recent commit was anything else. Used by
     * [deleteBackwards] as a fallback when [EditorContent.currentWord] is
     * invalid — e.g. inside Chrome / Instagram WebView fields that don't
     * report a composing region back to us. With this we can still
     * "backspace deletes the whole gesture word" without relying on the
     * editor's word-boundary tracking. Cleared on any non-gesture mutation
     * (manual char commit, setSelection, host-driven selection update).
     */
    private var lastGestureCommitLength: Int = 0
    private var lastCommittedNovelWord: String? = null

    private fun currentInputConnection() = FlorisImeService.currentInputConnection()

    override fun handleStartInputView(editorInfo: FlorisEditorInfo, isRestart: Boolean) {
        if (!prefs.correction.rememberCapsLockState.get()) {
            activeState.inputShiftState = InputShiftState.UNSHIFTED
        }
        activeState.isActionsOverflowVisible = false
        activeState.isActionsEditorVisible = false
        super.handleStartInputView(editorInfo, isRestart)
        val keyboardMode = when (editorInfo.inputAttributes.type) {
            InputAttributes.Type.NUMBER -> {
                activeState.keyVariation = KeyVariation.NORMAL
                KeyboardMode.NUMERIC
            }
            InputAttributes.Type.PHONE -> {
                activeState.keyVariation = KeyVariation.NORMAL
                KeyboardMode.PHONE
            }
            InputAttributes.Type.TEXT -> {
                activeState.keyVariation = when (editorInfo.inputAttributes.variation) {
                    InputAttributes.Variation.EMAIL_ADDRESS,
                    InputAttributes.Variation.WEB_EMAIL_ADDRESS,
                    -> {
                        KeyVariation.EMAIL_ADDRESS
                    }
                    InputAttributes.Variation.PASSWORD,
                    InputAttributes.Variation.VISIBLE_PASSWORD,
                    InputAttributes.Variation.WEB_PASSWORD,
                    -> {
                        KeyVariation.PASSWORD
                    }
                    InputAttributes.Variation.URI -> {
                        KeyVariation.URI
                    }
                    else -> {
                        KeyVariation.NORMAL
                    }
                }
                KeyboardMode.CHARACTERS
            }
            else -> {
                activeState.keyVariation = KeyVariation.NORMAL
                KeyboardMode.CHARACTERS
            }
        }
        activeState.keyboardMode = keyboardMode
        activeState.isComposingEnabled = when (keyboardMode) {
            KeyboardMode.NUMERIC,
            KeyboardMode.PHONE,
            KeyboardMode.PHONE2,
            -> false
            else -> activeState.keyVariation != KeyVariation.PASSWORD &&
                prefs.suggestion.enabled.get()// &&
            //!instance.inputAttributes.flagTextAutoComplete &&
            //!instance.inputAttributes.flagTextNoSuggestions
        }
        activeState.isIncognitoMode = when (prefs.suggestion.incognitoMode.get()) {
            IncognitoMode.FORCE_OFF -> false
            IncognitoMode.FORCE_ON -> true
            IncognitoMode.DYNAMIC_ON_OFF -> {
                editorInfo.imeOptions.flagNoPersonalizedLearning || prefs.suggestion.forceIncognitoModeFromDynamic.get()
            }
        }
    }

    override fun handleSelectionUpdate(oldSelection: EditorRange, newSelection: EditorRange, composing: EditorRange) {
        autoSpace.setInactiveFromUpdate()
        phantomSpace.setInactiveFromUpdate()
        // If the host moved the cursor away from where we just committed
        // the gesture, the fallback word-delete would target the wrong span.
        // The phantom-space check above already handles intent; cursor-jump
        // is the other signal we need to invalidate on.
        if (oldSelection != newSelection && !phantomSpace.isGestureTriggered) {
            lastGestureCommitLength = 0
        }
        if (massSelection.isActive) {
            super.handleMassSelectionUpdate(newSelection, composing)
        } else {
            super.handleSelectionUpdate(oldSelection, newSelection, composing)
        }
    }

    override fun determineComposingEnabled(): Boolean {
        return nlpManager.isSuggestionOn()
    }

    override fun determineComposer(composerName: ExtensionComponentName): Composer {
        return keyboardManager.resources.composers.value?.get(composerName) ?: Appender
    }

    override fun shouldDetermineComposingRegion(editorInfo: FlorisEditorInfo): Boolean {
        return super.shouldDetermineComposingRegion(editorInfo) &&
            (phantomSpace.isInactive || phantomSpace.showComposingRegion)
    }

    /**
     * Sets the selection of the input editor to the specified [start] and [end] values. This method does nothing if
     * the input connection is not valid or if the input editor is raw.
     *
     * @param start The start of the selection (inclusive). May be any value ranging from -1 to positive infinity.
     * @param end The end of the selection (exclusive). May be any value ranging from -1 to positive infinity.
     *
     * @return True on success or if the selection is already at specified position, false otherwise.
     */
    fun setSelection(start: Int, end: Int): Boolean {
        // Cursor move — the just-committed gesture word is no longer the
        // tail of the editor's text, so the fallback word-delete is unsafe.
        lastGestureCommitLength = 0
        lastCommittedNovelWord = null
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val selection = EditorRange.normalized(start, end)
        return super.setSelection(selection)
    }

    private fun shouldInsertAutoSpaceBefore(text: String): Boolean {
        if (!prefs.correction.autoSpacePunctuation.get() || text.isEmpty()) return false
        if (activeInfo.isRawInputEditor) return false
        if (activeState.keyVariation != KeyVariation.NORMAL) return false

        val punctuationRule = nlpManager.getActivePunctuationRule()
        val textBefore = activeContent.getTextBeforeCursor(1)
        return textBefore.isNotEmpty() && !textBefore.last().isWhitespace() &&
            punctuationRule.symbolsFollowingAutoSpace.contains(text.first())
    }

    private fun shouldInsertAutoSpaceAfter(text: String): Boolean {
        if (!prefs.correction.autoSpacePunctuation.get() || text.isEmpty()) return false
        if (activeInfo.isRawInputEditor) return false
        if (activeState.keyVariation != KeyVariation.NORMAL) return false

        val punctuationRule = nlpManager.getActivePunctuationRule()
        val content = activeContent
        val textBefore = content.getTextBeforeCursor(3).let { textBefore ->
            if (autoSpace.isActive && textBefore.isNotEmpty() && textBefore.last() == ' ') {
                textBefore.dropLast(1)
            } else {
                textBefore
            }
        }
        return textBefore.isNotEmpty() && !textBefore.last().isWhitespace() &&
            content.currentWordText.all { !it.isDigit() } &&
            punctuationRule.symbolsPrecedingAutoSpace.contains(text.first())
    }

    /**
     * Transforms straight quotes into typographic (curly/smart) quotes
     * based on the preceding text context.
     */
    private fun transformSmartQuote(char: String): String {
        if (char != "\"" && char != "'") return char
        val textBefore = activeContent.getTextBeforeCursor(1)
        val isOpening = textBefore.isEmpty() || textBefore.last().let {
            it.isWhitespace() || it == '(' || it == '[' || it == '{' || it == '\u2014' || it == '\u2013'
        }
        return when (char) {
            "\"" -> if (isOpening) "\u201C" else "\u201D"
            "'"  -> if (isOpening) "\u2018" else "\u2019"
            else -> char
        }
    }

    override fun commitChar(char: String): Boolean {
        // Any manual char commit means the user has moved past the gesture —
        // we no longer want the next backspace to wipe the swiped word.
        lastGestureCommitLength = 0
        ghostTextManager.cancelGhostText(clearComposing = true)
        // Smart Quotes: transform straight quotes to curly quotes
        val effectiveChar = if (prefs.correction.smartQuotes.get()) transformSmartQuote(char) else char
        val isInsertAutoSpaceBeforeChar = shouldInsertAutoSpaceBefore(effectiveChar)
        val isInsertAutoSpaceAfterChar = shouldInsertAutoSpaceAfter(effectiveChar)
        val isDeletePreviousSpace = isInsertAutoSpaceAfterChar && autoSpace.isActive
        if (isInsertAutoSpaceAfterChar) {
            autoSpace.setActive()
        } else {
            autoSpace.setInactive()
        }
        val isPhantomSpaceActive = phantomSpace.determine(effectiveChar)
        phantomSpace.setInactive()
        // Adaptive learned swipe typing: capture the in-progress composing
        // word BEFORE the commit (super.commitChar finalizes the composing
        // region). Only word terminators (space, newline, punctuation) cause
        // a learn — letters extend the composing region, they don't end it.
        val terminatesWord = effectiveChar == SPACE
            || effectiveChar == "\n"
            || (effectiveChar.length == 1 && !effectiveChar[0].isLetter() && !effectiveChar[0].isDigit())
        val composingBeforeCommit = if (terminatesWord) {
            activeContent.composingText.toString().ifBlank { activeContent.currentWordText.trim() }
        } else ""
        val result = super.commitChar(
            char = effectiveChar,
            deletePreviousSpace = isDeletePreviousSpace,
            insertSpaceBeforeChar = isInsertAutoSpaceBeforeChar || isPhantomSpaceActive,
            insertSpaceAfterChar = isInsertAutoSpaceAfterChar,
        )
        if (result && composingBeforeCommit.isNotBlank() && !activeState.isIncognitoMode) {
            // Fire-and-forget; provider does validation + threshold logic.
            lastCommittedNovelWord = composingBeforeCommit
            nlpManager.learnWord(subtypeManager.activeSubtype, composingBeforeCommit)
        } else if (result && !terminatesWord) {
            lastCommittedNovelWord = null
        }
        if (result && effectiveChar == SPACE) {
            ghostTextManager.requestGhostText()
        }
        return result
    }

    /**
     * Commits the given [text] to this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * This method overwrites any selected text and replaces it with given [text]. If there is no
     * text selected (selection is in cursor mode), then this method will insert the [text] after
     * the cursor, then set the cursor position to the first character after the inserted text.
     *
     * @param text The text to commit.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    override fun commitText(text: String): Boolean {
        // Non-gesture text commit (autocorrect pick, paste, emoji, etc.)
        // moves us past the gesture — invalidate the word-delete fallback.
        lastGestureCommitLength = 0
        
        // Adaptive learned swipe typing: capture the in-progress composing
        // word BEFORE the commit if this is a word terminator (like space).
        val terminatesWord = text == SPACE
            || text == "\n"
            || (text.length == 1 && !text[0].isLetter() && !text[0].isDigit())
        val composingBeforeCommit = if (terminatesWord) {
            activeContent.composingText.toString().ifBlank { activeContent.currentWordText.trim() }
        } else ""

        val isPhantomSpaceActive = phantomSpace.determine(text)
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val result = if (isPhantomSpaceActive) {
            super.commitText("$SPACE$text")
        } else {
            super.commitText(text)
        }
        
        if (result && composingBeforeCommit.isNotBlank() && !activeState.isIncognitoMode) {
            lastCommittedNovelWord = composingBeforeCommit
            nlpManager.learnWord(subtypeManager.activeSubtype, composingBeforeCommit)
        } else if (result && !terminatesWord) {
            lastCommittedNovelWord = null
        }
        return result
    }

    /**
     * Completes the given [candidate] in the current composing region. Does nothing if the current
     * input editor is not rich or if the input connection is invalid.
     *
     * Current phantom space state is respected and a space char will be inserted accordingly.
     * Phantom space will be activated if the text is committed.
     *
     * @param candidate The candidate to complete in this editor.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun commitCompletion(candidate: SuggestionCandidate): Boolean {
        val text = candidate.text.toString()
        if (text.isEmpty() || activeInfo.isRawInputEditor) return false
        val content = activeContent
        return if (content.composing.isValid) {
            phantomSpace.setActive(
                showComposingRegion = false,
                candidate = candidate,
                source = PhantomSpaceState.Source.COMPLETION,
            )
            super.finalizeComposingText(text)
        } else {
            val isPhantomSpaceActive = phantomSpace.determine(text)
            phantomSpace.setActive(
                showComposingRegion = false,
                candidate = candidate,
                source = PhantomSpaceState.Source.COMPLETION,
            )
            return if (isPhantomSpaceActive) {
                super.commitText("$SPACE$text")
            } else {
                super.commitText(text)
            }.also {
                // handled in finalizeComposingText if content.composing.isValid
                updateLastCommitPosition()
            }
        }
    }

    /**
     * Commit a word generated by a gesture.
     *
     * Ignores the current phantom space state and will insert a space depending on the character
     * before selection start. Phantom space will be activated if the text is committed.
     *
     * @param text The text to commit in this editor.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun commitGesture(text: String): Boolean {
        lastCommittedNovelWord = null
        if (text.isEmpty() || activeInfo.isRawInputEditor) return false
        val isPhantomSpaceActive = phantomSpace.determine(text, forceActive = true)
        phantomSpace.setActive(
            showComposingRegion = true,
            source = PhantomSpaceState.Source.GESTURE,
        )
        val committed = if (isPhantomSpaceActive) {
            super.commitText("$SPACE$text")
        } else {
            super.commitText(text)
        }
        if (committed) {
            // Remember exactly how many chars the gesture inserted so
            // [deleteBackwards] can word-delete in editors that don't expose
            // currentWord (e.g. Chrome / Instagram WebView fields). The
            // leading auto-space is NOT counted — backspace should leave
            // existing spacing intact and only remove the swiped word.
            lastGestureCommitLength = text.length
            updateLastCommitPosition()
        }
        return committed
    }

    /**
     * Commit a content URI to the host editor as rich media (GIF, sticker,
     * any `image/<sub>` MIME). Used by the GIF panel after a downloaded KLIPY
     * GIF has been cloned into [ClipboardFileStorage], and by the sticker
     * panel for both user-imported and WhatsApp-pack stickers.
     *
     * Reuses the same `InputContentInfoCompat` + `commitContent` path as
     * clipboard image paste — the host editor sees a standard rich-content
     * insert. Falls back to copying [fallbackText] to the clipboard when
     * the editor doesn't advertise rich-content support (most non-IM hosts).
     *
     * @param uri          Content URI exposing the media file. MUST be one
     *                     we have permission to grant — typically a
     *                     [ClipboardMediaProvider] URI.
     * @param mimeType     Concrete MIME (e.g. "image/gif", "image/webp").
     * @param description  Short label surfaced to accessibility services.
     * @param fallbackText URL or hint text copied to clipboard when the
     *                     host doesn't accept rich content. Blank → no
     *                     fallback (silent failure on incompatible hosts).
     */
    fun commitMedia(
        uri: android.net.Uri,
        mimeType: String,
        description: String,
        fallbackText: String = "",
    ): Boolean {
        var finalUri = uri
        var finalMimeType = mimeType

        // Check if the target editor supports this MIME type. If not, and it's image/webp,
        // see if it supports image/png instead, and convert it.
        val acceptsWebp = activeInfo.contentMimeTypes.any { type ->
            type.equals("image/webp", ignoreCase = true) ||
            type.equals("image/*", ignoreCase = true) ||
            type.equals("*/*", ignoreCase = true)
        }
        val acceptsPng = activeInfo.contentMimeTypes.any { type ->
            type.equals("image/png", ignoreCase = true) ||
            type.equals("image/*", ignoreCase = true) ||
            type.equals("*/*", ignoreCase = true)
        }

        if (mimeType.equals("image/webp", ignoreCase = true) && !acceptsWebp && acceptsPng) {
            val id = try { android.content.ContentUris.parseId(uri) } catch (e: Exception) { -1L }
            if (id != -1L) {
                val webpFile = ClipboardFileStorage.getFileForId(appContext, id)
                if (webpFile.exists()) {
                    val pngFile = java.io.File(appContext.cacheDir, "sticker_conv_${System.nanoTime()}.png")
                    try {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(webpFile.absolutePath)
                        if (bitmap != null) {
                            java.io.FileOutputStream(pngFile).use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                            }
                            // Insert the converted PNG as a new clip in the provider so it has an image/png type
                            val values = android.content.ContentValues(3).apply {
                                put(com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardMediaProvider.Columns.MediaUri, android.net.Uri.fromFile(pngFile).toString())
                                put(com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardMediaProvider.Columns.MimeTypes, "image/png")
                                put(android.provider.OpenableColumns.DISPLAY_NAME, "sticker")
                            }
                            val newUri = appContext.contentResolver.insert(com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardMediaProvider.IMAGE_CLIPS_URI, values)
                            if (newUri != null && !newUri.toString().endsWith("/0")) {
                                finalUri = newUri
                                finalMimeType = "image/png"
                            }
                        }
                    } catch (e: Exception) {
                        com.noxquill.rewordium.keyboard.lib.devtools.flogError { "Failed to convert webp to png: ${e.message}" }
                    } finally {
                        pngFile.delete()
                    }
                }
            }
        }

        val ic = currentInputConnection() ?: return false
        val inputContentInfo = InputContentInfoCompat(
            finalUri,
            ClipDescription(description, arrayOf(finalMimeType)),
            null,
        )
        ic.finishComposingText()
        val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        val ok = InputConnectionCompat.commitContent(ic, activeInfo.base, inputContentInfo, flags, null)
        if (!ok && fallbackText.isNotBlank()) {
            // Host editor didn't accept the rich-content commit — most chat
            // apps support it, but plain text fields don't. Copy a URL/hint
            // to the clipboard so the user can still share something.
            clipboardManager.addNewPlaintext(fallbackText)
            appContext.showShortToastSync("Pasted link to clipboard")
        }
        return ok
    }

    /**
     * Commits the given [ClipboardItem]. If the clip data is text (incl. HTML), it delegates to [commitText].
     * If the item has a content URI (and the EditText supports it), the item is committed as rich data.
     * This allows for committing (e.g) images.
     *
     * @param item The ClipboardItem to commit
     *
     * @return True on success, false if something went wrong.
     */
    fun commitClipboardItem(item: ClipboardItem?): Boolean {
        if (item == null) return false
        val mimeTypes = item.mimeTypes
        return when (item.type) {
            ItemType.TEXT -> {
                commitText(item.text.toString()).also {
                    updateLastCommitPosition()
                }
            }
            ItemType.IMAGE, ItemType.VIDEO -> {
                item.uri ?: return false
                val id = ContentUris.parseId(item.uri)
                val file = ClipboardFileStorage.getFileForId(appContext, id)
                if (!file.exists()) return false
                val inputContentInfo = InputContentInfoCompat(
                    item.uri,
                    ClipDescription("clipboard media file", mimeTypes.toTypedArray()),
                    null,
                )
                val ic = currentInputConnection() ?: return false
                ic.finishComposingText()
                val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                InputConnectionCompat.commitContent(ic, activeInfo.base, inputContentInfo, flags, null)
            }
        }.also {
            if (prefs.clipboard.historyHideOnPaste.get()) {
                keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
            }
        }
    }

    /**
     * Executes a backward delete on this editor's text. If a text selection is active, all
     * characters inside this selection will be removed, else only the left-most character from
     * the cursor's position.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun deleteBackwards(unit: OperationUnit): Boolean {
        if (lastCommittedNovelWord != null) {
            nlpManager.unlearnWord(subtypeManager.activeSubtype, lastCommittedNovelWord!!)
            lastCommittedNovelWord = null
        }

        val content = activeContent
        if (unit == OperationUnit.CHARACTERS) {
            if (
                prefs.glide.enabled.get() &&
                prefs.glide.immediateBackspaceDeletesWord.get() &&
                phantomSpace.isGestureTriggered
            ) {
                // Preferred path: the editor reports a valid currentWord, so
                // the standard WORDS delete uses BreakIterator-correct
                // boundaries. This works in WhatsApp, Telegram, native
                // EditText fields, etc.
                if (content.currentWord.isValid) {
                    return deleteBackwards(OperationUnit.WORDS)
                }
                // Fallback for WebView-backed editors (Chrome address bar,
                // Instagram comment box, etc.) that don't expose a composing
                // region or currentWord. We still know exactly how many
                // chars the last gesture inserted, so we issue a single
                // surrounding-text delete of that span directly. Without
                // this branch, those apps fall through to single-char
                // backspace and the "swipe-then-backspace deletes the
                // word" UX silently breaks.
                if (lastGestureCommitLength > 0) {
                    val ic = currentInputConnection()
                    if (ic != null) {
                        val n = lastGestureCommitLength
                        lastGestureCommitLength = 0
                        autoSpace.setInactive()
                        phantomSpace.setInactive()
                        ic.beginBatchEdit()
                        ic.finishComposingText()
                        val ok = ic.deleteSurroundingText(n, 0)
                        ic.endBatchEdit()
                        return ok
                    }
                }
            }
        }
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return if (content.selection.isSelectionMode) {
            commitText("")
        } else runBlocking {
            deleteAroundCursor(unit, OperationScope.BEFORE_CURSOR, n = 1)
        }
    }

    /**
     * Executes a backward delete on this editor's text. If a text selection is active, all
     * characters inside this selection will be removed, else only the left-most character from
     * the cursor's position.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun deleteForwards(unit: OperationUnit): Boolean {
        lastCommittedNovelWord = null
        val content = activeContent
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return if (content.selection.isSelectionMode) {
            commitText("")
        } else runBlocking {
            deleteAroundCursor(unit, OperationScope.AFTER_CURSOR, n = 1)
        }
    }

    fun setSelectionSurrounding(n: Int, unit: OperationUnit, scope: OperationScope): Boolean {
        lastCommittedNovelWord = null
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val content = activeContent
        val selection = content.selection
        val safeEditorBounds = content.safeEditorBounds
        if (selection.isNotValid) return false
        when (scope) {
            OperationScope.BEFORE_CURSOR -> {
                if (n <= 0) {
                    return setSelection(selection.end, selection.end)
                }
                val textToAnalyze = content.text.substring(0, content.localSelection.end)
                val length = runBlocking {
                    when (unit) {
                        OperationUnit.CHARACTERS -> breakIterators.measureLastUChars(textToAnalyze, n)
                        OperationUnit.WORDS -> breakIterators.measureLastUWords(textToAnalyze, n)
                    }
                }
                return setSelection((selection.end - length).coerceAtLeast(safeEditorBounds.start), selection.end)
            }
            OperationScope.AFTER_CURSOR -> {
                if (n <= 0) {
                    return setSelection(selection.start, selection.start)
                }
                val textToAnalyze = content.text.substring(content.localSelection.start)
                val length = runBlocking {
                    when (unit) {
                        OperationUnit.CHARACTERS -> breakIterators.measureUChars(textToAnalyze, n)
                        OperationUnit.WORDS -> breakIterators.measureUWords(textToAnalyze, n)
                    }
                }
                return setSelection(selection.start, (selection.start + length).coerceAtMost(safeEditorBounds.end))
            }
        }
    }

    /**
     * Performs a cut command on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardCut(): Boolean {
        lastCommittedNovelWord = null
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val text = activeContent.selectedText.ifBlank { currentInputConnection()?.getSelectedText(0) }
        if (text != null) {
            clipboardManager.addNewPlaintext(text.toString())
        } else {
            appContext.showShortToastSync("Failed to retrieve selected text requested to cut: Eiter selection state is invalid or an error occurred within the input connection.")
        }
        return deleteBackwards(OperationUnit.CHARACTERS)
    }

    /**
     * Performs a copy command on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardCopy(): Boolean {
        lastCommittedNovelWord = null
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val text = activeContent.selectedText.ifBlank { currentInputConnection()?.getSelectedText(0) }
        if (text != null) {
            clipboardManager.addNewPlaintext(text.toString())
        } else {
            appContext.showShortToastSync("Failed to retrieve selected text requested to copy: Eiter selection state is invalid or an error occurred within the input connection.")
        }
        val activeSelection = activeContent.selection
        return setSelection(activeSelection.end, activeSelection.end)
    }

    /**
     * Performs a paste command on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardPaste(): Boolean {
        lastCommittedNovelWord = null
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return commitClipboardItem(clipboardManager.primaryClip).also { result ->
            if (!result) {
                appContext.showShortToastSync("Failed to paste item.")
            }
        }
    }

    /**
     * Performs a select all on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardSelectAll(): Boolean {
        lastCommittedNovelWord = null
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val ic = currentInputConnection() ?: return false
        ic.finishComposingText()
        return if (activeInfo.isRawInputEditor) {
            sendDownUpKeyEvent(KeyEvent.KEYCODE_A, meta(ctrl = true))
        } else {
            ic.performContextMenuAction(android.R.id.selectAll)
        }
    }

    /**
     * Performs an enter key press on the current input editor.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performEnter(): Boolean {
        lastCommittedNovelWord = null
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return if (activeInfo.isRawInputEditor) {
            sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER)
        } else {
            commitText("\n")
        }
    }

    fun tryPerformEnterCommitRaw(): Boolean {
        return if (subtypeManager.activeSubtype.primaryLocale.language.startsWith("zh") && activeContent.composing.length > 0) {
            finalizeComposingText(activeContent.composingText)
        } else {
            false
        }
    }

    /**
     * Performs a given [action] on the current input editor.
     *
     * @param action The action to be performed on this editor instance.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performEnterAction(action: ImeOptions.Action): Boolean {
        lastCommittedNovelWord = null
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val ic = currentInputConnection() ?: return false
        return ic.performEditorAction(action.toInt())
    }

    /**
     * Undoes the last action.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performUndo(): Boolean {
        lastCommittedNovelWord = null
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return sendDownUpKeyEvent(KeyEvent.KEYCODE_Z, meta(ctrl = true))
    }

    /**
     * Redoes the last Undo action.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performRedo(): Boolean {
        lastCommittedNovelWord = null
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return sendDownUpKeyEvent(KeyEvent.KEYCODE_Z, meta(ctrl = true, shift = true))
    }

    override fun reset() {
        super.reset()
        lastCommittedNovelWord = null
        autoSpace.setInactive()
        phantomSpace.setInactive()
        massSelection.reset()
    }

    private fun PhantomSpaceState.determine(text: String, forceActive: Boolean = false): Boolean {
         val content = activeContent
         val selection = content.selection
         if (!(isActive || forceActive) || selection.isNotValid || selection.start <= 0 || text.isEmpty()) return false
         val textBefore = content.getTextBeforeCursor(1)
         val punctuationRule = nlpManager.getActivePunctuationRule()
         if (!subtypeManager.activeSubtype.primaryLocale.supportsAutoSpace) return false;
         return textBefore.isNotEmpty() &&
             (punctuationRule.symbolsPrecedingPhantomSpace.contains(textBefore[textBefore.length - 1]) ||
                 textBefore[textBefore.length - 1].isLetterOrDigit()) &&
             (punctuationRule.symbolsFollowingPhantomSpace.contains(text[0]) || text[0].isLetterOrDigit())
    }

    class AutoSpaceState {
        companion object {
            private const val F_IS_ACTIVE = 0x1
            private const val F_STAY_ACTIVE_NEXT_UPDATE = 0x4
        }

        private val state = AtomicInteger(0)

        val isActive: Boolean
            get() = state.get() and F_IS_ACTIVE != 0

        val isInactive: Boolean
            get() = !isActive

        fun setActive(stayActiveNextUpdate: Boolean = true) {
            state.set(F_IS_ACTIVE or (if (stayActiveNextUpdate) F_STAY_ACTIVE_NEXT_UPDATE else 0))
        }

        fun setInactive() {
            state.set(0)
        }

        fun setInactiveFromUpdate() {
            state.updateAndGet { state ->
                if ((state and F_STAY_ACTIVE_NEXT_UPDATE) != 0) (state and F_STAY_ACTIVE_NEXT_UPDATE.inv()) else 0
            }
        }
    }

    class PhantomSpaceState {
        enum class Source {
            NONE,
            GESTURE,
            COMPLETION,
        }

        companion object {
            private const val F_IS_ACTIVE = 0x1
            private const val F_SHOW_COMPOSING_REGION = 0x2
            private const val F_STAY_ACTIVE_NEXT_UPDATE = 0x4
        }

        private val state = AtomicInteger(0)
        private var source: Source = Source.NONE
        var candidateForRevert: SuggestionCandidate? = null
            private set

        val isActive: Boolean
            get() = state.get() and F_IS_ACTIVE != 0

        val isInactive: Boolean
            get() = !isActive

        val showComposingRegion: Boolean
            get() = state.get() and F_SHOW_COMPOSING_REGION != 0

        val isGestureTriggered: Boolean
            get() = isActive && source == Source.GESTURE

        fun setActive(
            showComposingRegion: Boolean,
            stayActiveNextUpdate: Boolean = true,
            candidate: SuggestionCandidate? = null,
            source: Source = Source.NONE,
        ) {
            state.set(
                F_IS_ACTIVE
                    or (if (showComposingRegion) F_SHOW_COMPOSING_REGION else 0)
                    or (if (stayActiveNextUpdate) F_STAY_ACTIVE_NEXT_UPDATE else 0)
            )
            candidateForRevert = candidate
            this.source = source
        }

        fun setInactive() {
            state.set(0)
            candidateForRevert = null
            source = Source.NONE
        }

        fun setInactiveFromUpdate() {
            val prevStateValue = state.getAndUpdate { state ->
                if ((state and F_STAY_ACTIVE_NEXT_UPDATE) != 0) (state and F_STAY_ACTIVE_NEXT_UPDATE.inv()) else 0
            }
            if ((prevStateValue and F_STAY_ACTIVE_NEXT_UPDATE) == 0) {
                candidateForRevert = null
                source = Source.NONE
            }
        }
    }

    inner class MassSelectionState {
        private val state = AtomicInteger(0)

        val isActive: Boolean
            get() = state.get() > 0

        val isInactive: Boolean
            get() = !isActive

        fun begin() {
            state.incrementAndGet()
        }

        fun end() {
            if (state.decrementAndGet() == 0) {
                // We need to emulate a selection update to update the content if mass selection has ended
                handleSelectionUpdate(EditorRange.Unspecified, activeContent.selection, EditorRange.Unspecified)
            }
        }

        fun reset() {
            state.set(0)
        }
    }
}
