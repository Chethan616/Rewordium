/*
 * Copyright (C) 2025-2026 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.ime.text.gestures

import android.content.Context
import androidx.collection.LruCache
import com.noxquill.rewordium.keyboard.BuildConfig
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.core.Subtype
import com.noxquill.rewordium.keyboard.ime.keyboard.KeyData
import com.noxquill.rewordium.keyboard.ime.text.key.KeyCode
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKey
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import com.noxquill.rewordium.keyboard.nlpManager
import java.text.Normalizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Classifies gestures by comparing them with the "ideal gesture" of each
 * candidate word (the polyline through its key centres) — SHARK²-style
 * shape + location channels combined with a unigram/bigram language model.
 *
 * Search runs in stages so a ~170k-word vocabulary stays fast and accurate:
 *  1. first/last-key pruning (6 nearest keys to the gesture's endpoints),
 *  2. a relative path-length gate,
 *  3. a cheap 10-point location + frequency score over all survivors,
 *  4. full 120-point shape/location scoring of the best [COARSE_KEEP].
 *
 * Offline simulation over the shipped dictionary (see tools/) showed the
 * old "keep the 256 most frequent survivors" cap discarded the right word
 * in ~19% of swipes once the vocabulary grew; the staged search lifts top-3
 * recall from ~80% to ~95%.
 *
 * Check out Étienne Desticourt's write up at https://github.com/AnySoftKeyboard/AnySoftKeyboard/pull/1870
 */
class StatisticalGlideTypingClassifier(context: Context) : GlideTypingClassifier {
    private val nlpManager by context.nlpManager()
    private val editorInstance by context.editorInstance()

    private val gesture = Gesture()

    /** Immutable snapshot of everything the decoder needs; swapped atomically. */
    private class Index(
        val subtype: Subtype,
        val layout: Layout,
        val tree: HashMap<Long, ArrayList<String>>,
        val frequencies: Map<String, Double>,
        val templates: LruCache<String, Array<Template>>,
    )

    /** Key geometry captured at [setLayout] time (TextKey bounds are mutable). */
    private class Layout(
        val codes: IntArray,
        val centerXs: FloatArray,
        val centerYs: FloatArray,
        val keyWidths: FloatArray,
        val keyHeights: FloatArray,
        val radius: Float,
        val signature: Int,
    ) {
        private val indexByCode = HashMap<Int, Int>(codes.size * 2).also { map ->
            for (i in codes.indices) map[codes[i]] = i
        }

        fun indexOf(char: Char): Int {
            val lower = Character.toLowerCase(char)
            indexByCode[lower.code]?.let { return it }
            // Accented letters fall back to their base key ("é" -> "e").
            val base = Normalizer.normalize(lower.toString(), Normalizer.Form.NFD).firstOrNull() ?: return -1
            return indexByCode[base.code] ?: -1
        }
    }

    /** Resampled ideal gesture of a word, in absolute and box-normalised form. */
    private class Template(
        val xs: FloatArray,
        val ys: FloatArray,
        val nxs: FloatArray,
        val nys: FloatArray,
        val length: Float,
    )

    @Volatile private var index: Index? = null
    @Volatile private var pendingLayout: Layout? = null
    @Volatile private var pendingSubtype: Subtype? = null
    private val buildGeneration = AtomicInteger(0)
    private val builder = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GlideIndexBuilder").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }

    override val ready: Boolean
        get() {
            val idx = index ?: return false
            return idx.subtype == pendingSubtype && idx.layout.signature == pendingLayout?.signature
        }

    /** The minimum distance between points to be added to a gesture. */
    @Volatile private var distanceThresholdSquared = 0f

    // Language-model weights (see [scoreLanguageModel]); variables only so the glide benchmark can compare them.
    @androidx.annotation.VisibleForTesting internal var unigramMode = UnigramMode.LOG_OF_LOG
    @androidx.annotation.VisibleForTesting internal var unigramWeight = FREQUENCY_WEIGHT
    @androidx.annotation.VisibleForTesting internal var bigramWeight = BIGRAM_WEIGHT
    /** Benchmarks: the previous word to use instead of the editor's ("" = sentence start). */
    @androidx.annotation.VisibleForTesting internal var previousWordForTesting: String? = null

