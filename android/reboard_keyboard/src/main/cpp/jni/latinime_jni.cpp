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

//
// JNI shim for the rewordium_latinime native library. Bridges Kotlin
// (com.noxquill.rewordium.keyboard.ime.nlp.engine.LatinImeNative) to the
// AOSP LatinIME C++ engine that lives under cpp/aosp/native/jni/src/ as a
// git submodule pinned to android-16.0.0_r4.
//
// All JNI entry points are exposed under our own package name so the AOSP
// JNI files (which expose org.android.inputmethod.latin.*) don't need to
// be compiled and our Kotlin side stays in our own namespace.
//
// Dictionary handles are raw pointers to AOSP's DictionaryStructureWith-
// BufferPolicy. They're cast to jlong for transport across the JNI
// boundary; the Kotlin side stores them opaquely and round-trips them
// back on every call. Close() deletes the pointer; the caller MUST NOT
// use the handle afterward.
//

#include <jni.h>
#include <algorithm>
#include <memory>
#include <string>
#include <vector>

#include "defines.h"
#include "dictionary/interface/dictionary_header_structure_policy.h"
#include "dictionary/interface/dictionary_structure_with_buffer_policy.h"
#include "dictionary/property/historical_info.h"
#include "dictionary/property/ngram_context.h"
#include "dictionary/property/ngram_property.h"
#include "dictionary/property/unigram_property.h"
#include "dictionary/structure/dictionary_structure_with_buffer_policy_factory.h"
#include "dictionary/utils/format_utils.h"
#include "suggest/core/dictionary/dictionary.h"
#include "suggest/core/layout/proximity_info.h"
#include "suggest/core/result/suggestion_results.h"
#include "suggest/core/session/dic_traverse_session.h"
#include "suggest/core/suggest_options.h"
#include "utils/int_array_view.h"

