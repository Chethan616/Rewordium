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

package com.noxquill.rewordium.keyboard.bench

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.noxquill.rewordium.keyboard.app.ReboardSettingsBridge
import com.noxquill.rewordium.keyboard.ime.core.Subtype
import com.noxquill.rewordium.keyboard.ime.editor.EditorContent
import com.noxquill.rewordium.keyboard.ime.editor.EditorRange
import com.noxquill.rewordium.keyboard.ime.nlp.latin.LatinLanguageProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Next-word predictions through the provider's real suggestion path, as the
 * strip shows them after a space:
 *  * chained picking - start a sentence and keep tapping the first prediction;
 *    how often does the strip run dry?
 *  * accuracy - for every word of the benchmark sentences, was it the first
 *    prediction / on the strip before it was typed?
 *  * latency per prediction.
 * Held-out accuracy on a large corpus is measured offline by
 * tools/eval_predictions.py; this checks the Kotlin path end to end.
 *
 * Results go to logcat (tag PredictionBench) and files/prediction_bench.txt.
 */
@RunWith(AndroidJUnit4::class)
class PredictionBenchmark {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun benchmark() = runBlocking {
        assertTrue("keyboard engine must start", ReboardSettingsBridge.awaitReady(context))
        val provider = LatinLanguageProvider(context)
        provider.preload(Subtype.DEFAULT)
        val nanos = ArrayList<Long>()

        suspend fun predict(textBefore: String): List<String> {
            val content = EditorContent(textBefore, 0, EditorRange.cursor(textBefore.length), EditorRange.Unspecified, EditorRange.Unspecified)
            val start = System.nanoTime()
            val out = provider.suggest(Subtype.DEFAULT, content, 8, false, false).map { it.text.toString() }
            nanos += System.nanoTime() - start
            return out
        }

        val report = StringBuilder()
        // 1. Chained picking: tap the first prediction 12 times in a row.
        val openings = listOf("I", "We", "Thank you", "Can you", "How", "Let me", "What", "Hope", "Please", "My", "The", "It", "Do you", "Good")
        var steps = 0
        var dry = 0
        for (opening in openings) {
            var text = "$opening "
            repeat(12) {
                val out = predict(text)
                steps++
                if (out.isEmpty()) {
                    dry++
                    return@repeat
                }
                text += out.first() + " "
            }
            report.appendLine("  chain: ${text.trim()}")
        }
        report.insert(0, String.format("== Chained picking: %d predictions, strip empty %d times (%.1f%%)\n", steps, dry, 100.0 * dry / steps))
        val sentenceStart = predict("Hello. ")
        report.appendLine("  after a full stop: $sentenceStart")
        report.appendLine("  empty field: ${predict("")}")
        // Phrases ("know if you") appear second on the strip when the models are confident of every word.
        for (text in listOf("Let me ", "Thank you ", "See you ", "I am going ", "How are ", "Happy ", "What do you ", "I hope you ")) {
            report.appendLine("  '$text' -> ${predict(text).take(4)}")
        }

        // 2. Accuracy on the benchmark sentences.
        val sentences = instrumentation.context.assets.open("bench/sentences.txt").bufferedReader().readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        var words = 0
        var top1 = 0
        var top3 = 0
        var empty = 0
        for (sentence in sentences) {
            val tokens = sentence.split(' ')
            for (i in 1 until tokens.size) {
                val target = tokens[i].trim { !it.isLetter() && it != '\'' }
                if (target.isEmpty() || tokens[i - 1].last() in ".!?") continue
                val out = predict(tokens.subList(0, i).joinToString(" ") + " ").map { it.lowercase() }
                words++
                if (out.isEmpty()) empty++
                if (out.firstOrNull() == target.lowercase()) top1++
                if (target.lowercase() in out.take(3)) top3++
            }
        }
        report.appendLine(String.format(
            "== Benchmark sentences: %d words  top-1 %.1f%%  top-3 %.1f%%  no prediction %.1f%%",
            words, 100.0 * top1 / words, 100.0 * top3 / words, 100.0 * empty / words,
        ))
        nanos.sort()
        report.appendLine(String.format(
            "== Latency per prediction: mean %.2f ms  p95 %.2f ms  max %.2f ms",
            nanos.average() / 1e6, nanos[(nanos.size * 0.95).toInt()] / 1e6, nanos.last() / 1e6,
        ))
        val out = report.toString()
        out.lines().forEach { Log.i("PredictionBench", it) }
        File(context.getExternalFilesDir(null), "prediction_bench.txt").writeText(out)
        assertTrue("predictions must not run dry", dry * 10 < steps)
    }
}
