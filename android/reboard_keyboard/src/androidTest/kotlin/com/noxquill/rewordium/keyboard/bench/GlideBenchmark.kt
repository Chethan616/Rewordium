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
import java.util.Random
import kotlin.math.hypot

/**
 * Glide (swipe) typing benchmark over the production decoder, run on a device
 * or emulator.
 *
 * Gestures are simulated like a thumb draws them: for each letter the finger
 * aims at a point near (not on) the key centre, cuts corners between keys,
 * and the path is a smooth spline sampled at ~60 Hz with jitter and varying
 * speed. Words come from everyday sentences, so each swipe has a real previous
 * word for the language model. The seed is fixed, so configurations are
 * compared on identical gestures.
 *
 * Results go to logcat (tag GlideBench) and files/glide_bench.txt.
 */
@RunWith(AndroidJUnit4::class)
class GlideBenchmark {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    // Phone QWERTY geometry (same as the decoder's generic layout): 108 x 150 px keys.
    private val keyWidth = 108f
    private val keyHeight = 150f
    private val centers: Map<Char, Pair<Float, Float>> = buildMap {
        listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f).forEachIndexed { row, (keys, offset) ->
            keys.forEachIndexed { i, c -> put(c, (offset + i + 0.5f) * keyWidth to (row + 0.5f) * keyHeight) }
        }
    }

    private class Swipe(val word: String, val previous: String, val points: List<GlideTypingGesture.Detector.Position>)

    @Test
    fun benchmark() = runBlocking {
        assertTrue(ReboardSettingsBridge.awaitReady(context))
        val nlp by context.nlpManager()
        nlp.preload(Subtype.DEFAULT)
        repeat(120) { if (nlp.getListOfWords(Subtype.DEFAULT).size > 100_000) return@repeat; delay(500) }
        val classifier = StatisticalGlideTypingClassifier(context)
        val letters = centers.keys.sorted()
        classifier.setLayoutForTesting(
            codes = letters.map { it.code }.toIntArray(),
            xs = letters.map { centers.getValue(it).first }.toFloatArray(),
            ys = letters.map { centers.getValue(it).second }.toFloatArray(),
            widths = FloatArray(letters.size) { keyWidth - 12f },
            heights = FloatArray(letters.size) { keyHeight - 24f },
            subtype = Subtype.DEFAULT,
        )
        repeat(240) { if (classifier.ready) return@repeat; delay(500) }
        assertTrue("glide index must build", classifier.ready)

        val sentences = instrumentation.context.assets.open("bench/sentences.txt").bufferedReader().readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        fun swipesFor(sloppy: Boolean): List<Swipe> {
            val random = Random(if (sloppy) 7L else 20261029L)
            val swipes = ArrayList<Swipe>()
            for (sentence in sentences) {
                var previous = ""
                for (raw in sentence.split(' ')) {
                    val word = raw.lowercase()
                    if (word.length >= 2 && word.all { it in 'a'..'z' }) {
                        swipes += Swipe(word, previous, gesture(word, random, sloppy))
                    }
                    previous = raw
                }
            }
            return swipes
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
        val report = StringBuilder()
        for (sloppy in listOf(false, true)) {
        val swipes = swipesFor(sloppy)
        report.appendLine("== Glide, ${if (sloppy) "sloppy" else "careful"} swipes: ${swipes.size} words of 2+ letters")
        for (config in configs) {
            classifier.unigramMode = config.mode
            classifier.unigramWeight = config.unigram
            classifier.bigramWeight = config.bigram
            var top1 = 0
            var top3 = 0
            var shortTotal = 0
            var shortTop1 = 0
            val nanos = ArrayList<Long>()
            val misses = ArrayList<String>()
            for (swipe in swipes) {
                classifier.previousWordForTesting = if (config.context) swipe.previous else ""
                val start = System.nanoTime()
                val out = classifier.decodeForTesting(swipe.points, 5).map { it.lowercase() }
                nanos += System.nanoTime() - start
                val rank = out.indexOf(swipe.word)
                if (rank == 0) top1++
                if (rank in 0..2) top3++
                if (swipe.word.length <= 3) { shortTotal++; if (rank == 0) shortTop1++ }
                if (rank != 0 && misses.size < 25) misses += "  [${swipe.previous}] ${swipe.word} -> ${out.take(3)}"
            }
            nanos.sort()
            report.appendLine(String.format(
                "%-32s top-1 %5.1f%%  top-3 %5.1f%%  short words top-1 %5.1f%%  latency mean %.0f ms p95 %.0f ms",
                config.name, 100.0 * top1 / swipes.size, 100.0 * top3 / swipes.size, 100.0 * shortTop1 / shortTotal,
                nanos.average() / 1e6, nanos[(nanos.size * 0.95).toInt()] / 1e6,
            ))
        }
        }
        val out = report.toString()
        out.lines().forEach { Log.i("GlideBench", it) }
        File(context.getExternalFilesDir(null), "glide_bench.txt").writeText(out)
    }

    /** A thumb's swipe for [word]: noisy aim points, cut corners, a smooth sampled path. */
    private fun gesture(word: String, random: Random, sloppy: Boolean): List<GlideTypingGesture.Detector.Position> {
        val keys = word.fold(StringBuilder()) { acc, c -> if (acc.isEmpty() || acc.last() != c) acc.append(c) else acc }.toString()
        val aims = keys.mapIndexed { i, c ->
            val (cx, cy) = centers.getValue(c)
            val end = i == 0 || i == keys.length - 1
            val spread = if (sloppy) (if (end) 0.26 else 0.36) else (if (end) 0.18 else 0.24)
            (cx + random.nextGaussian() * spread * keyWidth).toFloat() to (cy + random.nextGaussian() * spread * keyHeight * 0.8).toFloat()
        }.toMutableList()
        // Corner cutting: inner aim points drift toward the straight line between their neighbours.
        for (i in 1 until aims.size - 1) {
            val (px, py) = aims[i - 1]
            val (nx, ny) = aims[i + 1]
            val (x, y) = aims[i]
            val cut = if (sloppy) 0.3f else 0.15f
            aims[i] = (x + ((px + nx) / 2 - x) * cut) to (y + ((py + ny) / 2 - y) * cut)
        }
        val points = ArrayList<GlideTypingGesture.Detector.Position>()
        var t = 0L
        if (aims.size == 1) {
            points += GlideTypingGesture.Detector.Position(aims[0].first, aims[0].second, t)
            return points
        }
        for (seg in 0 until aims.size - 1) {
            val p0 = aims[maxOf(0, seg - 1)]
            val p1 = aims[seg]
            val p2 = aims[seg + 1]
            val p3 = aims[minOf(aims.size - 1, seg + 2)]
            val length = hypot(p2.first - p1.first, p2.second - p1.second)
            val steps = maxOf(3, (length / 14f).toInt())
            val speed = 0.8 + random.nextDouble() * 0.8 // px per ms
            for (s in 0 until steps) {
                val u = s.toFloat() / steps
                val x = catmullRom(p0.first, p1.first, p2.first, p3.first, u) + random.nextGaussian().toFloat() * (if (sloppy) 4f else 2f)
                val y = catmullRom(p0.second, p1.second, p2.second, p3.second, u) + random.nextGaussian().toFloat() * (if (sloppy) 4f else 2f)
                points += GlideTypingGesture.Detector.Position(x, y, t)
                t += ((length / steps) / speed).toLong().coerceAtLeast(1)
            }
        }
        points += GlideTypingGesture.Detector.Position(aims.last().first, aims.last().second, t)
        return points
    }

    private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, u: Float): Float {
        val u2 = u * u
        val u3 = u2 * u
        return 0.5f * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3)
    }
}