    /** How a word's dictionary frequency enters the score. */
    internal enum class UnigramMode {
        /** ln of the 0..255 log-scale frequency: a deliberately weak prior (the long-standing behaviour). */
        LOG_OF_LOG,

        /** The calibrated log-probability ([BigramModel.DEFAULT_F_PER_NAT] frequency steps per nat). */
        CALIBRATED,
    }

    companion object {
        /** Number of evenly spaced points gestures are resampled to before comparison. */
        private const val SAMPLING_POINTS = 120

        /** Points of the cheap pre-scoring pass. */
        private const val COARSE_POINTS = 10

        /** Candidates kept after the cheap pass for full scoring. */
        private const val COARSE_KEEP = 200

        /** Spread of the cheap pass' mean location error, in key widths. */
        private const val COARSE_STD = 0.6f

        /**
         * Standard deviation of the distance between the shapes of two gestures
         * representing the same word (normalised gestures, keyboard independent).
         */
        private const val SHAPE_STD = 22.08f

        /**
         * Standard deviation of the location distance between two gestures of
         * the same word, as a factor of the key size.
         */
        private const val LOCATION_STD = 0.46f

        /** Reference fast swipe velocity (px/ms) for velocity-aware LOCATION_STD widening. */
        private const val V_MAX_REFERENCE_PX_PER_MS = 8.0f

        /** Max widening of LOCATION_STD for fast gestures (0.5 = +50%). */
        private const val VELOCITY_STD_GAIN = 0.5f

        /** Fraction of the resampled gesture after which points are down-weighted when the lift was fast. */
        private const val TAIL_FRACTION_START = 0.88f

        /** Weight of the softened tail points. */
        private const val TAIL_WEIGHT = 0.35f

        /** Terminal velocity (px/ms) above which the user lifted mid-flight (path truncated). */
        private const val V_FAST_FINISH_PX_PER_MS = 1.5f

        /** Relative path-length gate: user length must lie in [L·(1-lo) - slack, L·(1+hi) + slack]. */
        private const val LENGTH_GATE_SHORTER = 0.40f
        private const val LENGTH_GATE_LONGER = 0.45f
        private const val LENGTH_GATE_SLACK_KEYS = 1.5f

        /** Weight of the unigram log-frequency (AOSP log scale, 1..255) in the score. */
        private const val FREQUENCY_WEIGHT = 1.0

        /**
         * All-caps dictionary entries ("HD", "OK", "USA") are rarely what a
         * glide means; without this "he" loses to "HD" on similar paths.
         */
        private const val ALL_CAPS_PENALTY = 1.0

        /**
         * Weight of the previous-word bonus (the pair's lift in nats, see LatinLanguageProvider.bigramBonuses).
         * Measured on 2,000 real swipes from the swipe.futo.org test split: top-1 83.3% without context,
         * 84.3% at 0.15, 82.3% at 0.3 (RealSwipeBenchmark); 0.15 was also best on simulated sloppy swipes.
         */
        private const val BIGRAM_WEIGHT = 0.15

        /** Slight preference for the plain form over an apostrophe form on exact ties. */
        private const val APOSTROPHE_PENALTY = 0.05

        /**
         * Where a glide starts and lifts is its most deliberate part, while
         * the resampled channels weigh every point equally. This extra
         * Gaussian on the first/last touch vs. the word's first/last key
         * separates "off"/"of", "made"/"mad", "hits"/"his" (simulated
         * top-1 85.8% -> 92.0%, top-3 96.3% -> 99.3%).
         */
        private const val ENDPOINT_WEIGHT = 0.5
        private const val ENDPOINT_STD_KEYS = 0.8f

        private const val SUGGESTION_CACHE_SIZE = 128
        private const val TEMPLATE_CACHE_SIZE = 2048

        private fun pairKey(first: Int, last: Int): Long = (first.toLong() shl 32) or (last.toLong() and 0xffffffffL)
    }

