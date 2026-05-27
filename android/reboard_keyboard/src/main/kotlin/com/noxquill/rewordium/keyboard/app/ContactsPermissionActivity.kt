/*
 * Copyright (C) 2026 The ReBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.noxquill.rewordium.keyboard.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.noxquill.rewordium.keyboard.ime.nlp.engine.ContactsLoader
import kotlinx.coroutines.launch

/**
 * Transparent trampoline activity that presents the contacts-permission
 * rationale and then launches the system READ_CONTACTS dialog.
 *
 * Launched from [CandidatesRow] (smartbar prompt) and from the native
 * Flutter app via MethodChannel "requestContactsPermission". The activity
 * is fully transparent — only the AlertDialog and the system dialog are
 * visible to the user.
 *
 * On grant: sends [ACTION_RELOAD_CONTACTS] broadcast so [FlorisImeService]
 *   can hot-reload contact names without an IME restart.
 * On permanent deny: sets [FlorisPreferenceStore.spelling.contactsPromptDismissed]
 *   so the smartbar prompt stops appearing.
 */
class ContactsPermissionActivity : ComponentActivity() {

    companion object {
        const val ACTION_RELOAD_CONTACTS = "com.noxquill.rewordium.keyboard.RELOAD_CONTACTS"
    }

    private val prefs by FlorisPreferenceStore

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            sendBroadcast(Intent(ACTION_RELOAD_CONTACTS))
        } else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            // User ticked "Don't ask again" — silence the smartbar prompt.
            lifecycleScope.launch { prefs.spelling.contactsPromptDismissed.set(true) }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already granted — broadcast a reload in case contacts changed.
        if (ContactsLoader.hasPermission(this)) {
            sendBroadcast(Intent(ACTION_RELOAD_CONTACTS))
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Use contacts for suggestions")
            .setMessage(
                "Rewordium can suggest names from your contacts while you type " +
                    "and recognise them during swipe input. " +
                    "Your contacts never leave your device.",
            )
            .setPositiveButton("Allow") { _, _ ->
                requestPermission.launch(Manifest.permission.READ_CONTACTS)
            }
            .setNegativeButton("Not now") { _, _ ->
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
