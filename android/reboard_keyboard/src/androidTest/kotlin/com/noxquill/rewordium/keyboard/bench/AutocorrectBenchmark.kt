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
import com.noxquill.rewordium.keyboard.ime.nlp.engine.KeyboardLayoutDescriptor
import com.noxquill.rewordium.keyboard.ime.nlp.engine.TypingProximity
import com.noxquill.rewordium.keyboard.ime.nlp.latin.LatinLanguageProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Random
import kotlin.math.hypot

/**
 * Objective autocorrect benchmark over the production decoder (AOSP typing
 * decoder + Rewordium's correction policy), run on a device or emulator.
 *
 * Typing is simulated, not hand-picked: for every letter of an intended word
 * a finger lands at the key's centre plus Gaussian noise on a phone QWERTY,
 * and the key under that point is what gets typed; on top of that, words get
 * occasional transpositions, dropped letters and doubled letters. Every tap
 * is recorded with its coordinates, exactly as the keyboard does, so the
 * spatial model sees what it would see on a phone. The seed is fixed, so runs
 * are comparable.
 *
 * Results go to logcat (tag AutocorrectBench) and to
 * files/autocorrect_bench.txt in the test app's external files directory.
 */
@RunWith(AndroidJUnit4::class)
class AutocorrectBenchmark {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private class Stats {
        var typoWords = 0
        var typoFixed = 0
        var typoLeft = 0
        var typoWrong = 0
        var typoTop3 = 0
        var cleanWords = 0
        var cleanChanged = 0
        val nanos = ArrayList<Long>()
        val examples = ArrayList<String>()
    }

