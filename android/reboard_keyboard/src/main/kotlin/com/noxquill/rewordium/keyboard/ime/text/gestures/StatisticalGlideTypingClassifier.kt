/*
 * Copyright (C) 2025 The ReBoard Contributors
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
import androidx.collection.SparseArrayCompat
import androidx.collection.set
import com.noxquill.rewordium.keyboard.BuildConfig
import com.noxquill.rewordium.keyboard.ime.core.Subtype
import com.noxquill.rewordium.keyboard.ime.keyboard.KeyData
import com.noxquill.rewordium.keyboard.ime.text.key.KeyCode
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKey
import com.noxquill.rewordium.keyboard.nlpManager
import java.text.Normalizer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

private fun TextKey.baseCode(): Int {
    return (data as? KeyData)?.code ?: KeyCode.UNSPECIFIED
}

/**
 * Classifies gestures by comparing them with an "ideal gesture".
 *
 * Check out Étienne Desticourt's excellent write up at https://github.com/AnySoftKeyboard/AnySoftKeyboard/pull/1870
 */
class StatisticalGlideTypingClassifier(context: Context) : GlideTypingClassifier {
    private val nlpManager by context.nlpManager()

    private val gesture = Gesture()
    private var keysByCharacter: SparseArrayCompat<TextKey> = SparseArrayCompat()
    private var words: List<String> = emptyList()
    // Snapshot of word frequencies loaded once per subtype change so the inner scoring loop
    // can do O(1) map lookups instead of a runBlocking JNI call per candidate.
    private var wordFrequencies: Map<String, Double> = emptyMap()

    /**
     * Per-gesture beam of best-seen-so-far candidates across mid-gesture preview calls.
     * Maps word → best (lowest) confidence value observed. Lower confidence = higher rank.
     * When [BuildConfig.ENABLE_BEAM_SEARCH_GESTURES] is on, [getSuggestions] returns a
     * stabilised ranking from this beam instead of the latest single-pass scoring, which
     * reduces flapping of the top candidate as the user's swipe progresses.
     *
     * Cleared on [clear] (gesture end). Acceptance gate before flipping the flag on by
     * default: beam-ranked top-1 must match current top-1 on >95% of a curated corpus.
     */
    private val beam = HashMap<String, Float>(16)
    private var keys: ArrayList<TextKey> = arrayListOf()
    private lateinit var pruner: Pruner
    private var wordDataSubtype: Subtype? = null
    private var layoutSubtype: Subtype? = null
    private var currentSubtype: Subtype? = null
    val ready: Boolean
        get() = currentSubtype == layoutSubtype && wordDataSubtype == layoutSubtype && wordDataSubtype != null
    private val prunerCache = LruCache<Subtype, Pruner>(PRUNER_CACHE_SIZE)

    /**
     * The minimum distance between points to be added to a gesture.
     */
    private var distanceThresholdSquared = 0

