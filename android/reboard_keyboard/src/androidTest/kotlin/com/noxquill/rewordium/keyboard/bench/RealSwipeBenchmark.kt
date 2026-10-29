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
import com.noxquill.rewordium.keyboard.ime.text.gestures.GlideTypingGesture
import com.noxquill.rewordium.keyboard.ime.text.gestures.StatisticalGlideTypingClassifier
import com.noxquill.rewordium.keyboard.nlpManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Glide accuracy on *real human swipes*: 2,000 gestures sampled from the
 * held-out test split of the swipe.futo.org dataset (MIT, (c) 2025 FUTO),
 * replayed on the exact QWERTY geometry they were drawn on. Each swipe keeps
 * the previous word of its sentence for the language model.
 *
 * Results go to logcat (tag RealSwipeBench) and files/real_swipe_bench.txt.
 */
@RunWith(AndroidJUnit4::class)
class RealSwipeBenchmark {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private class Swipe(val word: String, val previous: String, val points: List<GlideTypingGesture.Detector.Position>)

    @Test
    fun benchmark() = runBlocking {
        assertTrue(ReboardSettingsBridge.awaitReady(context))
        val nlp by context.nlpManager()
        nlp.preload(Subtype.DEFAULT)
        repeat(120) { if (nlp.getListOfWords(Subtype.DEFAULT).size > 100_000) return@repeat; delay(500) }
        val vocabulary = nlp.getListOfWords(Subtype.DEFAULT).mapTo(HashSet()) { it.lowercase() }

        val lines = instrumentation.context.assets.open("bench/futo_swipes.txt").bufferedReader().readLines()
        val keysLine = lines.first { it.startsWith("# canvas") }.substringAfter("keys: ")
        val keys = keysLine.split(';').map { it.split(',') }
        val classifier = StatisticalGlideTypingClassifier(context)
        classifier.setLayoutForTesting(
            codes = keys.map { it[0][0].code }.toIntArray(),
            xs = keys.map { it[1].toFloat() }.toFloatArray(),
            ys = keys.map { it[2].toFloat() }.toFloatArray(),
            widths = keys.map { it[3].toFloat() * 0.9f }.toFloatArray(),
            heights = keys.map { it[4].toFloat() * 0.85f }.toFloatArray(),
            subtype = Subtype.DEFAULT,
        )
        repeat(240) { if (classifier.ready) return@repeat; delay(500) }
        assertTrue("glide index must build", classifier.ready)

        val swipes = lines.filter { !it.startsWith("#") && it.isNotBlank() }.map { line ->
            val (word, previous, data) = line.split('|', limit = 3)
            val points = data.split(';').map { p ->
                val (x, y, t) = p.split(',')
                GlideTypingGesture.Detector.Position(x.toFloat(), y.toFloat(), t.toLong())
            }
            Swipe(word, previous, points)
        }

        data class Config(val name: String, val mode: StatisticalGlideTypingClassifier.UnigramMode, val unigram: Double, val bigram: Double, val context: Boolean)
        val logOfLog = StatisticalGlideTypingClassifier.UnigramMode.LOG_OF_LOG
        val calibrated = StatisticalGlideTypingClassifier.UnigramMode.CALIBRATED
        val configs = listOf(
            Config("no context (as before)", logOfLog, 1.0, 0.0, false),
            Config("old prior + pairs x0.15", logOfLog, 1.0, 0.15, true),
            Config("old prior + pairs x0.3", logOfLog, 1.0, 0.3, true),
            Config("LM x0.1 (prior + pairs)", calibrated, 0.1, 0.1, true),
            Config("LM x0.2 (prior + pairs)", calibrated, 0.2, 0.2, true),
            Config("LM x0.3 (prior + pairs)", calibrated, 0.3, 0.3, true),
        )
        val inVocab = swipes.count { it.word.lowercase() in vocabulary }
        val report = StringBuilder("== Real swipes (swipe.futo.org test split): ${swipes.size}, of which ${inVocab} in the dictionary\n")
        for (config in configs) {
            classifier.unigramMode = config.mode
            classifier.unigramWeight = config.unigram
            classifier.bigramWeight = config.bigram
            var top1 = 0
            var top3 = 0
            var top1Iv = 0
            var top3Iv = 0
            val nanos = ArrayList<Long>()
            val misses = ArrayList<String>()
            for (swipe in swipes) {
                classifier.previousWordForTesting = if (config.context) swipe.previous else ""
                val start = System.nanoTime()
                val out = classifier.decodeForTesting(swipe.points, 5).map { it.lowercase() }
                nanos += System.nanoTime() - start
                val target = swipe.word.lowercase()
                val rank = out.indexOf(target)
                if (rank == 0) top1++
                if (rank in 0..2) top3++
                if (target in vocabulary) {
                    if (rank == 0) top1Iv++
                    if (rank in 0..2) top3Iv++
                }
                if (rank != 0 && target in vocabulary && misses.size < 30) misses += "  [${swipe.previous}] ${swipe.word} -> ${out.take(3)}"
            }
            nanos.sort()
            report.appendLine(String.format(
                "%-26s top-1 %5.1f%%  top-3 %5.1f%%  | in-dictionary top-1 %5.1f%% top-3 %5.1f%%  | latency mean %.0f ms p95 %.0f ms",
                config.name, 100.0 * top1 / swipes.size, 100.0 * top3 / swipes.size,
                100.0 * top1Iv / inVocab, 100.0 * top3Iv / inVocab,
                nanos.average() / 1e6, nanos[(nanos.size * 0.95).toInt()] / 1e6,
            ))
            if (config === configs.first()) misses.forEach { report.appendLine(it) }
        }
        val out = report.toString()
        out.lines().forEach { Log.i("RealSwipeBench", it) }
        File(context.getExternalFilesDir(null), "real_swipe_bench.txt").writeText(out)
    }
}