    @Test
    fun benchmark() = runBlocking {
        assertTrue("keyboard engine must start", ReboardSettingsBridge.awaitReady(context))
        val layout = TypingProximity.useStandardLayoutForTesting()
        assertTrue("native decoder must load", layout != null)
        val provider = LatinLanguageProvider(context)
        provider.preload(Subtype.DEFAULT)
        val dictionaryInfo = "native dictionary: ${provider.nativeDictionary.loadedUnigrams} words, ${provider.nativeDictionary.loadedBigrams} word pairs"
        val assets = instrumentation.context.assets
        fun lines(name: String) = assets.open("bench/$name").bufferedReader().readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

        val report = StringBuilder()
        report.appendLine(dictionaryInfo)
        // Word-pair context only, then with the two- and three-word contexts; the same noise both times.
        for (longContext in listOf(false, true)) {
            provider.longContextRanking = longContext
            report.appendLine()
            report.appendLine("######## " + if (longContext) "word pairs + 3-/4-gram contexts" else "word pairs only")
            val typing = Stats()
            val random = Random(20261029)
            for (sentence in lines("sentences.txt")) {
                var text = ""
                for ((index, intended) in sentence.split(' ').withIndex()) {
                    val (typed, taps) = typeWithNoise(intended, layout!!, random)
                    taps.forEach { (code, x, y) -> TypingProximity.recordTap(code, x, y) }
                    val final = decide(provider, text, typed, typing)
                    val top3 = suggestions(provider, text, typed).take(3)
                    if (typed == intended) {
                        typing.cleanWords++
                        if (final != intended) {
                            typing.cleanChanged++
                            if (typing.examples.size < 60) typing.examples += "FALSE  '$intended' -> '$final' ranked=${ranking(provider, text, intended)}"
                        }
                    } else {
                        typing.typoWords++
                        when (final) {
                            intended -> typing.typoFixed++
                            typed -> {
                                typing.typoLeft++
                                if (typing.examples.size < 60) typing.examples += "LEFT   '$intended' typed '$typed' top3=$top3 decoder=${rawDecode(provider, text, typed)}"
                            }
                            else -> {
                                typing.typoWrong++
                                if (typing.examples.size < 60) typing.examples += "WRONG  '$intended' typed '$typed' -> '$final' ranked=${ranking(provider, text, typed)}"
                            }
                        }
                        if (intended in top3) typing.typoTop3++
                    }
                    text += (if (index == 0) "" else " ") + final
                }
            }
            report.appendLine("== Noisy typing (${typing.typoWords + typing.cleanWords} words, ${typing.typoWords} with typos)")
            report.appendLine(String.format("typo words corrected:      %5.1f%%  (%d/%d)", pct(typing.typoFixed, typing.typoWords), typing.typoFixed, typing.typoWords))
            report.appendLine(String.format("typo words left as typed:  %5.1f%%", pct(typing.typoLeft, typing.typoWords)))
            report.appendLine(String.format("typo words changed wrongly:%5.1f%%", pct(typing.typoWrong, typing.typoWords)))
            report.appendLine(String.format("intended word in top 3:    %5.1f%%", pct(typing.typoTop3, typing.typoWords)))
            report.appendLine(String.format("false corrections:         %5.2f%%  (%d of %d correctly typed words)", pct(typing.cleanChanged, typing.cleanWords), typing.cleanChanged, typing.cleanWords))
            val total = typing.typoWords + typing.cleanWords
            val right = typing.typoFixed + (typing.cleanWords - typing.cleanChanged)
            report.appendLine(String.format("word accuracy after autocorrect: %5.1f%%  (raw typing %5.1f%%)", pct(right, total), pct(typing.cleanWords, total)))
            report.appendLine(latency(typing.nanos))

            // Real-word errors: the typo is itself a word; only context can tell.
            val real = Stats()
            for (line in lines("realword.txt")) {
                val (prefix, typed, intended) = line.split('|')
                centreTaps(typed, layout!!)
                val final = decide(provider, prefix, typed, real)
                val top3 = suggestions(provider, prefix, typed).take(3)
                if (typed == intended) {
                    real.cleanWords++
                    if (final != intended) { real.cleanChanged++; real.examples += "FALSE  $prefix [$typed] -> '$final'" }
                } else {
                    real.typoWords++
                    when (final) {
                        intended -> real.typoFixed++
                        typed -> real.typoLeft++
                        else -> { real.typoWrong++; real.examples += "WRONG  $prefix [$typed] want '$intended' -> '$final' ranked=${ranking(provider, prefix, typed)}" }
                    }
                    if (intended in top3) real.typoTop3++ else real.examples += "MISS3  $prefix [$typed] want '$intended' top3=$top3 decoder=${rawDecode(provider, prefix, typed)}"
                }
            }
            report.appendLine()
            report.appendLine("== Context cases (${real.typoWords} typos, ${real.cleanWords} controls)")
            report.appendLine(String.format("corrected: %5.1f%%   intended in top 3: %5.1f%%   wrong: %d   controls changed: %d", pct(real.typoFixed, real.typoWords), pct(real.typoTop3, real.typoWords), real.typoWrong, real.cleanChanged))

            // Words that must be kept as typed.
            val keep = Stats()
            for (line in lines("preserve.txt")) {
                val (prefix, word) = line.split('|')
                centreTaps(word, layout!!)
                val final = decide(provider, prefix, word, keep)
                keep.cleanWords++
                if (final != word) { keep.cleanChanged++; keep.examples += "CHANGED  $prefix [$word] -> '$final'" }
            }
            report.appendLine()
            report.appendLine("== Names, brands and slang typed correctly (${keep.cleanWords})")
            report.appendLine(String.format("changed by autocorrect: %d (%5.1f%%)", keep.cleanChanged, pct(keep.cleanChanged, keep.cleanWords)))

            report.appendLine()
            report.appendLine("== Examples")
            (typing.examples + real.examples + keep.examples).forEach { report.appendLine(it) }
        }

        val out = report.toString()
        out.lines().forEach { Log.i("AutocorrectBench", it) }
        File(context.getExternalFilesDir(null), "autocorrect_bench.txt").writeText(out)
    }

    /** What the keyboard commits when a space is typed after [typed] (autocorrected or as typed). */
    private fun decide(provider: LatinLanguageProvider, before: String, typed: String, stats: Stats): String {
        val prefix = if (before.isEmpty()) "" else "$before "
        val content = contentFor(prefix, typed)
        val start = System.nanoTime()
        val correction = provider.autocorrectFor(content)
        stats.nanos += System.nanoTime() - start
        return correction?.text?.toString() ?: typed
    }

    private suspend fun suggestions(provider: LatinLanguageProvider, before: String, typed: String): List<String> {
        val prefix = if (before.isEmpty()) "" else "$before "
        return provider.suggest(Subtype.DEFAULT, contentFor(prefix, typed), 8, false, false).map { it.text.toString() }
    }