    companion object {
        /**
         * Describes the allowed length variance in a gesture. If a gesture is too long or too short, it is immediately
         * discarded to save cycles.
         */
        private const val PRUNING_LENGTH_THRESHOLD = 8.42

        /**
         * Number of evenly-spaced points to resample a gesture into before comparison.
         * Reduced from 240 → 120 for a 2× speedup in inner-loop distance calculations
         * with negligible accuracy loss at typical Android key sizes (~40 dp).
         */
        private const val SAMPLING_POINTS: Int = 120

        /**
         * Standard deviation of the distribution of distances between the shapes of two gestures
         * representing the same word. It's expressed for normalized gestures and is therefore
         * independent of the keyboard or key size.
         */
        private const val SHAPE_STD = 22.08f

        /**
         * Standard deviation of the distribution of distances between the locations of two gestures
         * representing the same word. It's expressed as a factor of key radius as it's applied to
         * un-normalized gestures and is therefore dependent on the size of the keys/keyboard.
         * Tightened from 0.58 → 0.46 to penalise spatially-offset gestures more aggressively,
         * improving precision for common short words.
         */
        private const val LOCATION_STD = 0.46f

        /**
         * Reference "fast" gesture velocity in px / ms. Used to normalise a gesture's measured
         * 95th-percentile velocity into [0,1] for velocity-aware LOCATION_STD widening.
         * 8 px/ms ≈ a typical fast-but-deliberate Gboard-style swipe on a 1080p phone.
         */
        private const val V_MAX_REFERENCE_PX_PER_MS = 8.0f

        /**
         * Maximum amount by which a fast gesture widens LOCATION_STD (0.5 = +50%). Faster gestures
         * have lower per-point positional certainty, so we score them more leniently.
         */
        private const val VELOCITY_STD_GAIN = 0.5f

        /**
         * Fraction of the resampled gesture's length after which sample points are down-weighted
         * in shape/location distance. 0.88 = the last 12% of points are softened. Only applied
         * when terminal velocity is HIGH (see [V_FAST_FINISH_PX_PER_MS]); a deliberate slow
         * lift gets full-weight tails so that "tomorrow" beats "tomorrow's".
         */
        private const val TAIL_FRACTION_START = 0.88f

        /** Weight applied to sample points after [TAIL_FRACTION_START] when tail-softening is active. */
        private const val TAIL_WEIGHT = 0.35f

        /**
         * Terminal-velocity threshold (px / ms) above which the gesture is considered to have
         * been "lifted mid-flight" — i.e., the user did not decelerate before lifting, so the
         * path was likely truncated. Endpoint tolerance only kicks in above this threshold.
         * Calibrated for ~1080p phones; typical deliberate lifts decelerate well below this.
         */
        private const val V_FAST_FINISH_PX_PER_MS = 1.5f

        /**
         * Length-match bonus: candidates whose ideal path length matches the user's gesture
         * length within [LENGTH_MATCH_FALLOFF_KEYS] key-widths get up to this multiplicative
         * confidence advantage. Direct fix for "tomorrow's" being preferred over "tomorrow":
         * the user's gesture length matches "tomorrow" much more closely.
         */
        private const val LENGTH_MATCH_MAX_BONUS = 1.6f
        private const val LENGTH_MATCH_FALLOFF_KEYS = 1.5f

        /**
         * Prefix-extension multiplier — kept for the disabled experimental promotion path.
         * Length-match bonus + velocity-gated tail-softening replace this in normal operation.
         */
        private const val PREFIX_TIE_MULT = 1.4f

        /**
         * Suggestion LRU cache size. Raised from 48 → 128 for a higher hit rate during rapid
         * multi-word glide sessions; modern devices have ample memory.
         */
        private const val SUGGESTION_CACHE_SIZE = 128

        /**
         * For multiple subtypes, the pruner is cached.
         */
        private const val PRUNER_CACHE_SIZE = 8
    }

    override fun addGesturePoint(position: GlideTypingGesture.Detector.Position) {
        // Fall back to system clock when the position carries no event time (legacy callers).
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
        setWordData(subtype)
        // stop duplicate calls
        if (layoutSubtype == subtype && keys == keyViews) {
            return
        }

        // if only layout changed but not subtype
        val layoutChanged = layoutSubtype == subtype

        keysByCharacter.clear()
        keys.clear()
        keyViews.forEach {
            keysByCharacter[it.baseCode()] = it
            keys.add(it)
        }
        layoutSubtype = subtype
        distanceThresholdSquared = (keyViews.first().visibleBounds.width / 8).toInt()
        distanceThresholdSquared *= distanceThresholdSquared

        if (
            (wordDataSubtype == layoutSubtype)
            || layoutChanged // should force a re-initialize
        ) {
            initializePruner(layoutChanged)
        }
    }

    override fun setWordData(subtype: Subtype, force: Boolean) {
        // Early-exit unless caller explicitly forces a rebuild (used by the
        // adaptive learned-swipe pipeline after enough personal words have
        // accumulated to warrant re-snapshotting the lexicon).
        if (!force && wordDataSubtype == subtype) {
            return
        }

        this.words = nlpManager.getListOfWords(subtype)
        // Load all frequencies up-front so unCachedGetSuggestions can use a local map lookup
        // instead of calling nlpManager.getFrequencyForWord() (a runBlocking call) per candidate.
        this.wordFrequencies = nlpManager.getFrequencyMap(subtype)

        this.wordDataSubtype = subtype
        if (force) {
            // Drop the cached pruner so initializePruner rebuilds with the
            // newly-learned vocabulary instead of returning the stale one.
            prunerCache.remove(subtype)
            lruSuggestionCache.evictAll()
        }
        if (wordDataSubtype == layoutSubtype) {
            initializePruner(invalidateCache = force)
        }
    }