    override fun addGesturePoint(position: GlideTypingGesture.Detector.Position) {
        val t = if (position.t != 0L) position.t else System.currentTimeMillis()
        if (!gesture.isEmpty) {
            val dx = gesture.getLastX() - position.x
            val dy = gesture.getLastY() - position.y
            if (dx * dx + dy * dy > distanceThresholdSquared) {
                gesture.addPoint(position.x, position.y, t)
            }
        } else {
            gesture.addPoint(position.x, position.y, t)
        }
    }

    override fun setLayout(keyViews: List<TextKey>, subtype: Subtype) {
        val layout = captureLayout(keyViews) ?: return
        val current = pendingLayout
        if (current != null && current.signature == layout.signature && pendingSubtype == subtype) {
            return
        }
        pendingLayout = layout
        pendingSubtype = subtype
        distanceThresholdSquared = (layout.radius / 8f).let { it * it }
        scheduleRebuild(force = false)
    }

    override fun setWordData(subtype: Subtype, force: Boolean) {
        if (!force && pendingSubtype == subtype && (index?.subtype == subtype)) return
        pendingSubtype = subtype
        scheduleRebuild(force)
    }

    /**
     * (Re)builds the search index off the UI thread. Keyboard layout changes
     * only invalidate the geometry-dependent template cache; vocabulary
     * changes (subtype switch, learned words) re-snapshot the word list.
     */
    private fun scheduleRebuild(force: Boolean) {
        val generation = buildGeneration.incrementAndGet()
        builder.execute {
            if (generation != buildGeneration.get()) return@execute
            val subtype = pendingSubtype ?: return@execute
            val layout = pendingLayout ?: return@execute
            try {
                val previous = index
                val reuseWords = !force && previous != null && previous.subtype == subtype
                val words: List<String>
                val frequencies: Map<String, Double>
                if (reuseWords) {
                    words = emptyList()
                    frequencies = previous!!.frequencies
                } else {
                    words = nlpManager.getListOfWords(subtype)
                    frequencies = nlpManager.getFrequencyMap(subtype)
                }
                val tree = if (reuseWords && previous!!.layout.codes.contentEquals(layout.codes)) {
                    previous.tree
                } else {
                    buildTree(if (reuseWords) previous!!.tree.values.flatten() else words, layout)
                }
                if (generation != buildGeneration.get()) return@execute
                index = Index(subtype, layout, tree, frequencies, LruCache(TEMPLATE_CACHE_SIZE))
                lruSuggestionCache.evictAll()
                flogDebug { "Glide index built: ${tree.values.sumOf { it.size }} words" }
            } catch (e: Exception) {
                flogDebug { "Glide index build failed: $e" }
            }
        }
    }

    private fun captureLayout(keyViews: List<TextKey>): Layout? {
        val letters = keyViews.filter { key ->
            val code = (key.data as? KeyData)?.code ?: KeyCode.UNSPECIFIED
            code > KeyCode.SPACE && Character.isLetter(code) &&
                key.visibleBounds.width > 0f && key.visibleBounds.height > 0f
        }
        if (letters.isEmpty()) return null
        val n = letters.size
        val codes = IntArray(n)
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val ws = FloatArray(n)
        val hs = FloatArray(n)
        var signature = n
        for ((i, key) in letters.withIndex()) {
            val bounds = key.visibleBounds
            codes[i] = Character.toLowerCase((key.data as KeyData).code)
            xs[i] = bounds.left + bounds.width / 2f
            ys[i] = bounds.top + bounds.height / 2f
            ws[i] = bounds.width
            hs[i] = bounds.height
            signature = 31 * signature + codes[i]
            signature = 31 * signature + xs[i].toInt()
            signature = 31 * signature + ys[i].toInt()
            signature = 31 * signature + ws[i].toInt()
        }
        val first = letters.first().visibleBounds
        val radius = min(first.width, first.height)
        return Layout(codes, xs, ys, ws, hs, radius, signature)
    }

    private fun buildTree(words: List<String>, layout: Layout): HashMap<Long, ArrayList<String>> {
        val tree = HashMap<Long, ArrayList<String>>(1024)
        for (word in words) {
            var first = -1
            var last = -1
            for (c in word) {
                val idx = layout.indexOf(c)
                if (idx < 0) continue
                if (first < 0) first = idx
                last = idx
            }
            if (first < 0) continue
            tree.getOrPut(pairKey(layout.codes[first], layout.codes[last])) { ArrayList() }.add(word)
        }
        return tree
    }

