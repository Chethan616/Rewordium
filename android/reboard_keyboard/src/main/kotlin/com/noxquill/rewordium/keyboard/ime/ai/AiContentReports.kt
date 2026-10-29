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

package com.noxquill.rewordium.keyboard.ime.ai

import android.content.Context
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Lets people flag an AI result they find offensive or harmful without
 * leaving the keyboard (Google Play's AI-generated content policy). Only the
 * AI's output is sent, never the text the user typed, and the report is an
 * admin-only usage event.
 */
object AiContentReports {
    fun report(context: Context, surface: String, tool: String, result: String) {
        val appContext = context.applicationContext
        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        if (uid == null) {
            Toast.makeText(appContext, "Log in to Rewordium to report a result", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            FirebaseFirestore.getInstance().collection("usage_events").add(
                mapOf(
                    "uid" to uid,
                    "type" to "ai_content_report",
                    "surface" to surface,
                    "tool" to tool,
                    "reason" to "flagged",
                    "reply" to result.take(4_000),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
                .addOnSuccessListener { Toast.makeText(appContext, "Thanks — we’ll review this result", Toast.LENGTH_SHORT).show() }
                .addOnFailureListener { Toast.makeText(appContext, "Couldn’t send the report. Try again.", Toast.LENGTH_SHORT).show() }
        }
    }
}