    /**
     * Exists because Pruner requires both word data and layout are initialized,
     * however we don't know what order they're initialized in.
     */
    private fun initializePruner(invalidateCache: Boolean) {
        val currentSubtype = this.layoutSubtype!!
        val cached = when {
            invalidateCache -> null
            else -> prunerCache.get(currentSubtype)
        }
        if (cached == null) {
            this.pruner = Pruner(PRUNING_LENGTH_THRESHOLD, this.words, keysByCharacter)
            prunerCache.put(currentSubtype, this.pruner)
        } else {
            this.pruner = cached
        }
        this.currentSubtype = currentSubtype
    }

    override fun initGestureFromPointerData(pointerData: GlideTypingGesture.Detector.PointerData) {
        for (position in pointerData.positions) {
            addGesturePoint(position)
        }
    }

    private val lruSuggestionCache = LruCache<Pair<Gesture, Int>, List<String>>(SUGGESTION_CACHE_SIZE)
    override fun getSuggestions(maxSuggestionCount: Int, gestureCompleted: Boolean): List<String> {
        return when (val cached = lruSuggestionCache.get(Pair(this.gesture, maxSuggestionCount))) {
            null -> {
                val suggestions = unCachedGetSuggestions(maxSuggestionCount)
                lruSuggestionCache.put(Pair(this.gesture.clone(), maxSuggestionCount), suggestions)

                suggestions
            }
            else -> {
                cached
            }
        }
    }