    override fun initGestureFromPointerData(pointerData: GlideTypingGesture.Detector.PointerData) {
        for (position in pointerData.positions) {
            addGesturePoint(position)
        }
    }

    private val lruSuggestionCache = LruCache<Pair<Gesture, Int>, List<String>>(SUGGESTION_CACHE_SIZE)

    override fun getSuggestions(maxSuggestionCount: Int, gestureCompleted: Boolean): List<String> {
        val cacheKey = Pair(this.gesture, maxSuggestionCount)
        lruSuggestionCache.get(cacheKey)?.let { return it }
        val suggestions = unCachedGetSuggestions(maxSuggestionCount)
        lruSuggestionCache.put(Pair(this.gesture.clone(), maxSuggestionCount), suggestions)
        return suggestions
    }

    private fun unCachedGetSuggestions(maxSuggestionCount: Int): List<String> {
        val idx = index ?: return emptyList()
        if (gesture.pointCount == 0) return emptyList()
        val layout = idx.layout
        val radius = layout.radius
        if (radius <= 0f) return emptyList()

        // 1. First/last key pruning.
        val startKeys = nearestKeys(layout, gesture.getFirstX(), gesture.getFirstY(), 6)
        val endKeys = nearestKeys(layout, gesture.getLastX(), gesture.getLastY(), 6)
        val userLength = gesture.getLength()
        val slack = LENGTH_GATE_SLACK_KEYS * radius
        val survivors = ArrayList<String>(512)
        for (s in startKeys) {
            for (e in endKeys) {
                val bucket = idx.tree[pairKey(layout.codes[s], layout.codes[e])] ?: continue
                for (word in bucket) {
                    // 2. Relative path-length gate.
                    val ideal = idealLength(word, layout)
                    if (userLength >= ideal * (1f - LENGTH_GATE_SHORTER) - slack &&
                        userLength <= ideal * (1f + LENGTH_GATE_LONGER) + slack
                    ) {
                        survivors.add(word)
                    }
                }
            }
        }
        if (survivors.isEmpty()) return emptyList()

        // Optionally simplify the raw gesture with Douglas-Peucker before resampling.
        val smoothed = if (BuildConfig.ENABLE_PATH_SMOOTHER) {
            GesturePathSmoother.simplify(gesture, epsilon = 2.0f)
        } else {
            gesture
        }

        // 3. Cheap 10-point location + frequency pass.
        val finalists = if (survivors.size <= COARSE_KEEP) {
            survivors
        } else {
            val ux = FloatArray(COARSE_POINTS)
            val uy = FloatArray(COARSE_POINTS)
            resampleInto(smoothed, COARSE_POINTS, ux, uy)
            val scores = DoubleArray(survivors.size)
            val tx = FloatArray(COARSE_POINTS)
            val ty = FloatArray(COARSE_POINTS)
            val coarseDenominator = 2.0 * COARSE_STD * COARSE_STD
            for ((i, word) in survivors.withIndex()) {
                idealPolylineResample(word, layout, COARSE_POINTS, tx, ty)
                var sum = 0.0
                for (p in 0 until COARSE_POINTS) {
                    val dx = tx[p] - ux[p]
                    val dy = ty[p] - uy[p]
                    sum += sqrt((dx * dx + dy * dy).toDouble())
                }
                val d = sum / COARSE_POINTS / radius
                scores[i] = -(d * d) / coarseDenominator + unigramWeight * lnFrequency(idx, word)
            }
            survivors.indices.sortedByDescending { scores[it] }.take(COARSE_KEEP).map { survivors[it] }
        }

        // 4. Full scoring.
        val userXs = FloatArray(SAMPLING_POINTS)
        val userYs = FloatArray(SAMPLING_POINTS)
        resampleInto(smoothed, SAMPLING_POINTS, userXs, userYs)
        val userNxs = FloatArray(SAMPLING_POINTS)
        val userNys = FloatArray(SAMPLING_POINTS)
        normalizeInto(userXs, userYs, userNxs, userNys)

        // Velocity-aware LOCATION_STD: fast swipes have lower positional certainty.
        val effectiveLocationStd = if (BuildConfig.ENABLE_VELOCITY_AWARE_GESTURE) {
            val velocityFactor = (gesture.peakVelocity() / V_MAX_REFERENCE_PX_PER_MS).coerceIn(0f, 1f)
            LOCATION_STD * (1f + velocityFactor * VELOCITY_STD_GAIN)
        } else {
            LOCATION_STD
        }
        val locationStdPx = effectiveLocationStd * radius
        // Tail softening only when the user lifted mid-flight (path truncated).
        val softenTail = BuildConfig.ENABLE_GESTURE_ENDPOINT_TOLERANCE &&
            gesture.terminalVelocity() > V_FAST_FINISH_PX_PER_MS
        val bigrams = previousWord()?.let { nlpManager.getBigramBonuses(idx.subtype, it) } ?: emptyMap()
        val startX = gesture.getFirstX()
        val startY = gesture.getFirstY()
        val endX = gesture.getLastX()
        val endY = gesture.getLastY()
        val endpointStdPx = ENDPOINT_STD_KEYS * radius
        val endpointDenominator = 2.0 * endpointStdPx * endpointStdPx

        val best = HashMap<String, Double>(finalists.size * 2)
        for (word in finalists) {
            val lnFreq = lnFrequency(idx, word)
            val bigram = bigrams[word] ?: bigrams[word.lowercase()] ?: 0.0
            val allCaps = word.length >= 2 && word.all { !it.isLetter() || it.isUpperCase() }
            val endpoint = endpointPenalty(word, layout, startX, startY, endX, endY) / endpointDenominator
            for (template in templatesFor(idx, word)) {
                val shape = shapeDistance(template, userNxs, userNys, softenTail)
                val location = locationDistance(template, userXs, userYs, softenTail)
                var score = -(shape * shape) / (2.0 * SHAPE_STD * SHAPE_STD) -
                    (location * location) / (2.0 * locationStdPx * locationStdPx)
                // Note: an explicit "ideal length matches gesture length" bonus
                // was evaluated and lowered accuracy (85.5% -> 82.5% top-1);
                // the location channel already captures it.
                score += unigramWeight * lnFreq
                score += bigramWeight * bigram
                if (word.indexOf('\'') >= 0) score -= APOSTROPHE_PENALTY
                if (allCaps) score -= ALL_CAPS_PENALTY
                score -= ENDPOINT_WEIGHT * endpoint
                val previous = best[word]
                if (previous == null || score > previous) best[word] = score
            }
        }
        return best.entries
            .sortedByDescending { it.value }
            .take(maxSuggestionCount)
            .map { it.key }
    }

