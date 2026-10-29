package com.noxquill.rewordium.data

import android.content.Context
import android.content.SharedPreferences
import com.noxquill.rewordium.util.KeyboardConstants
import org.json.JSONArray
import org.json.JSONObject

data class KeyboardPersona(
    val name: String,
    val description: String,
    val builtIn: Boolean = false,
)

/** The voices the keyboard's AI rewrite can use, shared by the Personas screen and Jade. */
object KeyboardPersonas {
    val defaults = listOf(
        KeyboardPersona("Happy", "Cheerful, optimistic, and upbeat.", builtIn = true),
        KeyboardPersona("Sad", "Thoughtful, reflective, and gentle.", builtIn = true),
        KeyboardPersona("Humor", "Witty and playful while keeping the point clear.", builtIn = true),
        KeyboardPersona("Formal", "Polished and professional for work.", builtIn = true),
        KeyboardPersona("Casual", "Relaxed and conversational, like a message to a friend.", builtIn = true),
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(KeyboardConstants.PREFS_NAME, Context.MODE_PRIVATE)

    fun all(context: Context): List<KeyboardPersona> = defaults + custom(prefs(context))

    fun active(context: Context): String = prefs(context).getString("active_persona", "Happy") ?: "Happy"

    fun custom(prefs: SharedPreferences): List<KeyboardPersona> = runCatching {
        val json = prefs.getString("custom_personas", null) ?: return@runCatching emptyList()
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").trim()
            val description = item.optString("description").trim()
            if (name.isBlank() || description.isBlank()) null else KeyboardPersona(name, description)
        }
    }.getOrDefault(emptyList())

    fun saveCustom(context: Context, personas: List<KeyboardPersona>) {
        val json = JSONArray().apply {
            personas.filterNot { it.builtIn }.forEach { persona ->
                put(JSONObject().put("name", persona.name).put("description", persona.description))
            }
        }
        prefs(context).edit().putString("custom_personas", json.toString()).apply()
    }

    fun activate(context: Context, persona: KeyboardPersona) {
        prefs(context).edit()
            .putString("active_persona", persona.name)
            .putString("active_persona_description", persona.description)
            .apply()
    }
}