    /** The decoder's raw candidates with scores, for diagnosing context cases. */
    private fun rawDecode(provider: LatinLanguageProvider, prefix: String, typed: String): String {
        val prev = prefix.trim().substringAfterLast(' ')
        return TypingProximity.withCoordinates(typed) { handle, xs, ys ->
            provider.nativeDictionary.suggestForTyping(handle, typed, xs, ys, prev, false, 6)
        }?.joinToString { "${it.word}:${it.score}/${"%.2f".format(it.normalizedScore)}" } ?: "-"
    }

    private fun ranking(provider: LatinLanguageProvider, before: String, typed: String): String {
        val prefix = if (before.isEmpty()) "" else "$before "
        return provider.debugRanking(contentFor(prefix, typed)).take(5).joinToString { "${it.first}:${"%.1f".format(it.second)}" }
    }

    private fun contentFor(prefix: String, typed: String): EditorContent {
        val text = prefix + typed
        val word = EditorRange(prefix.length, text.length)
        return EditorContent(text, 0, EditorRange.cursor(text.length), word, word)
    }

    /** Types [intended] like a hurried thumb: Gaussian landing noise plus occasional slips. */
    private fun typeWithNoise(intended: String, layout: KeyboardLayoutDescriptor, random: Random): Pair<String, List<Triple<Int, Float, Float>>> {
        if (intended.any { !it.isLetter() }) return intended to emptyList()
        val chars = ArrayList<Char>()
        val taps = ArrayList<Triple<Int, Float, Float>>()
        for ((i, ch) in intended.withIndex()) {
            if (intended.length > 3 && i > 0 && random.nextDouble() < 0.012) continue // dropped letter
            val repeat = if (random.nextDouble() < 0.008) 2 else 1 // doubled letter
            repeat(repeat) {
                val center = layout.centerOf(ch.code) ?: return intended to emptyList()
                val x = center.first + random.nextGaussian() * layout.mostCommonKeyWidth * 0.21
                val y = center.second + random.nextGaussian() * layout.mostCommonKeyHeight * 0.20
                val hit = keyAt(layout, x, y)
                val typedChar = if (ch.isUpperCase()) hit.uppercaseChar() else hit
                chars += typedChar
                taps += Triple(typedChar.code, x.toFloat(), y.toFloat())
            }
        }
        if (chars.size >= 3 && random.nextDouble() < 0.015) { // transposition
            val i = 1 + random.nextInt(chars.size - 2)
            chars[i] = chars[i + 1].also { chars[i + 1] = chars[i] }
            taps[i] = taps[i + 1].also { taps[i + 1] = taps[i] }
        }
        return chars.joinToString("") to taps
    }

    private fun centreTaps(word: String, layout: KeyboardLayoutDescriptor) {
        for (ch in word) {
            val c = layout.centerOf(ch.code) ?: continue
            TypingProximity.recordTap(ch.code, c.first.toFloat(), c.second.toFloat())
        }
    }

    /** The letter key nearest to a touch point (what a real tap resolves to). */
    private fun keyAt(layout: KeyboardLayoutDescriptor, x: Double, y: Double): Char {
        var best = 'a'
        var bestD = Double.MAX_VALUE
        for (i in 0 until layout.keyCount) {
            val code = layout.keyCharCodes[i]
            if (code !in 'a'.code..'z'.code) continue
            val cx = layout.keyXCoordinates[i] + layout.keyWidths[i] / 2.0
            val cy = layout.keyYCoordinates[i] + layout.keyHeights[i] / 2.0
            val d = hypot(x - cx, y - cy)
            if (d < bestD) { bestD = d; best = code.toChar() }
        }
        return best
    }

    private fun pct(a: Int, b: Int) = if (b == 0) 0.0 else 100.0 * a / b

    private fun latency(nanos: List<Long>): String {
        if (nanos.isEmpty()) return "latency: n/a"
        val sorted = nanos.sorted()
        val mean = sorted.average() / 1e6
        val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)] / 1e6
        return String.format("autocorrect decision latency: mean %.2f ms, p95 %.2f ms (n=%d)", mean, p95, sorted.size)
    }
}
