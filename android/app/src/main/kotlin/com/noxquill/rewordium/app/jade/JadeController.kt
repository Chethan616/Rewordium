package com.noxquill.rewordium.app.jade

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.data.RewordiumRepository
import com.noxquill.rewordium.data.jade.AppSettingsCatalog
import com.noxquill.rewordium.data.jade.JadeChatStore
import com.noxquill.rewordium.data.jade.JadeChatSummary
import com.noxquill.rewordium.data.jade.JadeEngine
import com.noxquill.rewordium.data.jade.JadeItem
import com.noxquill.rewordium.data.jade.JadeSkills
import com.noxquill.rewordium.data.jade.JadeToolHost
import com.noxquill.rewordium.data.jade.JadeTurn
import com.noxquill.rewordium.data.jade.SettingChangeRecord
import com.noxquill.rewordium.data.jade.SettingProposal
import com.noxquill.rewordium.data.jade.SkillOptions
import com.noxquill.rewordium.data.jade.newJadeId
import com.noxquill.rewordium.keyboard.app.ReboardSettingsBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Jade's conversation, kept above the screens so a reply keeps arriving (and
 * the draft stays put) while the user hops to another page and back.
 */
@Stable
class JadeController(
    context: Context,
    private val scope: CoroutineScope,
    /** The signed-in account's id; chats are stored separately per account. */
    private val owner: () -> String,
    private val userName: () -> String?,
    /** Throws with a friendly message when the user can't make a request (signed out, no credits). */
    private val beforeRequest: suspend () -> Unit,
    /** Called once per successful request (charges a credit when needed). */
    private val afterSuccess: suspend () -> Unit,
) {
    private val appContext = context.applicationContext
    private val engine = JadeEngine(appContext)
    private val store = JadeChatStore(appContext)
    private val approvals = HashMap<String, CompletableDeferred<Boolean>>()
    private var job: Job? = null

    var chatId by mutableStateOf(newJadeId()); private set
    val items = mutableStateListOf<JadeItem>()
    /** Items before this index were loaded from history and appear without an entrance animation. */
    var animateFrom by mutableStateOf(0); private set
    var working by mutableStateOf(false); private set
    var status by mutableStateOf<String?>(null); private set
    var chats by mutableStateOf<List<JadeChatSummary>>(emptyList()); private set
    var notice by mutableStateOf<String?>(null)

    var draft by mutableStateOf("")
    var skill by mutableStateOf(JadeSkills.CHAT)
    var tone by mutableStateOf(JadeSkills.tones.first())
    var language by mutableStateOf("English")

    private var flash by mutableStateOf<BotMood?>(null)
    private var awaitingApproval by mutableStateOf(false)

    val mood: BotMood
        get() = when {
            awaitingApproval -> BotMood.NeedsYou
            working && status?.contains("Tuning") == true -> BotMood.Working
            working -> BotMood.Thinking
            else -> flash ?: BotMood.Idle
        }

    val canUseTools: Boolean get() = engine.canUseTools()

    init {
        scope.launch { refreshChats() }
    }

    fun send(text: String = draft) {
        val clean = text.trim()
        if (clean.isBlank() || working) return
        val currentSkill = skill
        val options = SkillOptions(tone = tone, language = language)
        val sendChat = chatId
        items += JadeItem.User(newJadeId(), clean, currentSkill)
        if (text == draft) draft = ""
        working = true
        status = if (currentSkill == JadeSkills.CHAT) "Thinking…" else "Working on your ${jadeSkill(currentSkill).label.lowercase()}…"
        fun post(item: JadeItem) { if (chatId == sendChat) items += item }
        job = scope.launch {
            try {
                beforeRequest()
                val reply = if (currentSkill == JadeSkills.CHAT) {
                    engine.reply(userName(), history(), clean, host)
                } else {
                    engine.runSkill(currentSkill, clean, options)
                }
                post(JadeItem.Reply(newJadeId(), reply, currentSkill))
                runCatching { afterSuccess() }
                pulse(BotMood.Happy)
            } catch (e: CancellationException) {
                post(JadeItem.Failure(newJadeId(), "Stopped."))
            } catch (e: Throwable) {
                post(JadeItem.Failure(newJadeId(), e.message ?: "Jade couldn’t reply just now. Please try again."))
                pulse(BotMood.Sad)
            } finally {
                approvals.values.forEach { it.complete(false) }
                approvals.clear()
                awaitingApproval = false
                working = false
                status = null
                if (chatId == sendChat) persist()
            }
        }
    }

    /** Jade designs a keyboard theme for [description]; counts as one Jade request. */
    suspend fun designTheme(description: String): com.noxquill.rewordium.keyboard.ime.theme.studio.KeyboardThemeSpec {
        beforeRequest()
        val spec = engine.designTheme(description)
        runCatching { afterSuccess() }
        return spec
    }

    fun stop() {
        job?.cancel()
    }

    /** Starts a fresh chat and sends [prompt] right away (Home's quick asks). */
    fun ask(prompt: String, skillId: String = JadeSkills.CHAT) {
        if (working) return
        if (items.isNotEmpty()) newChat()
        skill = skillId
        send(prompt)
    }

    fun resolveApproval(id: String, allowed: Boolean) {
        approvals.remove(id)?.complete(allowed)
        awaitingApproval = approvals.isNotEmpty()
        replace(id) { (it as? JadeItem.Approval)?.copy(state = if (allowed) JadeItem.Approval.State.Allowed else JadeItem.Approval.State.Declined) }
    }

    fun undo(id: String) {
        val card = items.firstOrNull { it.id == id } as? JadeItem.Changes ?: return
        if (card.undone) return
        replace(id) { (it as JadeItem.Changes).copy(undone = true) }
        scope.launch {
            val failed = card.changes.reversed().count { change ->
                val result = if (AppSettingsCatalog.owns(change.key)) AppSettingsCatalog.set(appContext, change.key, change.restore)
                else ReboardSettingsBridge.set(appContext, change.key, change.restore)
                result.isFailure
            }
            notice = if (failed == 0) "Changes undone" else "Couldn’t undo $failed change${if (failed == 1) "" else "s"}"
            persist()
        }
    }

    fun report(id: String, reason: String, details: String) {
        val index = items.indexOfFirst { it.id == id }
        val reply = items.getOrNull(index) as? JadeItem.Reply ?: return
        val prompt = items.take(index).lastOrNull { it is JadeItem.User } as? JadeItem.User
        scope.launch {
            runCatching { RewordiumRepository.reportAiContent(appContext, reason, details, reply.text, prompt?.text) }
                .onSuccess {
                    replace(id) { (it as JadeItem.Reply).copy(reported = true) }
                    notice = "Thanks — your report was sent"
                    persist()
                }
                .onFailure { notice = it.message ?: "Couldn’t send the report. Try again." }
        }
    }

    fun newChat() {
        stop()
        chatId = newJadeId()
        items.clear()
        animateFrom = 0
        flash = null
    }

    fun openChat(id: String) {
        stop()
        val account = owner()
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { store.load(account, id) }
            chatId = id
            items.clear()
            items.addAll(loaded)
            animateFrom = loaded.size
        }
    }

    fun deleteChat(id: String) {
        val account = owner()
        scope.launch {
            withContext(Dispatchers.IO) { store.delete(account, id) }
            if (id == chatId) newChat()
            refreshChats()
        }
    }

    /** Deletes this account's saved chats. The account is read now, so it's safe to sign out right after. */
    fun clearHistory() {
        val account = owner()
        newChat()
        chats = emptyList()
        scope.launch {
            withContext(Dispatchers.IO) { store.clear(account) }
            refreshChats()
        }
    }

    /** Someone signed in or out: start fresh and show only their chats. */
    /** Re-reads the chat list from storage — after a backup is restored, say. */
    fun reloadChats() {
        scope.launch { refreshChats() }
    }

    fun accountChanged() {
        newChat()
        draft = ""
        skill = JadeSkills.CHAT
        scope.launch { refreshChats() }
    }

    private val host = object : JadeToolHost {
        override fun status(text: String) {
            scope.launch(Dispatchers.Main.immediate) { status = text }
        }

        override suspend fun applied(changes: List<SettingChangeRecord>) = withContext(Dispatchers.Main) {
            items += JadeItem.Changes(newJadeId(), changes)
        }

        override suspend fun approve(proposals: List<SettingProposal>): Boolean {
            val id = newJadeId()
            val decision = CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main) {
                approvals[id] = decision
                awaitingApproval = true
                items += JadeItem.Approval(id, proposals)
                status = "Waiting for your okay…"
            }
            val allowed = decision.await()
            withContext(Dispatchers.Main) { status = if (allowed) "Tuning your settings…" else "Thinking…" }
            return allowed
        }

        override fun suggestScreen(path: String, label: String) {
            scope.launch(Dispatchers.Main.immediate) { items += JadeItem.Link(newJadeId(), path, label) }
        }
    }

    /** The conversation so far, as the model should see it. */
    private fun history(): List<JadeTurn> = items.dropLast(1).mapNotNull { item ->
        when (item) {
            is JadeItem.User -> JadeTurn(
                true,
                if (item.skill == JadeSkills.CHAT) item.text else "[${jadeSkill(item.skill).label} this]\n${item.text}",
            )
            is JadeItem.Reply -> JadeTurn(false, item.text)
            is JadeItem.Changes -> JadeTurn(
                false,
                "(Settings ${if (item.undone) "changed, then undone by the user" else "changed"}: " +
                    item.changes.joinToString("; ") { "${it.title}: ${it.previous} → ${it.current}" } + ")",
            )
            else -> null
        }
    }.takeLast(12)

    private fun replace(id: String, transform: (JadeItem) -> JadeItem?) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        transform(items[index])?.let { items[index] = it }
    }

    private fun pulse(mood: BotMood) {
        flash = mood
        scope.launch {
            delay(2_600)
            if (flash == mood) flash = null
        }
    }

    private fun persist() {
        val snapshot = items.toList()
        val id = chatId
        val account = owner()
        scope.launch {
            withContext(Dispatchers.IO) { store.save(account, id, snapshot) }
            refreshChats()
        }
    }

    private suspend fun refreshChats() {
        val account = owner()
        chats = withContext(Dispatchers.IO) { store.list(account) }
    }
}