    /** Squared distances of the gesture's first/last touch from the word's first/last key centres. */
    private fun endpointPenalty(word: String, layout: Layout, sx: Float, sy: Float, ex: Float, ey: Float): Double {
        var first = -1
        var last = -1
        for (c in word) {
            val i = layout.indexOf(c)
            if (i < 0) continue
            if (first < 0) first = i
            last = i
        }
        if (first < 0) return 0.0
        val dsx = (sx - layout.centerXs[first]).toDouble()
        val dsy = (sy - layout.centerYs[first]).toDouble()
        val dex = (ex - layout.centerXs[last]).toDouble()
        val dey = (ey - layout.centerYs[last]).toDouble()
        return dsx * dsx + dsy * dsy + dex * dex + dey * dey
    }

    private fun lnFrequency(idx: Index, word: String): Double {
        val f = 255.0 * (idx.frequencies[word] ?: 0.0)
        return when (unigramMode) {
            UnigramMode.LOG_OF_LOG -> ln(max(1.0, f))
            UnigramMode.CALIBRATED -> (max(1.0, f) - 255.0) / com.noxquill.rewordium.keyboard.ime.nlp.latin.BigramModel.DEFAULT_F_PER_NAT
        }
    }

    /** Last word before the cursor (context for the bigram model), or null at sentence start. */
    private fun previousWord(): String? {
        previousWordForTesting?.let { return it.ifEmpty { null } }
        val text = editorInstance.activeContent.textBeforeSelection.toString().trimEnd()
        if (text.isEmpty()) return null
        val last = text.last()
        if (last == '.' || last == '!' || last == '?' || last == '\n') return null
        val start = text.lastIndexOf(' ') + 1
        val word = text.substring(start).trim { !it.isLetter() && it != '\'' }
        return word.takeIf { it.isNotEmpty() }
    }

