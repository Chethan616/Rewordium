package com.noxquill.rewordium.data.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.noxquill.rewordium.data.AppSettings
import com.noxquill.rewordium.data.jade.JadeChatStore
import com.noxquill.rewordium.keyboard.app.ReboardBackup
import com.noxquill.rewordium.util.KeyboardConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A complete, local backup of Rewordium in one .zip file the user keeps
 * wherever they like on their phone: every Jade chat (for every account used
 * here), the app's look and writing preferences, personas, and the whole
 * keyboard — settings, themes, layouts and learned words (clipboard text
 * only when asked). Nothing is uploaded.
 *
 * Secrets and account state never go into a backup file: no AI provider API
 * key, no sign-in, credits or Pro status — those come from the account.
 */
object RewordiumBackup {
    private const val TAG = "RewordiumBackup"
    const val MIME_TYPE = "application/zip"
    private const val FORMAT = 1
    private const val MANIFEST = "rewordium_backup.json"
    private const val JADE_DIR = "jade"
    private const val PREFS_FILE = "app_preferences.json"
    private const val KEYBOARD_DIR = "keyboard"
    private const val MAX_UNZIPPED_BYTES = 512L * 1024 * 1024

    /** The app's own preference files, and the keys in each that must never leave the device. */
    private val PREFERENCE_FILES = mapOf(
        "rewordium_app" to emptySet(),
        KeyboardConstants.PREFS_NAME to setOf("user_logged_in", "user_is_pro", "user_credits", KeyboardConstants.KEY_KEYBOARD_SELECTED_AS_DEFAULT),
        "ai_settings" to setOf("ai_api_key"),
    )
    private val SECRET_KEY = Regex("(?i)(api_?key|token|secret|password)")

    data class Options(
        val jadeChats: Boolean = true,
        val appSettings: Boolean = true,
        val keyboard: Boolean = true,
        val clipboard: Boolean = false,
    )

    /** What a backup holds. */
    data class Summary(
        val createdAt: Long,
        val appVersion: String,
        val jadeChats: Int,
        val appSettings: Boolean,
        val keyboardSettings: Boolean,
        val keyboardThemes: Int,
        val keyboardLayouts: Int,
        val learnedWords: Boolean,
        val clipboardItems: Int,
    )

    /** A backup file opened and checked, ready to restore. */
    class Opened internal constructor(internal val dir: File, val summary: Summary) {
        fun discard() { dir.deleteRecursively() }
    }

    fun suggestedFileName(now: Long = System.currentTimeMillis()): String =
        "Rewordium-backup-${SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(now))}.zip"

    private fun workspace(context: Context): File =
        File(context.cacheDir, "rewordium-backup/${UUID.randomUUID()}").apply { mkdirs() }

    /** Writes a backup to [destination] (a document the user picked). */
    suspend fun create(context: Context, destination: Uri, options: Options): Result<Summary> {
        val dir = workspace(context)
        return runCatching {
            val chats = if (options.jadeChats) withContext(Dispatchers.IO) { JadeChatStore(context).backupTo(File(dir, JADE_DIR)) } else 0
            if (options.appSettings) withContext(Dispatchers.IO) { File(dir, PREFS_FILE).writeText(exportPreferences(context).toString()) }
            val keyboard = if (options.keyboard) {
                ReboardBackup.exportTo(context, File(dir, KEYBOARD_DIR), options.clipboard)
                    .onFailure { Log.w(TAG, "Keyboard part of the backup failed", it) }
                    .getOrNull()
            } else null
            val summary = Summary(
                createdAt = System.currentTimeMillis(),
                appVersion = appVersion(context),
                jadeChats = chats,
                appSettings = options.appSettings,
                keyboardSettings = keyboard?.settings == true,
                keyboardThemes = keyboard?.themes ?: 0,
                keyboardLayouts = keyboard?.layouts ?: 0,
                learnedWords = keyboard?.learnedWords == true,
                clipboardItems = keyboard?.clipboardItems ?: 0,
            )
            if (chats == 0 && !options.appSettings && keyboard == null) error("There was nothing to back up.")
            withContext(Dispatchers.IO) {
                File(dir, MANIFEST).writeText(summary.toJson().toString(2))
                val out = context.contentResolver.openOutputStream(destination, "w") ?: throw IOException("Couldn’t open the backup file for writing.")
                ZipOutputStream(out.buffered()).use { zip -> zipInto(zip, dir, "") }
            }
            summary
        }.also { dir.deleteRecursively() }
    }

