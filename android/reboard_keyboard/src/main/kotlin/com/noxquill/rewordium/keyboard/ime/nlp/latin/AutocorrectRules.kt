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

package com.noxquill.rewordium.keyboard.ime.nlp.latin

/**
 * Deterministic English autocorrect rules that sit in front of the AOSP
 * typing decoder, mirroring AOSP LatinIME's dictionary "whitelist" shortcuts.
 *
 * The decoder already handles most apostrophe omissions ("didnt" → "didn't")
 * on its own, but a handful of forms need an explicit rule:
 *  * apostrophe-less forms that are themselves real (rare) words — "cant",
 *    "wont", "id" — which the decoder would otherwise treat as valid input;
 *  * the pronoun "I" and its contractions, which users type in lowercase;
 *  * classic misspellings where the decoder's top candidate is ambiguous.
 *
 * AOSP's own whitelist also force-corrects plain words ("hid" → "his",
 * "nut" → "but"); those are deliberately left out — correcting valid words
 * is exactly the "autocorrect keeps changing my words" failure users hate.
 */
internal object AutocorrectRules {
    /** Always applied, regardless of context. Keys are lowercase. */
    private val ALWAYS = mapOf(
        "i" to "I",
        "im" to "I'm",
        "i'm" to "I'm",
        "ive" to "I've",
        "i've" to "I've",
        "i'll" to "I'll",
        "i'd" to "I'd",
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
        "didnt" to "didn't",
        "doesnt" to "doesn't",
        "isnt" to "isn't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "arent" to "aren't",
        "havent" to "haven't",
        "hasnt" to "hasn't",
        "hadnt" to "hadn't",
        "couldnt" to "couldn't",
        "wouldnt" to "wouldn't",
        "shouldnt" to "shouldn't",
        "shouldent" to "shouldn't",
        "didint" to "didn't",
        "mustnt" to "mustn't",
        "neednt" to "needn't",
        "aint" to "ain't",
        "youre" to "you're",
        "youve" to "you've",
        "youll" to "you'll",
        "youd" to "you'd",
        "theyre" to "they're",
        "theyve" to "they've",
        "theyll" to "they'll",
        "theyd" to "they'd",
        "weve" to "we've",
        "thats" to "that's",
        "whats" to "what's",
        "whos" to "who's",
        "wheres" to "where's",
        "heres" to "here's",
        "theres" to "there's",
        "hows" to "how's",
        "whens" to "when's",
        "whys" to "why's",
        "itll" to "it'll",
        "itd" to "it'd",
        "thatll" to "that'll",
        "thatd" to "that'd",
        "whatre" to "what're",
        "whatd" to "what'd",
        "whove" to "who've",
        "wholl" to "who'll",
        "whod" to "who'd",
        "howd" to "how'd",
        "whered" to "where'd",
        "yall" to "y'all",
        "maam" to "ma'am",
        "alot" to "a lot",
        "atleast" to "at least",
        "infact" to "in fact",
        "aswell" to "as well",
        "eachother" to "each other",
        "inteh" to "in the",
        "andteh" to "and the",
        "ofthe" to "of the",
        "inthe" to "in the",
        "wierd" to "weird",
        "acomodate" to "accommodate",
        "teh" to "the",
        "adn" to "and",
        "taht" to "that",
        "thier" to "their",
        "recieve" to "receive",
        "recieved" to "received",
        "untill" to "until",
        "occured" to "occurred",
        "definately" to "definitely",
        "seperate" to "separate",
        "tommorow" to "tomorrow",
        "tommorrow" to "tomorrow",
        "goverment" to "government",
        "beleive" to "believe",
        "freind" to "friend",
        "freinds" to "friends",
        "becuase" to "because",
        "beacuse" to "because",
        // Common misspellings where the decoder sees two near-equal fixes or
        // none it is confident about (validated with the host decoder harness).
        "happend" to "happened",
        "peice" to "piece",
        "persue" to "pursue",
        "basicly" to "basically",
        "maintainance" to "maintenance",
        "probly" to "probably",
        "wensday" to "Wednesday",
        "greatful" to "grateful",
        "restaraunt" to "restaurant",
        "restaraunts" to "restaurants",
        "tounge" to "tongue",
        "definatly" to "definitely",
        "definetly" to "definitely",
        "fourty" to "forty",
        "bizzare" to "bizarre",
        "mischevious" to "mischievous",
        "noticible" to "noticeable",
        "occurance" to "occurrence",
        "ommision" to "omission",
        "rember" to "remember",
        "truley" to "truly",
        "vehical" to "vehicle",
        "tyrany" to "tyranny",
        "preceed" to "precede",
        "visable" to "visible",
        "neice" to "niece",
        "accidently" to "accidentally",
        "grammer" to "grammar",
        "suprised" to "surprised",
        "suprise" to "surprise",
        "wellcome" to "welcome",
        "embarassing" to "embarrassing",
        "embarasing" to "embarrassing",
        "jewelery" to "jewelry",
        "potatos" to "potatoes",
        "tomatos" to "tomatoes",
        "seperated" to "separated",
        "sory" to "sorry",
        "hwat" to "what",
    )

    /**
     * Words that are real English on their own but are far more often a
     * missed apostrophe at the start of a sentence ("ill see you" → "I'll
     * see you", "lets go" → "Let's go"). Mid-sentence they stay untouched
     * ("I feel ill", "she lets me", "enter your id").
     */
    private val SENTENCE_START = mapOf(
        "ill" to "I'll",
        "id" to "I'd",
        "lets" to "let's",
        "hes" to "he's",
        "shes" to "she's",
    )

    /** Previous words after which "ill" / "id" are almost always "I'll" / "I'd". */
    private val ILL_AFTER = setOf("and", "but", "so", "then", "think", "guess", "hope", "maybe", "probably", "promise", "said", "ok", "okay", "yes", "yeah", "sure", "if", "because")

    /**
     * Returns the replacement for [typedLower] or null. [previousWord] is the
     * previous word in lowercase ("" when none) and [atSentenceStart] tells
     * whether the word begins a sentence.
     */
    fun replacementFor(typedLower: String, previousWord: String, atSentenceStart: Boolean): String? {
        ALWAYS[typedLower]?.let { return it }
        val contextual = SENTENCE_START[typedLower] ?: return null
        return when {
            atSentenceStart -> contextual
            (typedLower == "ill" || typedLower == "id") && previousWord in ILL_AFTER -> contextual
            else -> null
        }
    }
}