    private fun nearestKeys(layout: Layout, x: Float, y: Float, n: Int): List<Int> {
        return layout.codes.indices
            .sortedBy { i ->
                val dx = layout.centerXs[i] - x
                val dy = layout.centerYs[i] - y
                dx * dx + dy * dy
            }
            .take(n)
    }

    private fun idealLength(word: String, layout: Layout): Float {
        var length = 0f
        var px = Float.NaN
        var py = Float.NaN
        for (c in word) {
            val i = layout.indexOf(c)
            if (i < 0) continue
            val x = layout.centerXs[i]
            val y = layout.centerYs[i]
            if (!px.isNaN()) {
                val dx = x - px
                val dy = y - py
                length += sqrt(dx * dx + dy * dy)
            }
            px = x
            py = y
        }
        return length
    }

    /** Resamples the plain ideal polyline of [word] to [n] points without allocating. */
    private fun idealPolylineResample(word: String, layout: Layout, n: Int, outX: FloatArray, outY: FloatArray) {
        val xs = FloatArray(word.length)
        val ys = FloatArray(word.length)
        var count = 0
        for (c in word) {
            val i = layout.indexOf(c)
            if (i < 0) continue
            xs[count] = layout.centerXs[i]
            ys[count] = layout.centerYs[i]
            count++
        }
        resampleArrays(xs, ys, count, n, outX, outY)
    }

    private fun templatesFor(idx: Index, word: String): Array<Template> {
        idx.templates.get(word)?.let { return it }
        val layout = idx.layout
        val plainX = ArrayList<Float>()
        val plainY = ArrayList<Float>()
        val loopX = ArrayList<Float>()
        val loopY = ArrayList<Float>()
        var hasLoops = false
        var previous = '\u0000'
        for (raw in word) {
            val c = Character.toLowerCase(raw)
            val i = layout.indexOf(c)
            if (i < 0) continue
            val cx = layout.centerXs[i]
            val cy = layout.centerYs[i]
            if (c == previous) {
                // A small loop on the key distinguishes "pool" from "pol", "lull" from "lul".
                val qx = layout.keyWidths[i] / 4f
                val qy = layout.keyHeights[i] / 4f
                loopX += listOf(cx + qx, cx + qx, cx - qx, cx - qx)
                loopY += listOf(cy + qy, cy - qy, cy - qy, cy + qy)
                hasLoops = true
            } else {
                loopX += cx
                loopY += cy
            }
            plainX += cx
            plainY += cy
            previous = c
        }
        val paths = if (hasLoops) listOf(plainX to plainY, loopX to loopY) else listOf(plainX to plainY)
        val result = paths.map { (px, py) ->
            val ax = px.toFloatArray()
            val ay = py.toFloatArray()
            val xs = FloatArray(SAMPLING_POINTS)
            val ys = FloatArray(SAMPLING_POINTS)
            resampleArrays(ax, ay, ax.size, SAMPLING_POINTS, xs, ys)
            val nxs = FloatArray(SAMPLING_POINTS)
            val nys = FloatArray(SAMPLING_POINTS)
            normalizeInto(xs, ys, nxs, nys)
            Template(xs, ys, nxs, nys, polylineLength(ax, ay, ax.size))
        }.toTypedArray()
        idx.templates.put(word, result)
        return result
    }

    private fun shapeDistance(template: Template, userNxs: FloatArray, userNys: FloatArray, softenTail: Boolean): Double {
        val tailStart = (SAMPLING_POINTS * TAIL_FRACTION_START).toInt()
        var weighted = 0.0
        var weights = 0.0
        for (i in 0 until SAMPLING_POINTS) {
            val w = if (softenTail && i >= tailStart) TAIL_WEIGHT.toDouble() else 1.0
            val dx = template.nxs[i] - userNxs[i]
            val dy = template.nys[i] - userNys[i]
            weighted += sqrt((dx * dx + dy * dy).toDouble()) * w
            weights += w
        }
        return weighted * (SAMPLING_POINTS / weights)
    }

