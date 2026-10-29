package com.noxquill.rewordium.data.jade

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One settings change Jade made, kept so it can be undone. */
data class SettingChangeRecord(
    val key: String,
    val title: String,
    val previous: String,
    val current: String,
    val note: String? = null,
    /** The exact earlier value, for undo. */
    val restore: String = previous,
)

/** A change Jade wants to make to an advanced setting and needs the user's okay for. */
data class SettingProposal(val key: String, val title: String, val current: String, val proposed: String)

/** Everything that can appear in a Jade conversation. */
sealed interface JadeItem {
    val id: String

    data class User(override val id: String, val text: String, val skill: String = "chat") : JadeItem
    data class Reply(override val id: String, val text: String, val skill: String = "chat", val reported: Boolean = false) : JadeItem
    data class Changes(override val id: String, val changes: List<SettingChangeRecord>, val undone: Boolean = false) : JadeItem
    data class Approval(override val id: String, val proposals: List<SettingProposal>, val state: State = State.Pending) : JadeItem {
        enum class State { Pending, Allowed, Declined }
    }
    data class Link(override val id: String, val path: String, val label: String) : JadeItem
    data class Failure(override val id: String, val text: String) : JadeItem
}

fun newJadeId(): String = UUID.randomUUID().toString()

data class JadeChatSummary(val id: String, val title: String, val preview: String, val updatedAt: Long, val count: Int)

/**
 * Jade conversations, kept only in this app's private storage on the
 * device and separately for each signed-in account, so someone else signing
 * in on the same phone never sees them. Nothing here is uploaded; deleting a
 * chat (or the account) removes it for good.
 *
 * Every call names its [owner] (the account id) explicitly, captured when
 * the request was made rather than when it runs.
 */
class JadeChatStore(context: Context) {
    private val dir = context.filesDir
    private val lock = Any()

    private fun fileFor(owner: String) = File(dir, "jade_chats_${owner.filter { it.isLetterOrDigit() }.ifBlank { "guest" }}.json")

    private fun readAll(owner: String): JSONArray = synchronized(lock) {
        val file = fileFor(owner)
        runCatching { if (file.exists()) JSONArray(file.readText()) else JSONArray() }.getOrDefault(JSONArray())
    }

    private fun writeAll(owner: String, array: JSONArray) = synchronized(lock) {
        runCatching {
            val file = fileFor(owner)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(array.toString())
            tmp.renameTo(file)
        }
    }

    fun list(owner: String): List<JadeChatSummary> {
        val array = readAll(owner)
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { chat ->
            val items = chat.optJSONArray("items") ?: JSONArray()
            val lastText = (items.length() - 1 downTo 0).asSequence()
                .mapNotNull { items.optJSONObject(it)?.optString("text")?.takeIf(String::isNotBlank) }
                .firstOrNull().orEmpty()
            JadeChatSummary(
                id = chat.optString("id"),
                title = chat.optString("title").ifBlank { "Chat with Jade" },
                preview = lastText.replace('\n', ' ').take(120),
                updatedAt = chat.optLong("updatedAt"),
                count = items.length(),
            )
        }.sortedByDescending { it.updatedAt }
    }

    fun load(owner: String, id: String): List<JadeItem> {
        val array = readAll(owner)
        val chat = (0 until array.length()).mapNotNull { array.optJSONObject(it) }.firstOrNull { it.optString("id") == id }
            ?: return emptyList()
        val items = chat.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { items.optJSONObject(it)?.let(::decode) }
    }

    fun save(owner: String, id: String, items: List<JadeItem>) {
        val persisted = items.filterNot { it is JadeItem.Failure }.takeLast(MAX_ITEMS)
        if (persisted.none { it is JadeItem.User }) return
        val title = persisted.filterIsInstance<JadeItem.User>().first().text.lineSequence().first().take(60)
        val array = readAll(owner)
        val kept = JSONArray()
        for (i in 0 until array.length()) {
            val chat = array.optJSONObject(i) ?: continue
            if (chat.optString("id") != id) kept.put(chat)
        }
        val entry = JSONObject()
            .put("id", id)
            .put("title", title)
            .put("updatedAt", System.currentTimeMillis())
            .put("items", JSONArray().apply { persisted.forEach { put(encode(it)) } })
        // Newest first, capped.
        val result = JSONArray().put(entry)
        val sorted = (0 until kept.length()).mapNotNull { kept.optJSONObject(it) }.sortedByDescending { it.optLong("updatedAt") }
        sorted.take(MAX_CHATS - 1).forEach { result.put(it) }
        writeAll(owner, result)
    }

    fun delete(owner: String, id: String) {
        val array = readAll(owner)
        val kept = JSONArray()
        for (i in 0 until array.length()) {
            val chat = array.optJSONObject(i) ?: continue
            if (chat.optString("id") != id) kept.put(chat)
        }
        writeAll(owner, kept)
    }

    fun clear(owner: String) = synchronized(lock) { fileFor(owner).delete(); Unit }

