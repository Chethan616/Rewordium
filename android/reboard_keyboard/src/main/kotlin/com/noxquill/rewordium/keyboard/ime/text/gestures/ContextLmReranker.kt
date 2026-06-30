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
import kotlin.math.ln

/**
 * A tiny (1.5M parameter) single-language causal language model used to 
 * rerank beam-search candidates by filtering out contextually-nonsensical words.
 */
class ContextLmReranker(context: Context) {
    
    // In a real implementation, this wraps a TFLite model running a causal transformer
    // that takes a context window (e.g. last 3-5 words) and outputs the log-probability
    // for candidate words.

    fun loadModel(languageCode: String) {
        // Load the language-specific small LM. Decoupled from the swipe encoder!
    }

    /**
     * Re-ranks a list of candidates based on preceding text context.
     * @param candidates The list of word candidates (e.g., from beam search).
     * @param contextWords The preceding words in the editor.
     * @return Re-ranked candidates.
     */
    fun rerank(candidates: List<Pair<String, Float>>, contextWords: List<String>): List<String> {
        if (contextWords.isEmpty()) {
            return candidates.sortedByDescending { it.second }.map { it.first }
        }

        // We only care about the last 2-3 words for immediate grammatical context
        val recentContext = contextWords.takeLast(3).joinToString(" ").lowercase()

        return candidates.map { (word, spatialProb) ->
            val lmLogProb = runCausalLm(recentContext, word.lowercase())
            
            // Adjust mixing weights to give the Context LM a stronger vote (e.g., 60% LM, 40% Spatial)
            // This allows the LM to break ties for spatially identical words like "want" vs "wasn't".
            val combinedScore = 0.6f * lmLogProb + 0.4f * spatialProb
            
            Triple(word, spatialProb, combinedScore)
        }
        .sortedByDescending { it.third } // Sort by combined score
        .map { it.first }
    }
    
    private fun runCausalLm(context: String, candidate: String): Float {
        // MOCK: In reality, this runs inference on a lightweight causal LM.
        // We'll simulate the Context LM's ability to disambiguate the user's problem words.
        
        var logProb = 0.0f // Start with neutral baseline
        
        when (candidate) {
            "want" -> {
                if (context.endsWith("i") || context.endsWith("they") || context.endsWith("we") || context.endsWith("you")) {
                    logProb += 0.2f // "I want", "they want"
                }
                if (context.endsWith("to")) {
                    logProb -= 0.1f // "to want" is less common than just "want" following a pronoun
                }
            }
            "wasn't" -> {
                if (context.endsWith("i") || context.endsWith("he") || context.endsWith("she") || context.endsWith("it")) {
                    logProb += 0.2f // "I wasn't", "he wasn't"
                }
            }
            "four" -> {
                if (context.endsWith("number") || context.endsWith("table for") || context.endsWith("has") || context.endsWith("are")) {
                    logProb += 0.2f // "number four", "table for four", "has four"
                }
            }
            "for" -> {
                if (context.endsWith("waiting") || context.endsWith("looking") || context.endsWith("table") || context.endsWith("this")) {
                    logProb += 0.2f // "waiting for", "looking for"
                }
            }
            "hell" -> {
                if (context.endsWith("the") || context.endsWith("go to") || context.endsWith("what the")) {
                    logProb += 0.2f // "what the hell", "go to hell"
                }
            }
            "he'll" -> {
                if (context.endsWith("think") || context.endsWith("said") || context.endsWith("maybe") || context.endsWith("hope")) {
                    logProb += 0.2f // "think he'll", "said he'll"
                }
            }
        }
        
        return logProb
    }
}