    private fun locationDistance(template: Template, userXs: FloatArray, userYs: FloatArray, softenTail: Boolean): Double {
        val tailStart = (SAMPLING_POINTS * TAIL_FRACTION_START).toInt()
        var weighted = 0.0
        var weights = 0.0
        for (i in 0 until SAMPLING_POINTS) {
            val w = if (softenTail && i >= tailStart) TAIL_WEIGHT.toDouble() else 1.0
            weighted += (abs(template.xs[i] - userXs[i]) + abs(template.ys[i] - userYs[i])) * w
            weights += w
        }
        return weighted / weights / 2.0
    }

    override fun clear() {
        gesture.clear()
    }

    /** Benchmarks: uses this key geometry instead of an on-screen keyboard. */
    @androidx.annotation.VisibleForTesting
    internal fun setLayoutForTesting(codes: IntArray, xs: FloatArray, ys: FloatArray, widths: FloatArray, heights: FloatArray, subtype: Subtype) {
        var signature = codes.size
        for (i in codes.indices) signature = 31 * (31 * signature + codes[i]) + xs[i].toInt()
        val layout = Layout(codes, xs, ys, widths, heights, min(widths[0], heights[0]), signature)
        pendingLayout = layout
        pendingSubtype = subtype
        distanceThresholdSquared = (layout.radius / 8f).let { it * it }
        scheduleRebuild(force = true)
    }

    /** Benchmarks: decodes one gesture from scratch (no suggestion cache). */
    @androidx.annotation.VisibleForTesting
    internal fun decodeForTesting(points: List<GlideTypingGesture.Detector.Position>, maxSuggestions: Int): List<String> {
        gesture.clear()
        points.forEach { addGesturePoint(it) }
        lruSuggestionCache.evictAll()
        return unCachedGetSuggestions(maxSuggestions).also { gesture.clear() }
    }

    private fun resampleInto(source: Gesture, n: Int, outX: FloatArray, outY: FloatArray) {
        val count = source.pointCount
        val xs = FloatArray(count) { source.getX(it) }
        val ys = FloatArray(count) { source.getY(it) }
        resampleArrays(xs, ys, count, n, outX, outY)
    }

    class Gesture(
        private val xs: FloatArray = FloatArray(MAX_SIZE),
        private val ys: FloatArray = FloatArray(MAX_SIZE),
        private val ts: LongArray = LongArray(MAX_SIZE),
        private var size: Int = 0,
    ) {
        companion object {
            private const val MAX_SIZE = 500

            fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
                val dx = x1 - x2
                val dy = y1 - y2
                return sqrt(dx * dx + dy * dy)
            }
        }

        val isEmpty: Boolean
            get() = size == 0

        val pointCount: Int
            get() = size

        fun addPoint(x: Float, y: Float) {
            addPoint(x, y, System.currentTimeMillis())
        }

        fun addPoint(x: Float, y: Float, t: Long) {
            if (size >= MAX_SIZE) {
                return
            }
            xs[size] = x
            ys[size] = y
            ts[size] = t
            size += 1
        }

        fun getT(i: Int): Long = if (i in 0 until size) ts[i] else 0L

        /** Velocity (px / ms) at point [i], from the segment [i-1, i]; 0 for the first point. */
        fun velocityAt(i: Int): Float {
            if (i <= 0 || i >= size) return 0f
            val dt = (ts[i] - ts[i - 1]).coerceAtLeast(1L).toFloat()
            val dx = xs[i] - xs[i - 1]
            val dy = ys[i] - ys[i - 1]
            return sqrt(dx * dx + dy * dy) / dt
        }

        /** 95th-percentile per-segment velocity; 0 when fewer than 2 points. */
        fun peakVelocity(): Float {
            if (size < 2) return 0f
            val vs = FloatArray(size - 1)
            for (i in 1 until size) vs[i - 1] = velocityAt(i)
            vs.sort()
            return vs[(vs.size * 0.95f).toInt().coerceAtMost(vs.size - 1)]
        }