    private fun unCachedGetSuggestions(maxSuggestionCount: Int): List<String> {
        val candidates = arrayListOf<String>()
        val candidateWeights = arrayListOf<Float>()
        val key = keys.firstOrNull() ?: return listOf()
        val radius = min(key.visibleBounds.height, key.visibleBounds.width)
        val extremityCandidates = pruner.pruneByExtremities(gesture, this.keys)
        // Optionally simplify the raw gesture with Douglas-Peucker before resampling.
        // Removes micro-jitter while preserving directional corners.
        val smoothedGesture = if (BuildConfig.ENABLE_PATH_SMOOTHER) {
            GesturePathSmoother.simplify(gesture, epsilon = 2.0f)
        } else {
            gesture
        }
        val userGesture = smoothedGesture.resample(SAMPLING_POINTS)
        val normalizedUserGesture: Gesture = userGesture.normalizeByBoxSide()
        val lengthCandidates = pruner.pruneByLength(gesture, extremityCandidates, keysByCharacter, keys)
        val remainingWords = if (lengthCandidates.isNotEmpty()) {
            lengthCandidates
        } else {
            extremityCandidates
        }

        // Velocity-aware LOCATION_STD: widen the location distribution for fast gestures
        // so that high-velocity swipes (lower positional certainty) score more leniently.
        // Computed from the raw gesture's 95th-percentile per-segment velocity.
        val effectiveLocationStd = if (BuildConfig.ENABLE_VELOCITY_AWARE_GESTURE) {
            val v = gesture.peakVelocity()
            val velocityFactor = (v / V_MAX_REFERENCE_PX_PER_MS).coerceIn(0f, 1f)
            LOCATION_STD * (1f + velocityFactor * VELOCITY_STD_GAIN)
        } else {
            LOCATION_STD
        }

        // Tail-softening is conditional on terminal velocity: only kicks in when the user
        // lifted mid-flight (high velocity at the end), i.e., the path was truncated.
        // For deliberate decelerated lifts, we score the full path so that exact-length
        // candidates like "tomorrow" aren't beaten by their extensions like "tomorrow's".
        val softenTail = BuildConfig.ENABLE_GESTURE_ENDPOINT_TOLERANCE &&
            gesture.terminalVelocity() > V_FAST_FINISH_PX_PER_MS

        val userPathLength = gesture.getLength()

        for (i in remainingWords.indices) {
            val word = remainingWords[i]
            val idealGestures = Gesture.generateIdealGestures(word, keysByCharacter)

            for (idealGesture in idealGestures) {
                val wordGesture = idealGesture.resample(SAMPLING_POINTS)
                val normalizedGesture: Gesture = wordGesture.normalizeByBoxSide()
                val shapeDistance = calcShapeDistance(normalizedGesture, normalizedUserGesture, softenTail)
                val locationDistance = calcLocationDistance(wordGesture, userGesture, softenTail)
                val shapeProbability = max(0.000001f, calcGaussianProbability(shapeDistance, 0.0f, SHAPE_STD))
                val locationProbability = max(0.000001f, calcGaussianProbability(locationDistance, 0.0f, effectiveLocationStd * radius))
                // Length-aware frequency: when candidates saturate the unigram cap (e.g., "hello"
                // and "hell" are both very common), bias toward the longer one. The dictionary file
                // tops out at byte-range 255, so common words tie on raw frequency — this gentle
                // length factor breaks ties in favor of complete words over their short prefixes.
                val rawFreq = 255f * wordFrequencies.getOrDefault(word, 0.0).toFloat()
                val lengthBonus = if (word.length >= 5) 1f + ((word.length - 4).coerceAtMost(6)) * 0.10f else 1f
                // Length-match bonus: reward candidates whose ideal path length matches the
                // user's actual gesture length. Direct fix for "tomorrow → tomorrow's": the
                // longer extension's ideal path is too long for the user's actual swipe.
                val idealPathLength = idealGesture.getLength()
                val lengthMismatchKeys = abs(idealPathLength - userPathLength) / radius
                val lengthMatchBonus = 1f + (LENGTH_MATCH_MAX_BONUS - 1f) *
                    exp(-lengthMismatchKeys / LENGTH_MATCH_FALLOFF_KEYS)
                val frequency = max(1f, rawFreq * lengthBonus * lengthMatchBonus)
                val confidence = 1.0f / (shapeProbability * locationProbability * frequency)

                var candidateDistanceSortedIndex = 0
                var duplicateIndex = Int.MAX_VALUE

                while (candidateDistanceSortedIndex < candidateWeights.size
                    && candidateWeights[candidateDistanceSortedIndex] <= confidence
                ) {
                    if (candidates[candidateDistanceSortedIndex].contentEquals(word)) duplicateIndex =
                        candidateDistanceSortedIndex
                    candidateDistanceSortedIndex++
                }
                if (candidateDistanceSortedIndex < maxSuggestionCount && candidateDistanceSortedIndex <= duplicateIndex) {
                    if (duplicateIndex < Int.MAX_VALUE) {
                        candidateWeights.removeAt(duplicateIndex)
                        candidates.removeAt(duplicateIndex)
                    }
                    candidateWeights.add(candidateDistanceSortedIndex, confidence)
                    candidates.add(candidateDistanceSortedIndex, word)
                    if (candidateWeights.size > maxSuggestionCount) {
                        candidateWeights.removeAt(maxSuggestionCount)
                        candidates.removeAt(maxSuggestionCount)
                    }
                }
            }
        }

        if (BuildConfig.ENABLE_GESTURE_PREFIX_BIAS && candidates.size >= 2) {
            // Prefix-extension preference: when the #1 candidate is a strict prefix of another
            // candidate in the top-K, and the longer one's confidence is within PREFIX_TIE_MULT
            // of #1, promote the longer one. This is the fix for "hello" being beaten by "hell"
            // when both saturate the unigram frequency cap and the geometric difference is small.
            promotePrefixExtensions(candidates, candidateWeights)
        }

        if (BuildConfig.ENABLE_BEAM_SEARCH_GESTURES) {
            // Merge this scoring pass into the per-gesture beam (keep best/lowest confidence per word),
            // then return a stabilised ranking. Smooths candidate flapping across mid-gesture previews.
            for (j in candidates.indices) {
                val w = candidates[j]
                val c = candidateWeights[j]
                val existing = beam[w]
                if (existing == null || c < existing) beam[w] = c
            }
            // Cap beam size to avoid unbounded growth on long sessions; keep top-K by confidence.
            if (beam.size > 32) {
                val keepers = beam.entries.sortedBy { it.value }.take(16).map { it.key }.toHashSet()
                val it = beam.entries.iterator()
                while (it.hasNext()) if (!keepers.contains(it.next().key)) it.remove()
            }
            return beam.entries.sortedBy { it.value }.take(maxSuggestionCount).map { it.key }
        }

        return candidates
    }

    /**
     * Walks the scored candidate list and promotes any longer candidate B that strictly extends
     * candidate A (i.e., B starts with A and is longer), provided B's confidence is within
     * [PREFIX_TIE_MULT] of A's. Operates in-place on the parallel lists.
     */
    private fun promotePrefixExtensions(
        candidates: ArrayList<String>,
        weights: ArrayList<Float>,
    ) {
        var i = 0
        while (i < candidates.size - 1) {
            val shorter = candidates[i]
            var bestJ = -1
            var bestJWeight = Float.MAX_VALUE
            for (j in i + 1 until candidates.size) {
                val longer = candidates[j]
                if (longer.length > shorter.length && longer.startsWith(shorter)) {
                    val w = weights[j]
                    if (w <= weights[i] * PREFIX_TIE_MULT && w < bestJWeight) {
                        bestJ = j
                        bestJWeight = w
                    }
                }
            }
            if (bestJ >= 0) {
                val tmpW = weights[i]; weights[i] = weights[bestJ]; weights[bestJ] = tmpW
                val tmpC = candidates[i]; candidates[i] = candidates[bestJ]; candidates[bestJ] = tmpC
            }
            i++
        }
    }

