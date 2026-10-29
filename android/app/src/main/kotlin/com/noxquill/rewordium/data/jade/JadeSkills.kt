package com.noxquill.rewordium.data.jade

/** Options a writing skill can take. */
data class SkillOptions(val tone: String = "Warm", val language: String = "English")

/**
 * Jade's one-tap writing skills. They used to be separate tool screens; now
 * each is a mode of the same conversation, so results can be refined by
 * simply replying.
 */
object JadeSkills {
    const val CHAT = "chat"

    val tones = listOf("Warm", "Clear", "Confident", "Professional", "Friendly", "Playful", "Persuasive", "Calm")
    val languages = listOf(
        "English", "Spanish", "French", "German", "Hindi", "Kannada", "Tamil", "Telugu", "Bengali", "Marathi",
        "Portuguese", "Italian", "Japanese", "Korean", "Chinese (Simplified)", "Arabic", "Russian", "Indonesian", "Turkish",
    )

    private const val OUTPUT_ONLY = "Output only the requested text: no preamble, labels, quotes or commentary."

    /** The system and user prompts for running [skill] on [text]. */
    fun prompts(skill: String, text: String, options: SkillOptions): Pair<String, String> {
        val system = "You are Jade, Rewordium's writing assistant. Follow the instruction exactly and keep the writer's meaning and voice."
        return when (skill) {
            "grammar" -> system to "Proofread the text for grammar, spelling and punctuation. Keep its meaning, voice and formatting. $OUTPUT_ONLY\n\nText:\n$text"
            "summarize" -> system to "Summarize the text concisely. Keep key facts, names, numbers and decisions. Use a few short bullets when that reads better.\n\nText:\n$text"
            "translate" -> system to "Translate the text into ${options.language}. Preserve tone, meaning and formatting. $OUTPUT_ONLY\n\nText:\n$text"
            "tone" -> system to "Rewrite the text in a ${options.tone.lowercase()} tone. Keep the same meaning and don't add facts. $OUTPUT_ONLY\n\nText:\n$text"
            "humanize" -> system to "Revise this writing so it sounds natural, specific and like a real person wrote it. Preserve the meaning, facts, intent and voice. Remove formulaic phrasing and filler. Don't add errors, invent details, or discuss AI detection. $OUTPUT_ONLY\n\nText:\n$text"
            "polish" -> system to "Improve this document for clarity, flow and correctness while preserving its facts and structure. $OUTPUT_ONLY\n\nDocument:\n$text"
            "detector" -> system to "Assess the writing below for patterns commonly associated with AI-generated prose. Never claim certainty. Give a short estimate, explain the strongest signals in a few bullets, and remind the reader this is not proof of authorship.\n\nText:\n$text"
            else -> system to "Rewrite the text so it reads naturally and clearly while keeping the original meaning. $OUTPUT_ONLY\n\nText:\n$text"
        }
    }
}
