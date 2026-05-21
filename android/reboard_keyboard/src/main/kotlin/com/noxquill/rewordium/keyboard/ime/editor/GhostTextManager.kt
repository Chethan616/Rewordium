package com.noxquill.rewordium.keyboard.ime.editor

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import com.noxquill.rewordium.keyboard.BuildConfig
import com.noxquill.rewordium.keyboard.FlorisImeService
import com.noxquill.rewordium.keyboard.nlpManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GhostTextManager(context: Context) {
    private val nlpManager by context.nlpManager()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var pendingJob: Job? = null
    @Volatile private var shownText: String? = null

    val isShowing: Boolean get() = shownText != null
    val currentGhost: String? get() = shownText

    fun requestGhostText() {
        if (!BuildConfig.ENABLE_INLINE_COMPLETIONS) return
        cancelGhostText(clearComposing = true)
        pendingJob = scope.launch {
            delay(IDLE_DELAY_MS)
            val candidate = nlpManager.activeCandidates.firstOrNull() ?: return@launch
            val text = candidate.text?.toString()?.trim().orEmpty()
            if (text.isBlank() || text.length > MAX_GHOST_LEN) return@launch
            showGhost(text)
        }
    }

    fun cancelGhostText(clearComposing: Boolean = true) {
        pendingJob?.cancel()
        pendingJob = null
        if (clearComposing && shownText != null) {
            val ic = FlorisImeService.currentInputConnection()
            shownText = null
            if (ic != null) {
                ic.beginBatchEdit()
                try {
                    ic.setComposingText("", 1)
                    ic.finishComposingText()
                } finally {
                    ic.endBatchEdit()
                }
            }
        } else if (shownText != null) {
            shownText = null
        }
    }

    fun acceptGhostText(): Boolean {
        val text = shownText ?: return false
        val ic = FlorisImeService.currentInputConnection() ?: run {
            shownText = null
            return false
        }
        ic.beginBatchEdit()
        try {
            ic.setComposingText("", 1)
            ic.finishComposingText()
            ic.commitText("$text ", 1)
        } finally {
            ic.endBatchEdit()
        }
        shownText = null
        return true
    }

    private fun showGhost(text: String) {
        val ic = FlorisImeService.currentInputConnection() ?: return
        val styled = SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(GHOST_COLOR),
                0,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        ic.beginBatchEdit()
        try {
            ic.setComposingText(styled, 1)
        } finally {
            ic.endBatchEdit()
        }
        shownText = text
    }

    companion object {
        private const val IDLE_DELAY_MS = 280L
        private const val MAX_GHOST_LEN = 24
        private const val GHOST_COLOR = 0x66888888.toInt()
    }
}