    /** Opens and checks a backup file the user picked, without changing anything yet. */
    suspend fun open(context: Context, source: Uri): Result<Opened> {
        val dir = workspace(context)
        return runCatching {
            withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(source) ?: throw IOException("Couldn’t open that file.")
                ZipInputStream(input.buffered()).use { zip -> unzipInto(zip, dir) }
            }
            val manifest = File(dir, MANIFEST)
            if (!manifest.exists()) error("That file isn’t a Rewordium backup.")
            val json = JSONObject(manifest.readText())
            if (json.optInt("format") > FORMAT) error("This backup was made by a newer Rewordium. Update the app to restore it.")
            Opened(dir, summaryFrom(json))
        }.onFailure { dir.deleteRecursively() }
    }

    /** What a restore actually put back. */
    data class Restored(val jadeChats: Int, val appSettings: Boolean, val keyboard: Boolean, val keyboardError: String?)

    /**
     * Restores an [opened] backup: preferences are put back, Jade chats are
     * merged with the ones on this phone (the newer copy of a chat wins), and
     * the keyboard returns to the backed-up settings with its themes, layouts
     * and learned words. If the keyboard engine can't start, everything else
     * is still restored and [Restored.keyboardError] says why.
     */
    suspend fun restore(context: Context, opened: Opened): Result<Restored> = runCatching {
        val dir = opened.dir
        val prefs = File(dir, PREFS_FILE)
        if (prefs.exists()) withContext(Dispatchers.IO) { importPreferences(context, JSONObject(prefs.readText())) }
        val jade = File(dir, JADE_DIR)
        val chats = if (jade.exists()) withContext(Dispatchers.IO) { JadeChatStore(context).restoreFrom(jade) } else 0
        val keyboardDir = File(dir, KEYBOARD_DIR)
        val keyboard = if (keyboardDir.exists()) {
            ReboardBackup.importFrom(context, keyboardDir).onFailure { Log.w(TAG, "Keyboard part of the restore failed", it) }
        } else null
        withContext(Dispatchers.Main) { AppSettings.reload() }
        Restored(
            jadeChats = chats,
            appSettings = prefs.exists(),
            keyboard = keyboard?.isSuccess == true,
            keyboardError = keyboard?.exceptionOrNull()?.message,
        )
    }.also { opened.discard() }

    // ── Preferences ──────────────────────────────────────────────────────────

    private fun exportPreferences(context: Context): JSONObject {
        val root = JSONObject()
        PREFERENCE_FILES.forEach { (name, excluded) ->
            val values = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                if (key in excluded || SECRET_KEY.containsMatchIn(key) || value == null) return@forEach
                typed(value)?.let { values.put(key, it) }
            }
            root.put(name, values)
        }
        return root
    }

    private fun typed(value: Any): JSONObject? = when (value) {
        is String -> JSONObject().put("type", "string").put("value", value)
        is Boolean -> JSONObject().put("type", "boolean").put("value", value)
        is Int -> JSONObject().put("type", "int").put("value", value)
        is Long -> JSONObject().put("type", "long").put("value", value)
        is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
        is Set<*> -> JSONObject().put("type", "set").put("value", JSONArray(value.filterIsInstance<String>()))
        else -> null
    }

    private fun importPreferences(context: Context, root: JSONObject) {
        PREFERENCE_FILES.forEach { (name, excluded) ->
            val values = root.optJSONObject(name) ?: return@forEach
            val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            values.keys().forEach { key ->
                if (key in excluded || SECRET_KEY.containsMatchIn(key)) return@forEach
                values.optJSONObject(key)?.let { put(edit, key, it) }
            }
            edit.commit()
        }
    }

    private fun put(edit: SharedPreferences.Editor, key: String, entry: JSONObject) {
        when (entry.optString("type")) {
            "string" -> edit.putString(key, entry.optString("value"))
            "boolean" -> edit.putBoolean(key, entry.optBoolean("value"))
            "int" -> edit.putInt(key, entry.optInt("value"))
            "long" -> edit.putLong(key, entry.optLong("value"))
            "float" -> edit.putFloat(key, entry.optDouble("value").toFloat())
            "set" -> {
                val array = entry.optJSONArray("value") ?: JSONArray()
                edit.putStringSet(key, (0 until array.length()).map { array.optString(it) }.toSet())
            }
        }
    }

    // ── Manifest ─────────────────────────────────────────────────────────────

    private fun Summary.toJson() = JSONObject()
        .put("format", FORMAT)
        .put("createdAt", createdAt)
        .put("appVersion", appVersion)
        .put("jadeChats", jadeChats)
        .put("appSettings", appSettings)
        .put("keyboardSettings", keyboardSettings)
        .put("keyboardThemes", keyboardThemes)
        .put("keyboardLayouts", keyboardLayouts)
        .put("learnedWords", learnedWords)
        .put("clipboardItems", clipboardItems)

    private fun summaryFrom(json: JSONObject) = Summary(
        createdAt = json.optLong("createdAt"),
        appVersion = json.optString("appVersion"),
        jadeChats = json.optInt("jadeChats"),
        appSettings = json.optBoolean("appSettings"),
        keyboardSettings = json.optBoolean("keyboardSettings"),
        keyboardThemes = json.optInt("keyboardThemes"),
        keyboardLayouts = json.optInt("keyboardLayouts"),
        learnedWords = json.optBoolean("learnedWords"),
        clipboardItems = json.optInt("clipboardItems"),
    )

    private fun appVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    // ── Zip ──────────────────────────────────────────────────────────────────

    private fun zipInto(zip: ZipOutputStream, dir: File, prefix: String) {
        dir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            val name = prefix + file.name
            if (file.isDirectory) {
                zipInto(zip, file, "$name/")
            } else {
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Unzips into [dir], refusing entries that would land outside it and archives that unpack too large. */
    private fun unzipInto(zip: ZipInputStream, dir: File) {
        val root = dir.canonicalFile
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val entry = zip.nextEntry ?: break
            val target = File(root, entry.name).canonicalFile
            if (target != root && !target.path.startsWith(root.path + File.separator)) {
                throw IOException("That backup file is damaged.")
            }
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                target.outputStream().use { out ->
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_UNZIPPED_BYTES) throw IOException("That backup file is too large.")
                        out.write(buffer, 0, read)
                    }
                }
            }
            zip.closeEntry()
        }
    }
}
