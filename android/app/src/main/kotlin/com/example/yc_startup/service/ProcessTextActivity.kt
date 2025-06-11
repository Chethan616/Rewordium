package com.example.yc_startup.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log // <--- THIS IS THE MISSING IMPORT
import android.widget.Toast

/**
 * This is a transparent Activity that handles the ACTION_PROCESS_TEXT intent.
 * It shows up in the text selection menu alongside "Copy" and "Share".
 */
class ProcessTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the selected text from the intent
        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)

        if (selectedText.isNullOrBlank()) {
            Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
        } else {
            // --- THIS IS WHERE YOU DO YOUR AI MAGIC ---
            // For now, we'll just show it in a Toast and log it.
            // You could show a dialog, copy a result to the clipboard, etc.
            val result = "AI processed: $selectedText"
            Log.d("ProcessTextActivity", result)
            Toast.makeText(this, result, Toast.LENGTH_LONG).show()
            // Example:
            // showResultDialog(result)
        }

        // It's crucial to finish() the activity immediately so it disappears.
        finish()
    }
}