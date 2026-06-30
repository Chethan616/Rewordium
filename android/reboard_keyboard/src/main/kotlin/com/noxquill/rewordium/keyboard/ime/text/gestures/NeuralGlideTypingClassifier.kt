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

package com.noxquill.rewordium.keyboard.ime.text.gestures

import android.content.Context
import com.noxquill.rewordium.keyboard.ime.core.Subtype
import com.noxquill.rewordium.keyboard.ime.nlp.latin.LatinLanguageProvider
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKey
import com.noxquill.rewordium.keyboard.nlpManager
import java.util.PriorityQueue
import kotlin.math.ln

/**
 * Neural layout-agnostic gesture classifier — implements [GlideTypingClassifier].
 */
class NeuralGlideTypingClassifier(context: Context) : GlideTypingClassifier {

    private val nlpManager by context.nlpManager()
    
    // Internal Trie structure for dictionary-constrained beam search
    class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        var isWord: Boolean = false
        var frequency: Float = 0f
    }
    private var root = TrieNode()

    override val ready: Boolean
        get() = true

    private val xs = ArrayList<Float>()
    private val ys = ArrayList<Float>()
    private val ts = ArrayList<Long>()
    private var layoutKeys: List<TextKey> = emptyList()

    override fun addGesturePoint(position: GlideTypingGesture.Detector.Position) {
        val t = if (position.t != 0L) position.t else System.currentTimeMillis()
        xs.add(position.x)
        ys.add(position.y)
        ts.add(t)
    }

    override fun setLayout(keyViews: List<TextKey>, subtype: Subtype) {
        this.layoutKeys = keyViews
    }

    override fun setWordData(subtype: Subtype, force: Boolean) {
        val frequencies = nlpManager.getFrequencyMap(subtype)
        
        // Build Trie
        val newRoot = TrieNode()
        for ((word, freq) in frequencies) {
            var curr = newRoot
            for (char in word) {
                curr = curr.children.getOrPut(char) { TrieNode() }
            }
            curr.isWord = true
            curr.frequency = freq.toFloat()
        }
        root = newRoot
    }

    private val reranker = ContextLmReranker(context)

    override fun initGestureFromPointerData(pointerData: GlideTypingGesture.Detector.PointerData) {
        clear()
        for (position in pointerData.positions) {
            addGesturePoint(position)
        }
    }

    override fun getSuggestions(
        maxSuggestionCount: Int,
        gestureCompleted: Boolean
    ): List<CharSequence> {
        if (xs.isEmpty()) return emptyList()

        val logits = runEncoderInference()
        val beamCandidatesWithProbs = runBeamSearch(logits, maxSuggestionCount * 3) // Get more for reranking
        
        // Fetch context from the active editor via nlpManager/keyboardManager
        // For demonstration, we assume we fetch the last 3 words.
        val contextWords = emptyList<String>() // TODO: Fetch from EditorInstance

        // 3. Rerank the candidates using the tiny causal LM
        val finalCandidates = reranker.rerank(beamCandidatesWithProbs, contextWords)
        
        return finalCandidates.take(maxSuggestionCount)
    }

    override fun clear() {
        xs.clear()
        ys.clear()
        ts.clear()
    }

    private fun runEncoderInference(): Array<FloatArray> {
        // Stub: In a real environment, this invokes the ONNX/TFLite model.
        // Returns sequence of probabilities for each key.
        val seqLen = xs.size
        val numKeys = layoutKeys.size
        // Fake probabilities
        return Array(seqLen) { FloatArray(numKeys) { 1.0f / numKeys } }
    }

    private data class BeamState(
        val prefix: String,
        val node: TrieNode,
        val logProb: Float
    ) : Comparable<BeamState> {
        override fun compareTo(other: BeamState): Int {
            return other.logProb.compareTo(this.logProb) // Descending
        }
    }

    private fun runBeamSearch(logits: Array<FloatArray>, maxResults: Int): List<Pair<String, Float>> {
        val beamWidth = 300
        var beam = listOf(BeamState("", root, 0f))
        
        // Blank index logic: assume index 0 is blank (CTC-style).
        // For simplicity, we just use the max logit as a fallback if no blank is explicit.
        
        for (t in logits.indices) {
            val stepLogits = logits[t]
            val nextBeam = PriorityQueue<BeamState>()
            
            for (state in beam) {
                // 1. Blank transition (stay in same state)
                // In CTC, there's a blank token. Without a real model, we simulate a small penalty.
                val blankLogProb = -0.1f 
                nextBeam.add(BeamState(state.prefix, state.node, state.logProb + blankLogProb))
                
                // 2. Character transition
                for ((i, key) in layoutKeys.withIndex()) {
                    val keyData = key.data as? com.noxquill.rewordium.keyboard.ime.keyboard.KeyData ?: continue
                    val charStr = keyData.label
                    if (charStr.isEmpty()) continue
                    val char = charStr[0]
                    
                    val childNode = state.node.children[char]
                    if (childNode != null) {
                        val prob = stepLogits[i]
                        val logProb = if (prob > 0) ln(prob) else -100f
                        nextBeam.add(BeamState(state.prefix + char, childNode, state.logProb + logProb))
                    }
                }
            }
            
            // Prune to beamWidth
            val sortedNext = mutableListOf<BeamState>()
            while (nextBeam.isNotEmpty() && sortedNext.size < beamWidth) {
                sortedNext.add(nextBeam.poll()!!)
            }
            beam = sortedNext
        }
        
        // Filter to valid words and apply unigram frequency score
        val rawResults = beam.filter { it.node.isWord }
            .map { it.prefix to (it.logProb + ln(it.node.frequency.coerceAtLeast(0.0001f))) }
            
        // Apostrophe penalty heuristic: if a word contains an apostrophe, penalize it slightly 
        // to prefer the non-apostrophe version (e.g. want over wasn't, hell over he'll) unless 
        // the context LM strongly overrides it later.
        val results = rawResults.map { (word, score) ->
            val penalty = if (word.contains("'")) -0.5f else 0f
            word to (score + penalty)
        }
        .sortedByDescending { it.second }
        .distinctBy { it.first }
        .take(maxResults)
            
        return results
    }
}