    /** Copies every account's chat file into [target] for a backup; returns how many chats were copied. */
    fun backupTo(target: File): Int = synchronized(lock) {
        target.mkdirs()
        var chats = 0
        dir.listFiles { file -> CHAT_FILE.matches(file.name) }?.forEach { file ->
            file.copyTo(File(target, file.name), overwrite = true)
            chats += runCatching { JSONArray(file.readText()).length() }.getOrDefault(0)
        }
        chats
    }

    /**
     * Merges the chat files in a backup [source] into this phone's, chat by
     * chat: chats only on the phone stay, chats only in the backup come back,
     * and where both have a chat the newer copy wins. Each account's chats go
     * back to that account. Returns how many chats the backup held.
     */
    fun restoreFrom(source: File): Int = synchronized(lock) {
        var restored = 0
        source.listFiles { file -> CHAT_FILE.matches(file.name) }?.forEach { backup ->
            val incoming = runCatching { JSONArray(backup.readText()) }.getOrNull() ?: return@forEach
            val target = File(dir, backup.name)
            val current = runCatching { if (target.exists()) JSONArray(target.readText()) else JSONArray() }.getOrDefault(JSONArray())
            val byId = LinkedHashMap<String, JSONObject>()
            for (array in listOf(current, incoming)) {
                for (i in 0 until array.length()) {
                    val chat = array.optJSONObject(i) ?: continue
                    val id = chat.optString("id")
                    if (id.isBlank()) continue
                    val held = byId[id]
                    if (held == null || chat.optLong("updatedAt") > held.optLong("updatedAt")) byId[id] = chat
                }
            }
            restored += incoming.length()
            val merged = JSONArray()
            byId.values.sortedByDescending { it.optLong("updatedAt") }.take(MAX_CHATS).forEach { merged.put(it) }
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(merged.toString())
            tmp.renameTo(target)
        }
        restored
    }

    private fun encode(item: JadeItem): JSONObject = JSONObject().put("id", item.id).apply {
        when (item) {
            is JadeItem.User -> put("type", "user").put("text", item.text).put("skill", item.skill)
            is JadeItem.Reply -> put("type", "reply").put("text", item.text).put("skill", item.skill).put("reported", item.reported)
            is JadeItem.Changes -> put("type", "changes").put("undone", item.undone).put(
                "changes",
                JSONArray().apply {
                    item.changes.forEach {
                        put(JSONObject().put("key", it.key).put("title", it.title).put("previous", it.previous).put("current", it.current).put("restore", it.restore))
                    }
                },
            )
            is JadeItem.Approval -> put("type", "approval")
                .put("state", if (item.state == JadeItem.Approval.State.Pending) JadeItem.Approval.State.Declined.name else item.state.name)
                .put(
                    "proposals",
                    JSONArray().apply {
                        item.proposals.forEach {
                            put(JSONObject().put("key", it.key).put("title", it.title).put("current", it.current).put("proposed", it.proposed))
                        }
                    },
                )
            is JadeItem.Link -> put("type", "link").put("path", item.path).put("label", item.label)
            is JadeItem.Failure -> put("type", "failure").put("text", item.text)
        }
    }

    private fun decode(json: JSONObject): JadeItem? {
        val id = json.optString("id").ifBlank { newJadeId() }
        return when (json.optString("type")) {
            "user" -> JadeItem.User(id, json.optString("text"), json.optString("skill", "chat"))
            "reply" -> JadeItem.Reply(id, json.optString("text"), json.optString("skill", "chat"), json.optBoolean("reported"))
            "changes" -> {
                val changes = json.optJSONArray("changes") ?: JSONArray()
                JadeItem.Changes(
                    id,
                    (0 until changes.length()).mapNotNull { changes.optJSONObject(it) }.map {
                        SettingChangeRecord(
                            key = it.optString("key"),
                            title = it.optString("title"),
                            previous = it.optString("previous"),
                            current = it.optString("current"),
                            restore = it.optString("restore").ifBlank { it.optString("previous") },
                        )
                    },
                    json.optBoolean("undone"),
                )
            }
            "approval" -> {
                val proposals = json.optJSONArray("proposals") ?: JSONArray()
                JadeItem.Approval(
                    id,
                    (0 until proposals.length()).mapNotNull { proposals.optJSONObject(it) }.map {
                        SettingProposal(it.optString("key"), it.optString("title"), it.optString("current"), it.optString("proposed"))
                    },
                    runCatching { JadeItem.Approval.State.valueOf(json.optString("state")) }.getOrDefault(JadeItem.Approval.State.Declined),
                )
            }
            "link" -> JadeItem.Link(id, json.optString("path"), json.optString("label"))
            else -> null
        }
    }

    companion object {
        private const val MAX_CHATS = 30
        private const val MAX_ITEMS = 200
        private val CHAT_FILE = Regex("jade_chats_[A-Za-z0-9]+\\.json")
    }
}