        /** Average velocity over the trailing [tailPoints] segments — the speed at lift. */
        fun terminalVelocity(tailPoints: Int = 5): Float {
            if (size < 2) return 0f
            val n = tailPoints.coerceAtMost(size - 1).coerceAtLeast(1)
            var sum = 0f
            for (i in (size - n) until size) sum += velocityAt(i)
            return sum / n
        }

        fun getFirstX(): Float = if (size > 0) xs[0] else 0f
        fun getFirstY(): Float = if (size > 0) ys[0] else 0f
        fun getLastX(): Float = if (size > 0) xs[size - 1] else 0f
        fun getLastY(): Float = if (size > 0) ys[size - 1] else 0f

        fun getLength(): Float = polylineLength(xs, ys, size)

        fun clear() {
            this.size = 0
        }

        fun getX(i: Int): Float = if (i in 0 until size) xs[i] else 0f
        fun getY(i: Int): Float = if (i in 0 until size) ys[i] else 0f

        fun clone(): Gesture {
            return Gesture(xs.clone(), ys.clone(), ts.clone(), size)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Gesture
            if (this.size != other.size) return false
            for (i in 0 until size) {
                if (xs[i] != other.xs[i] || ys[i] != other.ys[i]) return false
            }
            return true
        }

        override fun hashCode(): Int {
            // Only the live prefix: cleared gestures keep stale array contents.
            var result = size
            for (i in 0 until size) {
                result = 31 * result + xs[i].toBits()
                result = 31 * result + ys[i].toBits()
            }
            return result
        }
    }
}

private fun polylineLength(xs: FloatArray, ys: FloatArray, count: Int): Float {
    var length = 0f
    for (i in 1 until count) {
        val dx = xs[i] - xs[i - 1]
        val dy = ys[i] - ys[i - 1]
        length += sqrt(dx * dx + dy * dy)
    }
    return length
}

/**
 * Exact arc-length resampling of the first [count] points of ([xs], [ys]) to
 * [n] points (first and last preserved). The previous implementation could
 * return fewer than [n] points, leaving stale (0, 0) entries that then
 * counted as huge distances in the comparison.
 */
private fun resampleArrays(xs: FloatArray, ys: FloatArray, count: Int, n: Int, outX: FloatArray, outY: FloatArray) {
    if (count <= 0) {
        outX.fill(0f); outY.fill(0f)
        return
    }
    val total = polylineLength(xs, ys, count)
    if (count == 1 || total <= 0f) {
        outX.fill(xs[0]); outY.fill(ys[0])
        return
    }
    val step = total / (n - 1)
    var segment = 0
    var segmentStart = 0f
    var segmentLength = run {
        val dx = xs[1] - xs[0]
        val dy = ys[1] - ys[0]
        sqrt(dx * dx + dy * dy)
    }
    for (p in 0 until n) {
        val target = if (p == n - 1) total else p * step
        while (segment < count - 2 && segmentStart + segmentLength < target) {
            segmentStart += segmentLength
            segment++
            val dx = xs[segment + 1] - xs[segment]
            val dy = ys[segment + 1] - ys[segment]
            segmentLength = sqrt(dx * dx + dy * dy)
        }
        val t = if (segmentLength > 0f) ((target - segmentStart) / segmentLength).coerceIn(0f, 1f) else 0f
        outX[p] = xs[segment] + (xs[segment + 1] - xs[segment]) * t
        outY[p] = ys[segment] + (ys[segment + 1] - ys[segment]) * t
    }
}

/** Scales the gesture into a unit box by its longest side, centred at the origin. */
private fun normalizeInto(xs: FloatArray, ys: FloatArray, outX: FloatArray, outY: FloatArray) {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (i in xs.indices) {
        minX = min(minX, xs[i]); maxX = max(maxX, xs[i])
        minY = min(minY, ys[i]); maxY = max(maxY, ys[i])
    }
    val width = maxX - minX
    val height = maxY - minY
    val longestSide = max(max(width, height), 0.00001f)
    val centroidX = (width / 2 + minX) / longestSide
    val centroidY = (height / 2 + minY) / longestSide
    for (i in xs.indices) {
        outX[i] = xs[i] / longestSide - centroidX
        outY[i] = ys[i] / longestSide - centroidY
    }
}