    override fun clear() {
        gesture.clear()
        beam.clear()
    }

    private fun calcLocationDistance(gesture1: Gesture, gesture2: Gesture, softenTail: Boolean): Float {
        if (!softenTail) {
            var totalDistance = 0.0f
            for (i in 0 until SAMPLING_POINTS) {
                val distance = abs(gesture1.getX(i) - gesture2.getX(i)) +
                    abs(gesture1.getY(i) - gesture2.getY(i))
                totalDistance += distance
            }
            return totalDistance / SAMPLING_POINTS / 2
        }
        var weightedSum = 0.0f
        var weightTotal = 0.0f
        val tailStart = (SAMPLING_POINTS * TAIL_FRACTION_START).toInt()
        for (i in 0 until SAMPLING_POINTS) {
            val w = if (i < tailStart) 1.0f else TAIL_WEIGHT
            val distance = abs(gesture1.getX(i) - gesture2.getX(i)) +
                abs(gesture1.getY(i) - gesture2.getY(i))
            weightedSum += distance * w
            weightTotal += w
        }
        return (weightedSum / weightTotal) / 2f
    }

    private fun calcGaussianProbability(value: Float, mean: Float, standardDeviation: Float): Float {
        val factor = 1.0 / (standardDeviation * sqrt(2 * PI))
        val exponent = ((value - mean) / standardDeviation).toDouble().pow(2.0)
        val probability = factor * exp(-1.0 / 2 * exponent)
        return probability.toFloat()
    }

    private fun calcShapeDistance(gesture1: Gesture, gesture2: Gesture, softenTail: Boolean): Float {
        if (!softenTail) {
            var totalDistance = 0.0f
            for (i in 0 until SAMPLING_POINTS) {
                totalDistance += Gesture.distance(
                    gesture1.getX(i), gesture1.getY(i),
                    gesture2.getX(i), gesture2.getY(i),
                )
            }
            return totalDistance
        }
        var weightedSum = 0.0f
        var weightTotal = 0.0f
        val tailStart = (SAMPLING_POINTS * TAIL_FRACTION_START).toInt()
        for (i in 0 until SAMPLING_POINTS) {
            val w = if (i < tailStart) 1.0f else TAIL_WEIGHT
            val d = Gesture.distance(
                gesture1.getX(i), gesture1.getY(i),
                gesture2.getX(i), gesture2.getY(i),
            )
            weightedSum += d * w
            weightTotal += w
        }
        return weightedSum * (SAMPLING_POINTS / weightTotal)
    }