namespace {

using ::latinime::CodePointArrayView;
using ::latinime::DicTraverseSession;
using ::latinime::Dictionary;
using ::latinime::DictionaryHeaderStructurePolicy;
using ::latinime::DictionaryStructureWithBufferPolicy;
using ::latinime::DictionaryStructureWithBufferPolicyFactory;
using ::latinime::FormatUtils;
using ::latinime::HistoricalInfo;
using ::latinime::NgramContext;
using ::latinime::NgramProperty;
using ::latinime::ProximityInfo;
using ::latinime::SuggestionResults;
using ::latinime::SuggestOptions;
using ::latinime::UnigramProperty;

// Convert a Java String into a contiguous std::vector<int> of Unicode code
// points. AOSP's APIs all take code-point arrays (not UTF-16) so we expand
// surrogate pairs into single ints here. Returns an empty vector if the
// JNI string fetch fails — callers should bail out on empty.
std::vector<int> JStringToCodePoints(JNIEnv* env, jstring jstr) {
    std::vector<int> out;
    if (jstr == nullptr) return out;
    const jsize utf16Len = env->GetStringLength(jstr);
    if (utf16Len <= 0) return out;
    const jchar* chars = env->GetStringChars(jstr, nullptr);
    if (chars == nullptr) return out;
    out.reserve(static_cast<size_t>(utf16Len));
    for (jsize i = 0; i < utf16Len; ++i) {
        const jchar c = chars[i];
        if (c >= 0xD800 && c <= 0xDBFF && i + 1 < utf16Len) {
            const jchar lo = chars[i + 1];
            if (lo >= 0xDC00 && lo <= 0xDFFF) {
                const int cp = 0x10000 +
                    ((static_cast<int>(c) - 0xD800) << 10) +
                    (static_cast<int>(lo) - 0xDC00);
                out.push_back(cp);
                ++i;
                continue;
            }
        }
        out.push_back(static_cast<int>(c));
    }
    env->ReleaseStringChars(jstr, chars);
    return out;
}

// Phase 5d refactor: the dict handle is now a Dictionary* (the AOSP wrapper
// that holds the underlying StructurePolicy plus the Suggest orchestration).
// Dictionary is what getSuggestions() lives on, so wrapping at open-time is
// what unlocks the native suggest path. Read-only trie walks that previously
// hit the policy directly now go via Dictionary::getDictionaryStructurePolicy().
//
// Returns nullptr on a zero handle (which is how Kotlin signals "open
// failed"). All JNI methods below must null-check before touching the pointer.
Dictionary* DictFromHandle(jlong handle) {
    return reinterpret_cast<Dictionary*>(handle);
}

DictionaryStructureWithBufferPolicy* PolicyFromHandle(jlong handle) {
    Dictionary* dict = DictFromHandle(handle);
    if (dict == nullptr) return nullptr;
    return const_cast<DictionaryStructureWithBufferPolicy*>(
            dict->getDictionaryStructurePolicy());
}

ProximityInfo* ProximityInfoFromHandle(jlong handle) {
    return reinterpret_cast<ProximityInfo*>(handle);
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_noxquill_rewordium_keyboard_ime_nlp_engine_LatinImeNative_helloFromNative(
        JNIEnv* env, jobject /* this */) {
    const std::string greeting = "rewordium_latinime: JNI bridge online";
    return env->NewStringUTF(greeting.c_str());
}

JNIEXPORT jint JNICALL
Java_com_noxquill_rewordium_keyboard_ime_nlp_engine_LatinImeNative_nativeAbiVersion(
        JNIEnv* /* env */, jobject /* this */) {
    // Bumped whenever the JNI surface changes shape (added/removed methods,
    // changed signatures). The Kotlin side asserts against an expected value
    // at load time so we fail fast on a stale .so / .kt mismatch rather than
    // crashing later inside a real call.
    return 5;
}

// ─── ProximityInfo lifecycle (Phase 5d.1) ─────────────────────────────────
//
// Opens an AOSP ProximityInfo from pre-shaped layout arrays produced by
// KeyboardLayoutDescriptor.fromTextKeys() on the Kotlin side. The handle
// stays alive for as long as the layout is current — rebuild on subtype
// switch / shift state / numeric flip. Phase 5d.2's nativeSuggestForGesture
// will consume this handle alongside the dict handle.
//
// Array length invariants (the constructor segfaults if these are wrong):
//   * keyXCoordinates / keyYCoordinates / keyWidths / keyHeights /
//     keyCharCodes  — all length [keyCount]
//   * sweetSpotCenterXs / sweetSpotCenterYs / sweetSpotRadii — length [keyCount]
//   * proximityChars — length [gridWidth × gridHeight × MAX_PROXIMITY_CHARS_SIZE]
//
// All array shapes are enforced on the Kotlin side by KeyboardLayoutDescriptor.

JNIEXPORT jlong JNICALL
Java_com_noxquill_rewordium_keyboard_ime_nlp_engine_LatinImeNative_nativeOpenProximityInfo(
        JNIEnv* env, jobject /* this */,
        jint keyboardWidth, jint keyboardHeight,
        jint gridWidth, jint gridHeight,
        jint mostCommonKeyWidth, jint mostCommonKeyHeight,
        jintArray proximityChars, jint keyCount,
        jintArray keyXCoordinates, jintArray keyYCoordinates,
        jintArray keyWidths, jintArray keyHeights, jintArray keyCharCodes,
        jfloatArray sweetSpotCenterXs, jfloatArray sweetSpotCenterYs,
        jfloatArray sweetSpotRadii) {
    if (keyboardWidth <= 0 || keyboardHeight <= 0 || keyCount <= 0) return 0;
    ProximityInfo* pInfo = new ProximityInfo(env, keyboardWidth, keyboardHeight,
            gridWidth, gridHeight, mostCommonKeyWidth, mostCommonKeyHeight,
            proximityChars, keyCount, keyXCoordinates, keyYCoordinates,
            keyWidths, keyHeights, keyCharCodes,
            sweetSpotCenterXs, sweetSpotCenterYs, sweetSpotRadii);
    return reinterpret_cast<jlong>(pInfo);
}

JNIEXPORT void JNICALL
Java_com_noxquill_rewordium_keyboard_ime_nlp_engine_LatinImeNative_nativeCloseProximityInfo(
        JNIEnv* /* env */, jobject /* this */, jlong handle) {
    ProximityInfo* pInfo = ProximityInfoFromHandle(handle);
    delete pInfo;
}

// ─── In-memory v4 dictionary ─────────────────────────────────────────────
//
// Lifecycle:
//   handle = nativeOpenInMemoryDict()       — allocate empty in-memory v4 dict
//   nativeAddUnigram(handle, "word", prob)  — repeated for each unigram
//   nativeAddBigram(handle, prev, w, prob)  — repeated for each bigram
//   nativeIsValidWord(handle, "word")       — sanity check / spell-check
//   nativeCloseDict(handle)                 — release native memory
//
// We use VERSION_403 (the latest v4 format AOSP supports). v4 lives entirely
// in memory until explicitly flushed to disk — we never call flush(), so the
// dict is rebuilt fresh from our JSON on every IME process restart.

JNIEXPORT jlong JNICALL
Java_com_noxquill_rewordium_keyboard_ime_nlp_engine_LatinImeNative_nativeOpenInMemoryDict(
        JNIEnv* env, jobject /* this */) {
    const std::vector<int> locale;  // unused for our purposes
    DictionaryHeaderStructurePolicy::AttributeMap attributeMap;
    // Public non-template dispatcher: takes a plain int formatVersion, picks
    // the right v4 buffer/policy types internally. 403 = the latest v4
    // format AOSP supports.
    auto policyPtr = DictionaryStructureWithBufferPolicyFactory::newPolicyForOnMemoryDict(
            FormatUtils::VERSION_403, locale, &attributeMap);
    if (policyPtr == nullptr) return 0;
    // Wrap in Dictionary — it owns the policy via unique_ptr move and adds
    // the Suggest+TraverseSession orchestration we need in phase 5d.2.
    Dictionary* dict = new Dictionary(env, std::move(policyPtr));
    return reinterpret_cast<jlong>(dict);
}

JNIEXPORT void JNICALL
Java_com_noxquill_rewordium_keyboard_ime_nlp_engine_LatinImeNative_nativeCloseDict(
        JNIEnv* /* env */, jobject /* this */, jlong handle) {
    Dictionary* dict = DictFromHandle(handle);
    delete dict;  // virtual dtor → unique_ptr drops policy → proper cleanup
}

JNIEXPORT jboolean JNICALL
Java_com_noxquill_rewordium_keyboard_ime_nlp_engine_LatinImeNative_nativeAddUnigram(
        JNIEnv* env, jobject /* this */, jlong handle, jstring word, jint probability) {
    Dictionary* dict = DictFromHandle(handle);
    if (dict == nullptr) return JNI_FALSE;
    const std::vector<int> codePoints = JStringToCodePoints(env, word);
    if (codePoints.empty()) return JNI_FALSE;
    // representsBeginningOfSentence=false, isNotAWord=false, isPossiblyOffensive=false,
    // plus an empty HistoricalInfo since this is a static dict, no learning timestamps.
    const UnigramProperty unigramProperty(
            /* representsBeginningOfSentence */ false,
            /* isNotAWord */ false,
            /* isPossiblyOffensive */ false,
            /* probability */ probability,
            HistoricalInfo());
    const CodePointArrayView view(codePoints.data(), codePoints.size());
    return dict->addUnigramEntry(view, &unigramProperty) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_noxquill_rewordium_keyboard_ime_nlp_engine_LatinImeNative_nativeAddBigram(
        JNIEnv* env, jobject /* this */, jlong handle,
        jstring prevWord, jstring word, jint probability) {
    Dictionary* dict = DictFromHandle(handle);
    if (dict == nullptr) return JNI_FALSE;
    const std::vector<int> prevCp = JStringToCodePoints(env, prevWord);
    const std::vector<int> targetCp = JStringToCodePoints(env, word);
    if (prevCp.empty() || targetCp.empty()) return JNI_FALSE;
    // Single-previous-word NgramContext ctor: (codePoints*, count, isBoS).
    // For bigrams that's exactly the shape we want. The multi-prev variant
    // takes a flat 2D array of [MAX_WORD_LENGTH] rows which isn't a
    // natural fit when each prev word can be a different length.
    const NgramContext context(
            prevCp.data(),
            static_cast<int>(prevCp.size()),
            /* isBeginningOfSentence */ false);
    std::vector<int> targetCopy = targetCp;
    const NgramProperty ngramProperty(
            context, std::move(targetCopy), probability, HistoricalInfo());
    return dict->addNgramEntry(&ngramProperty) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_noxquill_rewordium_keyboard_ime_nlp_engine_LatinImeNative_nativeIsValidWord(
        JNIEnv* env, jobject /* this */, jlong handle, jstring word) {
    DictionaryStructureWithBufferPolicy* policy = PolicyFromHandle(handle);
    if (policy == nullptr) return JNI_FALSE;
    const std::vector<int> codePoints = JStringToCodePoints(env, word);
    if (codePoints.empty()) return JNI_FALSE;
    // getWordId returns NOT_A_WORD_ID for unknown words; we use that as the
    // negative signal. Implementation detail of AOSP — kept fast for the
    // spell-check hot path.
    const CodePointArrayView view(codePoints.data(), codePoints.size());
    const int wordId = policy->getWordId(view, /* forceLowerCaseSearch */ false);
    return wordId != NOT_A_WORD_ID ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_noxquill_rewordium_keyboard_ime_nlp_engine_LatinImeNative_nativeGetUnigramProbability(
        JNIEnv* env, jobject /* this */, jlong handle, jstring word) {
    DictionaryStructureWithBufferPolicy* policy = PolicyFromHandle(handle);
    if (policy == nullptr) return -1;
    const std::vector<int> codePoints = JStringToCodePoints(env, word);
    if (codePoints.empty()) return -1;
    const CodePointArrayView view(codePoints.data(), codePoints.size());
    const int wordId = policy->getWordId(view, /* forceLowerCaseSearch */ false);
    if (wordId == NOT_A_WORD_ID) return -1;
    return policy->getProbability(wordId, NOT_A_WORD_ID);
}

}  // extern "C"  (closes the block opened above for the dict-construction methods)

// ─── Prefix completions (Phase 4c) ──────────────────────────────────────
//
// Walks the trie via getNextWordAndNextToken() to enumerate all words,
// filters by [prefix], computes a combined unigram + bigram probability
// for each match (bigram conditional on [prevWord] if it exists in the
// dict), sorts descending, and returns the top [maxResults] as a String[].
//
// Per-call cost on our ~5k-word dict is in the low-tens-of-ms range — fine
// for the suggest hot path with the existing nlp.suggest() debounce. If
// this becomes a bottleneck later, a Kotlin-side prefix cache keyed by the
// composing prefix collapses repeat scans on extending strokes.
//
// Notable simplification vs Gboard-class suggest: we do NOT do any
// proximity-based spatial correction (typo tolerance). That requires
// AOSP's Suggest + ProximityInfo machinery — a separate, larger
// integration tracked as the follow-up for Phase 4 / Phase 5.

namespace {

// Per-candidate score record used during the trie walk before sorting.
struct CompletionCandidate {
    std::vector<int> codePoints;
    int score;
};

// Convert a code-point vector to a JNI String. Encodes back to UTF-16
// (surrogate pairs for code points ≥ 0x10000) so the Kotlin side receives
// the exact original word.
jstring CodePointsToJString(JNIEnv* env, const std::vector<int>& codePoints) {
    std::vector<jchar> utf16;
    utf16.reserve(codePoints.size() + 4);
    for (int cp : codePoints) {
        if (cp < 0x10000) {
            utf16.push_back(static_cast<jchar>(cp));
        } else {
            const int v = cp - 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 | (v >> 10)));
            utf16.push_back(static_cast<jchar>(0xDC00 | (v & 0x3FF)));
        }
    }
    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
}

// True iff [word] starts with [prefix] in code-point order (case-sensitive;
// the Kotlin caller is responsible for lowering case if needed).
bool HasPrefix(const std::vector<int>& word, const std::vector<int>& prefix) {
    if (prefix.size() > word.size()) return false;
    for (size_t i = 0; i < prefix.size(); ++i) {
        if (word[i] != prefix[i]) return false;
    }
    return true;
}

}  // namespace

extern "C" {

JNIEXPORT jobjectArray JNICALL
Java_com_noxquill_rewordium_keyboard_ime_nlp_engine_LatinImeNative_nativeGetCompletions(
        JNIEnv* env, jobject /* this */, jlong handle,
        jstring prefix, jstring prevWord, jint maxResults) {
    jclass stringClass = env->FindClass("java/lang/String");
    DictionaryStructureWithBufferPolicy* policy = PolicyFromHandle(handle);
    if (policy == nullptr || maxResults <= 0) {
        return env->NewObjectArray(0, stringClass, nullptr);
    }
    const std::vector<int> prefixCp = JStringToCodePoints(env, prefix);
    // Empty prefix is legal — caller may want pure next-word predictions
    // conditional on prevWord. In that case we skip the prefix filter and
    // rely purely on the bigram score.

    // Resolve previous-word context to a WordIdArrayView once; reused for
    // every probability lookup in the iteration loop. Empty prevWord (or
    // prevWord not found in dict) → empty WordIdArrayView, which AOSP
    // treats as "no context", falling back to unigram probability.
    std::vector<int> prevWordIds;
    if (prevWord != nullptr) {
        const std::vector<int> prevCp = JStringToCodePoints(env, prevWord);
        if (!prevCp.empty()) {
            const CodePointArrayView prevView(prevCp.data(), prevCp.size());
            const int prevId = policy->getWordId(prevView, /* lower */ false);
            if (prevId != NOT_A_WORD_ID) {
                prevWordIds.push_back(prevId);
            }
        }
    }
    const ::latinime::WordIdArrayView prevContext(
            prevWordIds.data(), prevWordIds.size());

    // Walk every word in the dict. The iteration interface gives us each
    // word's code-point sequence; we re-resolve its WordId to score it.
    std::vector<CompletionCandidate> candidates;
    candidates.reserve(64);
    int outCodePoints[MAX_WORD_LENGTH];
    int outCount = 0;
    int token = 0;
    do {
        token = policy->getNextWordAndNextToken(token, outCodePoints, &outCount);
        if (outCount <= 0) continue;
        std::vector<int> word(outCodePoints, outCodePoints + outCount);
        if (!prefixCp.empty() && !HasPrefix(word, prefixCp)) continue;
        const CodePointArrayView wordView(word.data(), word.size());
        const int wordId = policy->getWordId(wordView, /* lower */ false);
        if (wordId == NOT_A_WORD_ID) continue;
        const int probability = policy->getProbabilityOfWord(prevContext, wordId);
        if (probability < 0) continue;
        candidates.push_back({std::move(word), probability});
    } while (token != 0);

    // Top-K by score. Partial sort would be ~10% faster for small K but
    // full sort keeps the call site simple and N is bounded by dict size.
    std::sort(candidates.begin(), candidates.end(),
            [](const CompletionCandidate& a, const CompletionCandidate& b) {
                return a.score > b.score;
            });
    const int take = std::min<int>(maxResults, static_cast<int>(candidates.size()));

    jobjectArray result = env->NewObjectArray(take, stringClass, nullptr);
    for (int i = 0; i < take; ++i) {
        jstring s = CodePointsToJString(env, candidates[i].codePoints);
        env->SetObjectArrayElement(result, i, s);
        env->DeleteLocalRef(s);
    }
    return result;
}

// ─── Gesture suggest (Phase 5d.2) ─────────────────────────────────────────
//
// Runs AOSP's full `Suggest` pipeline for a single swipe stroke: builds a
// per-call DicTraverseSession, populates it with the touch trajectory +
// previous-word ngram context + a gesture-typed SuggestOptions, calls
// Dictionary::getSuggestions(), and drains the resulting SuggestionResults
// into a Java String[] sorted by ranked score.
//
// Inputs:
//   * dictHandle  — Dictionary* from nativeOpenInMemoryDict
//   * proxHandle  — ProximityInfo* from nativeOpenProximityInfo
//   * xs/ys/ts    — parallel int arrays of touch (x, y, time) tuples for
//                   the swipe, length = number of sampled points
//   * prevWord    — previous committed word for bigram context; "" if none
//   * maxResults  — cap on the result list (also sizes the AOSP buffers)
//
// Marshaling choices:
//   * We use AOSP's outputSuggestions() drain protocol — allocate Java int
//     arrays, let SuggestionResults populate, then read back. Code points
//     come out as a flat `int[maxResults * MAX_WORD_LENGTH]` zero-terminated
//     per slot; we un-flatten and emit UTF-16.
//   * weight = -1.0f tells AOSP to use the scoring policy's auto-calibrated
//     LM-vs-spatial weight (see Phase 5b investigation).
//   * IS_GESTURE option index = 0; value 1 = gesture mode on.
//
// Per-call DicTraverseSession allocation is wasteful at the microsecond
// scale but correct; pooling sessions per pointerId is a phase-5f
// optimization once everything works end-to-end.

JNIEXPORT jobjectArray JNICALL
Java_com_noxquill_rewordium_keyboard_ime_nlp_engine_LatinImeNative_nativeSuggestForGesture(
        JNIEnv* env, jobject /* this */,
        jlong dictHandle, jlong proxHandle,
        jintArray jxs, jintArray jys, jintArray jts,
        jstring prevWord, jint maxResults) {
    jclass stringClass = env->FindClass("java/lang/String");
    Dictionary* dict = DictFromHandle(dictHandle);
    ProximityInfo* pInfo = ProximityInfoFromHandle(proxHandle);
    if (dict == nullptr || pInfo == nullptr || maxResults <= 0) {
        return env->NewObjectArray(0, stringClass, nullptr);
    }
    const jsize pointCount = env->GetArrayLength(jxs);
    if (pointCount <= 0) {
        return env->NewObjectArray(0, stringClass, nullptr);
    }

    // Marshal touch arrays. pointerIds is a parallel array required by
    // AOSP — single-finger glide uses 0 for every sample.
    std::vector<int> xs(pointCount), ys(pointCount), ts(pointCount);
    std::vector<int> pointerIds(pointCount, 0);
    env->GetIntArrayRegion(jxs, 0, pointCount, xs.data());
    env->GetIntArrayRegion(jys, 0, pointCount, ys.data());
    env->GetIntArrayRegion(jts, 0, pointCount, ts.data());

    // Previous-word ngram context (empty for sentence-initial gestures).
    // NgramContext disallows operator= (DISALLOW_ASSIGNMENT_OPERATOR), so we
    // must copy-initialize via ternary rather than build + assign.
    std::vector<int> prevCp;
    if (prevWord != nullptr) {
        prevCp = JStringToCodePoints(env, prevWord);
    }
    const NgramContext ngramCtx = prevCp.empty()
            ? NgramContext()
            : NgramContext(prevCp.data(),
                    static_cast<int>(prevCp.size()),
                    /* isBeginningOfSentence */ false);

    // SuggestOptions: size the int array generously (16 ints, zero-init
    // except IS_GESTURE). Earlier size=1 risked an out-of-bounds read if
    // AOSP's getBoolOption checks any flag beyond IS_GESTURE — better to
    // over-allocate than crash. All other flags default false.
    int suggestOptionFlags[16] = { 0 };
    suggestOptionFlags[0] = 1;  // IS_GESTURE = true (index 0)
    SuggestOptions suggestOpts(suggestOptionFlags, 16);

    // DicTraverseSession allocated on stack — RAII handles teardown.
    // Locale string is unused by our policy (we don't ship per-locale
    // scoring tables) so empty is fine.
    jstring emptyLocale = env->NewStringUTF("");
    DicTraverseSession session(env, emptyLocale, /* usesLargeCache */ false);
    env->DeleteLocalRef(emptyLocale);
    session.init(dict, &ngramCtx, &suggestOpts);
    // maxSpatialDistance: AOSP-ish heuristic of 2× the typical key width.
    // Guard against div-by-zero when ProximityInfo's most-common-key-width
    // comes back as 0 (degenerate layout).
    const int mostCommon = pInfo->getMostCommonKeyWidth();
    const float maxSpatialDistance = (mostCommon > 0)
            ? static_cast<float>(mostCommon) * 2.0f
            : 80.0f;  // sane fallback
    // CRITICAL: for gesture mode, inputSize is the number of touch SAMPLES
    // in xs/ys/times, NOT the number of typed code points. Passing 0
    // either silently returns no results or causes downstream out-of-bounds
    // reads in AOSP's gesture decoder (was the source of the on-device
    // crash when ENABLE_NATIVE_GLIDE=true).
    session.setupForGetSuggestions(pInfo,
            /* inputCodePoints */ nullptr,
            /* inputSize       */ pointCount,
            xs.data(), ys.data(), ts.data(), pointerIds.data(),
            maxSpatialDistance,
            /* maxPointerCount */ 1);

    // Run the orchestrator. weight=-1.0f → auto-calibrate via scoring policy.
    // inputSize=pointCount here too — same reasoning as above.
    SuggestionResults results(maxResults);
    dict->getSuggestions(pInfo, &session,
            xs.data(), ys.data(), ts.data(), pointerIds.data(),
            /* inputCodePoints */ nullptr,
            /* inputSize       */ pointCount,
            &ngramCtx, &suggestOpts,
            /* weightOfLangModelVsSpatialModel */ -1.0f,
            &results);

    // Drain via the AOSP outputSuggestions protocol (8 output arrays —
    // we only need count + code-points; the rest are sized-and-discarded).
    jintArray outCount = env->NewIntArray(1);
    jintArray outCodePoints = env->NewIntArray(maxResults * MAX_WORD_LENGTH);
    jintArray outScores = env->NewIntArray(maxResults);
    jintArray outSpaceIndices = env->NewIntArray(maxResults);
    jintArray outTypes = env->NewIntArray(maxResults);
    jintArray outConfidence = env->NewIntArray(1);
    jfloatArray outWeight = env->NewFloatArray(1);
    results.outputSuggestions(env, outCount, outCodePoints, outScores,
            outSpaceIndices, outTypes, outConfidence, outWeight);

    int count = 0;
    env->GetIntArrayRegion(outCount, 0, 1, &count);
    if (count < 0) count = 0;
    if (count > maxResults) count = maxResults;

    std::vector<int> codePointsAll(maxResults * MAX_WORD_LENGTH);
    env->GetIntArrayRegion(outCodePoints, 0,
            maxResults * MAX_WORD_LENGTH, codePointsAll.data());

    jobjectArray result = env->NewObjectArray(count, stringClass, nullptr);
    for (int i = 0; i < count; ++i) {
        std::vector<int> word;
        word.reserve(MAX_WORD_LENGTH);
        const int base = i * MAX_WORD_LENGTH;
        for (int j = 0; j < MAX_WORD_LENGTH; ++j) {
            const int cp = codePointsAll[base + j];
            if (cp == 0) break;  // 0 = end-of-word terminator
            word.push_back(cp);
        }
        jstring s = CodePointsToJString(env, word);
        env->SetObjectArrayElement(result, i, s);
        env->DeleteLocalRef(s);
    }

    env->DeleteLocalRef(outCount);
    env->DeleteLocalRef(outCodePoints);
    env->DeleteLocalRef(outScores);
    env->DeleteLocalRef(outSpaceIndices);
    env->DeleteLocalRef(outTypes);
    env->DeleteLocalRef(outConfidence);
    env->DeleteLocalRef(outWeight);

    return result;
}

}  // extern "C"