    class Pruner(
        /**
         * The length difference between a user gesture and a word gesture above which a word will
         * be pruned.
         */
        private val lengthThreshold: Double,
        words: List<String>,
        keysByCharacter: SparseArrayCompat<TextKey>,
    ) {

        /** A tree that provides fast access to words based on their first and last letter.  */
        private val wordTree = Collections.synchronizedMap(HashMap<Pair<Int, Int>, ArrayList<String>>())

        /**
         * Finds the words whose start and end letter are closest to the start and end points of the
         * user gesture.
         *
         * @param userGesture The current user gesture.
         * @param keys The keys on the keyboard.
         * @return A list of likely words.
         */
        fun pruneByExtremities(
            userGesture: Gesture,
            keys: Iterable<TextKey>,
        ): ArrayList<String> {
            val remainingWords = ArrayList<String>()
            val startX = userGesture.getFirstX()
            val startY = userGesture.getFirstY()
            val endX = userGesture.getLastX()
            val endY = userGesture.getLastY()
            // Search 6 nearest keys (up from 4) — improves short-word accuracy
            // since the gesture end-point is often slightly offset from the key centre.
            val startKeys = findNClosestKeys(startX, startY, 6, keys)
            val endKeys = findNClosestKeys(endX, endY, 6, keys)
            for (startKey in startKeys) {
                for (endKey in endKeys) {
                    val keyPair = Pair(startKey, endKey)
                    val wordsForKeys = synchronized(wordTree) { wordTree[keyPair] }
                    if (wordsForKeys != null) {
                        remainingWords.addAll(wordsForKeys)
                    }
                }
            }
            return remainingWords
        }

        /**
         * Finds the words whose ideal gesture length is within a certain threshold of the user
         * gesture's length.
         *
         * @param userGesture The current user gesture.
         * @param words A list of words to consider.
         * @return A list of words that remained after pruning the input list by length.
         */
        fun pruneByLength(
            userGesture: Gesture,
            words: ArrayList<String>,
            keysByCharacter: SparseArrayCompat<TextKey>,
            keys: List<TextKey>,
        ): ArrayList<String> {
            val remainingWords = ArrayList<String>()

            val key = keys.firstOrNull() ?: return arrayListOf()
            val radius = min(key.visibleBounds.height, key.visibleBounds.width)
            val userLength = userGesture.getLength()
            // Asymmetric tolerance: when the word's ideal path is LONGER than the user gesture
            // (i.e., they under-shot), allow up to 1.8x the standard threshold. When the word
            // is SHORTER than the user gesture, keep the original threshold. This admits
            // "hello" / "tomorrow" / "because" into candidate scoring even when the user's
            // fast swipe under-shot the final letters.
            val longerTolerance =
                if (BuildConfig.ENABLE_GESTURE_LENGTH_ASYMMETRY) lengthThreshold * 1.8 else lengthThreshold
            val shorterTolerance = lengthThreshold
            for (word in words) {
                val idealGestures = Gesture.generateIdealGestures(word, keysByCharacter)
                for (idealGesture in idealGestures) {
                    val wordIdealLength = getCachedIdealLength(word, idealGesture)
                    val diff = wordIdealLength - userLength
                    val tolerance = if (diff >= 0) longerTolerance else shorterTolerance
                    if (abs(diff) < tolerance * radius) {
                        remainingWords.add(word)
                    }
                }
            }
            return remainingWords
        }

        private val cachedIdealLength = ConcurrentHashMap<String, Float>()
        private fun getCachedIdealLength(word: String, idealGesture: Gesture): Float {
            return cachedIdealLength.getOrPut(word) { idealGesture.getLength() }
        }

        companion object {
            private fun getFirstKeyLastKey(
                word: String,
                keysByCharacter: SparseArrayCompat<TextKey>,
            ): Pair<Int, Int>? {
                val firstLetter = word[0]
                val lastLetter = word[word.length - 1]
                val firstBaseChar = Normalizer.normalize(firstLetter.toString(), Normalizer.Form.NFD)[0]
                val lastBaseChar = Normalizer.normalize(lastLetter.toString(), Normalizer.Form.NFD)[0]
                return when {
                    keysByCharacter.indexOfKey(firstBaseChar.code) < 0 || keysByCharacter.indexOfKey(lastBaseChar.code) < 0 -> {
                        null
                    }
                    else -> {
                        val firstKey = keysByCharacter[firstBaseChar.code]
                        val lastKey = keysByCharacter[lastBaseChar.code]
                        if (firstKey != null && lastKey != null) {
                            firstKey.baseCode() to lastKey.baseCode()
                        } else {
                            null
                        }
                    }
                }
            }

            /**
             * Finds a chosen number of keys closest to a given point on the keyboard.
             *
             * @param x X coordinate of the point.
             * @param y Y coordinate of the point.
             * @param n The number of keys to return.
             * @param keys The keys of the keyboard.
             * @return A list of the n closest keys.
             */
            private fun findNClosestKeys(
                x: Float, y: Float, n: Int, keys: Iterable<TextKey>
            ): Iterable<Int> {
                val keyDistances = HashMap<TextKey, Float>()
                for (key in keys) {
                    val visibleBoundsCenter = key.visibleBounds.center
                    val distance = Gesture.distance(
                        visibleBoundsCenter.x,
                        visibleBoundsCenter.y,
                        x,
                        y
                    )
                    keyDistances[key] = distance
                }

                return keyDistances.entries.sortedWith { c1, c2 -> c1.value.compareTo(c2.value) }.take(n)
                    .map { it.key.baseCode() }
            }
        }

        init {
            synchronized(wordTree) {
                for (word in words) {
                    val keyPair = getFirstKeyLastKey(word, keysByCharacter)
                    keyPair?.let {
                        wordTree.getOrPut(keyPair) { arrayListOf() }.add(word)
                    }
                }
            }
        }
    }

    class Gesture(
        private val xs: FloatArray = FloatArray(MAX_SIZE),
        private val ys: FloatArray = FloatArray(MAX_SIZE),
        private val ts: LongArray = LongArray(MAX_SIZE),
        private var size: Int = 0,
    ) {
        companion object {
            // TODO: Find out optimal max size
            private const val MAX_SIZE = 500

            fun generateIdealGestures(word: String, keysByCharacter: SparseArrayCompat<TextKey>): List<Gesture> {
                val idealGesture = Gesture()
                val idealGestureWithLoops = Gesture()
                var previousLetter = '\u0000'
                var hasLoops = false

                // Add points for each key
                for (c in word) {
                    val lc = Character.toLowerCase(c)
                    var key = keysByCharacter[lc.code]
                    if (key == null) {
                        // Try finding the base character instead, e.g., the "e" key instead of "é"
                        val baseCharacter: Char = Normalizer.normalize(lc.toString(), Normalizer.Form.NFD)[0]
                        key = keysByCharacter[baseCharacter.code]
                        if (key == null) {
                            continue
                        }
                    }
                    val visibleBoundsCenter = key.visibleBounds.center

                    // We adda little loop on  the key for duplicate letters
                    // so that we can differentiate words like pool and poll, lull and lul, etc...
                    if (previousLetter == lc) {
                        // bottom right
                        idealGestureWithLoops.addPoint(
                            visibleBoundsCenter.x + key.visibleBounds.width / 4.0f,
                            visibleBoundsCenter.y + key.visibleBounds.height / 4.0f
                        )
                        // top right
                        idealGestureWithLoops.addPoint(
                            visibleBoundsCenter.x + key.visibleBounds.width / 4.0f,
                            visibleBoundsCenter.y - key.visibleBounds.height / 4.0f
                        )
                        // top left
                        idealGestureWithLoops.addPoint(
                            visibleBoundsCenter.x - key.visibleBounds.width / 4.0f,
                            visibleBoundsCenter.y - key.visibleBounds.height / 4.0f
                        )
                        // bottom left
                        idealGestureWithLoops.addPoint(
                            visibleBoundsCenter.x - key.visibleBounds.width / 4.0f,
                            visibleBoundsCenter.y + key.visibleBounds.height / 4.0f
                        )
                        hasLoops = true

                        idealGesture.addPoint(
                            visibleBoundsCenter.x,
                            visibleBoundsCenter.y
                        )
                    } else {
                        idealGesture.addPoint(
                            visibleBoundsCenter.x,
                            visibleBoundsCenter.y
                        )
                        idealGestureWithLoops.addPoint(
                            visibleBoundsCenter.x,
                            visibleBoundsCenter.y
                        )
                    }
                    previousLetter = lc
                }
                return when (hasLoops) {
                    true -> listOf(idealGesture, idealGestureWithLoops)
                    false -> listOf(idealGesture)
                }
            }

            fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
                return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
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

        /**
         * Velocity (px / ms) at point [i], computed from the segment [i-1, i].
         * Returns 0 for the first point or invalid indices.
         */
        fun velocityAt(i: Int): Float {
            if (i <= 0 || i >= size) return 0f
            val dt = (ts[i] - ts[i - 1]).coerceAtLeast(1L).toFloat()
            val dx = xs[i] - xs[i - 1]
            val dy = ys[i] - ys[i - 1]
            return sqrt(dx * dx + dy * dy) / dt
        }

        /**
         * 95th-percentile per-segment velocity, used to self-normalise V_MAX for
         * velocity-aware location uncertainty scoring. Returns 0 when fewer than 2 points.
         */
        fun peakVelocity(): Float {
            if (size < 2) return 0f
            val vs = FloatArray(size - 1)
            for (i in 1 until size) vs[i - 1] = velocityAt(i)
            vs.sort()
            return vs[(vs.size * 0.95f).toInt().coerceAtMost(vs.size - 1)]
        }

        /**
         * Average velocity (px / ms) over the trailing [tailPoints] segments — the user's speed
         * at the moment of lift. High terminal velocity ⇒ lifted mid-flight (path truncated).
         * Low terminal velocity ⇒ decelerated to a deliberate stop. Returns 0 when fewer
         * than 2 points or when timestamps are unset.
         */
        fun terminalVelocity(tailPoints: Int = 5): Float {
            if (size < 2) return 0f
            val n = tailPoints.coerceAtMost(size - 1).coerceAtLeast(1)
            var sum = 0f
            for (i in (size - n) until size) sum += velocityAt(i)
            return sum / n
        }

        /**
         * Resamples the gesture into a new gesture with the chosen number of points by oversampling
         * it.
         *
         * @param numPoints The number of points that the new gesture will have. Must be superior to
         * the number of points in the current gesture.
         * @return An oversampled copy of the gesture.
         */
        fun resample(numPoints: Int): Gesture {
            val interpointDistance = (getLength() / numPoints)
            val resampledGesture = Gesture()
            resampledGesture.addPoint(xs[0], ys[0], ts[0])
            var lastX = xs[0]
            var lastY = ys[0]
            var newX: Float
            var newY: Float
            var cumulativeError = 0.0f

            // otherwise nothing happens if size is only 1:
            if (this.size == 1) {
                for (i in 0 until SAMPLING_POINTS) {
                    resampledGesture.addPoint(xs[0], ys[0], ts[0])
                }
            }

            for (i in 0 until size - 1) {
                // We calculate the unit vector from the two points we're between in the actual
                // gesture
                var dx = xs[i + 1] - xs[i]
                var dy = ys[i + 1] - ys[i]
                val norm = sqrt(dx.pow(2.0f) + dy.pow(2.0f))
                dx /= norm
                dy /= norm

                // The number of evenly sampled points that fit between the two actual points
                var numNewPoints = norm / interpointDistance

                // The number of point that'd fit between the two actual points is often not round,
                // which means we'll get an increasingly large error as we resample the gesture
                // and round down that number. To compensate for this we keep track of the error
                // and add additional points when it gets too large.
                cumulativeError += numNewPoints - numNewPoints.toInt()
                if (cumulativeError > 1) {
                    numNewPoints = (numNewPoints.toInt() + cumulativeError.toInt()).toFloat()
                    cumulativeError %= 1
                }
                val segStartT = ts[i]
                val segDurationMs = (ts[i + 1] - ts[i]).coerceAtLeast(0L)
                val totalNew = numNewPoints.toInt()
                for (j in 0 until totalNew) {
                    newX = lastX + dx * interpointDistance
                    newY = lastY + dy * interpointDistance
                    lastX = newX
                    lastY = newY
                    val fraction = (j + 1).toFloat() / max(1, totalNew)
                    val newT = segStartT + (segDurationMs * fraction).toLong()
                    resampledGesture.addPoint(newX, newY, newT)
                }
            }
            return resampledGesture
        }

        fun normalizeByBoxSide(): Gesture {
            val normalizedGesture = Gesture()

            var maxX = -1.0f
            var maxY = -1.0f
            var minX = 10000.0f
            var minY = 10000.0f

            for (i in 0 until size) {
                maxX = max(xs[i], maxX)
                maxY = max(ys[i], maxY)
                minX = min(xs[i], minX)
                minY = min(ys[i], minY)
            }

            val width = maxX - minX
            val height = maxY - minY
            val longestSide = max(max(width, height), 0.00001f)

            val centroidX = (width / 2 + minX) / longestSide
            val centroidY = (height / 2 + minY) / longestSide

            for (i in 0 until size) {
                val x = xs[i] / longestSide - centroidX
                val y = ys[i] / longestSide - centroidY
                normalizedGesture.addPoint(x, y, ts[i])
            }

            return normalizedGesture
        }

        fun getFirstX(): Float = xs.getOrElse(0) { 0f }
        fun getFirstY(): Float = ys.getOrElse(0) { 0f }
        fun getLastX(): Float = xs.getOrElse(size - 1) { 0f }
        fun getLastY(): Float = ys.getOrElse(size - 1) { 0f }

        fun getLength(): Float {
            var length = 0f
            for (i in 1 until size) {
                val previousX = xs[i - 1]
                val previousY = ys[i - 1]
                val currentX = xs[i]
                val currentY = ys[i]
                length += distance(previousX, previousY, currentX, currentY)
            }

            return length
        }

        fun clear() {
            this.size = 0
        }

        fun getX(i: Int): Float = xs.getOrElse(i) { 0f }
        fun getY(i: Int): Float = ys.getOrElse(i) { 0f }

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
            var result = xs.contentHashCode()
            result = 31 * result + ys.contentHashCode()
            result = 31 * result + size
            return result
        }
    }
}
