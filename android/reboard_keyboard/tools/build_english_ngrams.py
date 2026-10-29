"""Builds the English language models for prediction and in-context ranking:

    ime/dict/bigrams.bin    word pairs            (BigramModel)
    ime/dict/trigrams.bin   two-word contexts     (NgramModel, order 3)
    ime/dict/fourgrams.bin  three-word contexts   (NgramModel, order 4)
    ime/dict/fivegrams.bin  four-word contexts    (NgramModel, order 5)

Usage (each corpus may carry a weight after ":", default 1):
    python tools/build_english_ngrams.py eng_sentences.tsv.bz2:3 swipe-5/sentences/en.txt:3 c4-train.00000-of-01024.json.gz
    python tools/build_english_ngrams.py --holdout --out /tmp/lm ...    (evaluation build, see eval_predictions.py)

Corpora used for the shipped models:
  * Tatoeba English sentences (https://tatoeba.org, CC BY 2.0 FR): ~2M short,
    everyday sentences, close to what people type on a phone. Weight 3.
  * The English sentence lists of the swipe.futo.org dataset (MIT, (c) 2025
    FUTO; drawn from Mozilla Common Voice, CC0): swipe-*/sentences/*.txt. Weight 3.
  * Shards of C4, the cleaned Common Crawl corpus (allenai/c4, ODC-BY 1.0):
    hundreds of millions of words of general web English. Weight 1.
Conversational sources are weighted up so the models keep the phrasing of
messages rather than of web pages.

Scale. Every score is log P on the dictionary's 0..255 frequency scale, so
corpus probabilities compare directly with unigram frequencies. The scale is
fitted, not assumed: least squares of dictionary frequency against corpus
log-probability over all words seen at least MIN_FIT_COUNT times gives

    freq ~= intercept + F_PER_NAT * ln P(word)        (F_PER_NAT ~ 11)

and each n-gram is placed on that line: score = intercept + F_PER_NAT * ln P(w | context).

Sentence start. "<s>" marks the start of a sentence, so (<s>, w) pairs give the
likely first words and (<s>, w1, w2) the likely second words.

Counting. The corpus is tokenized in parallel and counted in chunks; in chunks
of the large web corpus, n-grams seen once are dropped before merging (they
can never reach the stored thresholds in practice), which keeps memory bounded.
Longer models keep their most frequent contexts until a size budget is spent.

bigrams.bin (little-endian varints) - every pair seen at least min_count times,
so an absent pair means "rarer than that" (see BigramModel.contextScore):
    "RBG2"  version:u8 (4)
    total:  ln(corpus tokens) * 1000      scale: F_PER_NAT * 1000
    intercept * 1000                      min_count * 1000
    words:  count, then (byte length, UTF-8, ln(count)*8 as u8) per word
    prevs:  count, then per prev: word index, pair count,
            then per pair: delta-coded next index, score:u8 (nexts ascending)

trigrams.bin / fourgrams.bin / fivegrams.bin - the strongest continuations of
the most frequent contexts:
    "RNG1"  version:u8 (1)  order:u8
    words:  count, then (byte length, UTF-8) per word, sorted
    contexts: count, then per context (sorted by its word indices):
        (order - 1) word indices, n, then n x (next index, score:u8), strongest first
"""
import argparse, bz2, gzip, json, math, os, re, sys
from multiprocessing import Pool
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
DICT = os.path.join(HERE, "..", "src", "main", "assets", "ime", "dict", "data.json")
OUT = os.path.join(HERE, "..", "src", "main", "assets", "ime", "dict")

BIGRAM_MIN_COUNT = 4           # pairs seen fewer (weighted) times are noise
BIGRAM_BUDGET = 1_400_000      # at most this many pairs (the threshold rises until they fit)
MIN_COUNT = {3: 3, 4: 3, 5: 3}                       # continuations seen fewer times are noise
MIN_CONTEXT = {3: 8, 4: 8, 5: 10}                    # contexts seen fewer times carry too little evidence
MAX_PER_CONTEXT = {3: 24, 4: 16, 5: 12}              # strongest continuations kept per context
BUDGET = {3: 1_600_000, 4: 1_300_000, 5: 900_000}    # continuations kept per model, most frequent contexts first
DISCOUNT = 0.5                 # absolute discount on n-gram counts
MIN_FIT_COUNT = 20             # words used to calibrate the frequency scale
NONE, BOS = 0, 1               # stream ids: context break, sentence start
BOS_WORD = "<s>"
ID_BITS = {2: 21, 3: 16, 4: 16, 5: 12}  # word id bits in packed n-gram keys (n ids fit one uint64)
CHUNK_TOKENS = 30_000_000
HOLDOUT_CHAT = 50              # evaluation builds: every 50th conversational sentence is held out
HOLDOUT_WEB = 4000             # ...and every 4000th web sentence

# Proper nouns Tatoeba's contributors use as stock characters and recurring topics. Found by comparing
# each word that is capitalised mid-sentence with its dictionary frequency on the fitted scale: these are
# more than ~45 steps (~60x) more frequent in Tatoeba than in general English and would dominate predictions
# ("Sami", "Tom"). Their sentences still count; the names only break the word context.
STOCK_NAMES = {
    "tom", "mary", "sami", "layla", "ken", "berber", "algeria", "algerian", "algerians", "algiers",
    "gabor", "walid", "adriano", "gunter", "farid", "gustavo", "rodrigo", "janos", "lukas", "fyodor",
    "martino", "dmitri", "oleg", "leonid",
}

TOKEN = re.compile(r"[A-Za-z]+(?:['’][A-Za-z]+)*")
SENTENCE_SPLIT = re.compile(r"[.!?]+|(?=[;:])")


def is_stock_name(raw):
    low = raw.lower()
    return low in STOCK_NAMES or (low.endswith("'s") and low[:-2] in STOCK_NAMES)


def is_web(path):
    return ".json" in path


def sentences(path):
    """Tatoeba's .tsv.bz2 export, C4's .json.gz shards (one document per line), or one sentence per line."""
    if path.endswith(".bz2"):
        with bz2.open(path, "rt", encoding="utf-8") as f:
            for line in f:
                parts = line.rstrip("\n").split("\t")
                if len(parts) >= 3:
                    yield parts[2]
    elif ".json" in path:
        opener = gzip.open if path.endswith(".gz") else open
        with opener(path, "rt", encoding="utf-8") as f:
            for line in f:
                try:
                    text = json.loads(line)["text"]
                except (ValueError, KeyError):
                    continue
                for paragraph in text.split("\n"):
                    paragraph = paragraph.strip()
                    if paragraph:
                        yield paragraph
    else:
        with open(path, encoding="utf-8", errors="ignore") as f:
            for line in f:
                line = line.strip()
                if line:
                    yield line


def canonical_forms(words):
    """lowercase -> dictionary casing (the lowercase form wins when both exist)."""
    canonical = {}
    for w, f in words.items():
        low = w.lower()
        if low in words:
            canonical[low] = low
        elif low not in canonical or words[canonical[low]] < f:
            canonical[low] = w
    return canonical


_canonical = _index = None


def _init(canonical, index):
    global _canonical, _index
    _canonical, _index = canonical, index


def _tokenize(batch):
    """A batch of sentences as one id stream: NONE between clauses and around unusable tokens, BOS at sentence starts."""
    out = []
    append = out.append
    for sentence in batch:
        for clause in SENTENCE_SPLIT.split(sentence):
            append(NONE)
            # A clause after ";" or ":" continues a sentence, so it gets no start marker.
            if not clause.startswith((";", ":")):
                append(BOS)
            for m in TOKEN.finditer(clause):
                raw = m.group(0).replace("’", "'")
                w = None if is_stock_name(raw) else _canonical.get(raw.lower())
                append(NONE if w is None else _index[w])
    append(NONE)
    return np.array(out, dtype=np.int32)


def batches(path, holdout_file, holdout_every, size=20000):
    batch = []
    for k, sentence in enumerate(sentences(path)):
        if holdout_every and k % holdout_every == 0:
            holdout_file.write(sentence.replace("\n", " ") + "\n")
            continue
        batch.append(sentence)
        if len(batch) == size:
            yield batch
            batch = []
    if batch:
        yield batch


def pack(stream, n, bits):
    """Packed n-grams of the stream (no NONE inside)."""
    m = len(stream) - n + 1
    if m <= 0:
        return np.zeros(0, dtype=np.uint64)
    key = np.zeros(m, dtype=np.uint64)
    valid = np.ones(m, dtype=bool)
    for j in range(n):
        part = stream[j:j + m]
        valid &= part > NONE
        key = (key << np.uint64(bits)) | part.astype(np.uint64)
    return key[valid]


def merge(acc, keys, counts):
    """Adds (keys, counts) to the accumulated unique keys and counts."""
    if acc is None:
        return keys, counts
    k = np.concatenate([acc[0], keys])
    c = np.concatenate([acc[1], counts])
    u, inv = np.unique(k, return_inverse=True)
    return u, np.bincount(inv, weights=c).astype(np.float64)


class Counter:
    """Weighted n-gram counts for orders 2..5, accumulated chunk by chunk."""

    def __init__(self, vocab_size, high_ids):
        self.uni = np.zeros(vocab_size, dtype=np.float64)
        self.acc = {n: None for n in ID_BITS}
        self.high_ids = high_ids  # order -> remap array from full ids to that order's ids (0 = breaks)

    def add(self, stream, weight, prune):
        self.uni += np.bincount(stream, minlength=len(self.uni)) * weight
        for n, bits in ID_BITS.items():
            s = stream if n == 2 else self.high_ids[bits][stream]
            keys, counts = np.unique(pack(s, n, bits), return_counts=True)
            if prune:
                keep = counts > 1
                keys, counts = keys[keep], counts[keep]
            self.acc[n] = merge(self.acc[n], keys, counts * float(weight))


def varint(out, n):
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return


def scores_for(counts, ctx_counts, intercept, f_per_nat, weight_floor=1.0):
    p = np.maximum(counts - DISCOUNT * weight_floor, 0.25) / ctx_counts
    return np.clip(np.round(intercept + f_per_nat * np.log(p)), 0, 255).astype(np.int64)


def write_bigrams(path, vocab, uni, total, intercept, f_per_nat, keys, counts):
    min_count = BIGRAM_MIN_COUNT
    while (counts >= min_count).sum() > BIGRAM_BUDGET:
        min_count *= 1.25
    keep = counts >= min_count
    keys, counts = keys[keep], counts[keep]
    prev = (keys >> np.uint64(21)).astype(np.int64)
    nxt = (keys & np.uint64((1 << 21) - 1)).astype(np.int64)
    starts = float(counts[prev == BOS].sum())
    ctx = np.where(prev == BOS, starts, uni[prev])
    scores = scores_for(counts, ctx, intercept, f_per_nat)

    used = np.unique(np.concatenate([prev, nxt]))
    names = [BOS_WORD if i == BOS else vocab[i] for i in used]
    order = sorted(range(len(used)), key=lambda j: names[j])
    out_index = np.zeros(len(vocab), dtype=np.int64)
    for rank, j in enumerate(order):
        out_index[used[j]] = rank
    out = bytearray(b"RBG2")
    out.append(4)
    varint(out, round(math.log(total) * 1000))
    varint(out, round(f_per_nat * 1000))
    varint(out, round(intercept * 1000))
    varint(out, round(min_count * 1000))
    varint(out, len(used))
    for j in order:
        i = used[j]
        data = names[j].encode("utf-8")
        varint(out, len(data))
        out.extend(data)
        count = starts if i == BOS else uni[i]
        out.append(max(0, min(255, round(math.log(max(1.0, count)) * 8))))
    p_out, n_out = out_index[prev], out_index[nxt]
    sort = np.lexsort((n_out, p_out))
    p_out, n_out, scores = p_out[sort], n_out[sort], scores[sort]
    bounds = np.flatnonzero(np.diff(p_out)) + 1
    starts_at = np.r_[0, bounds]
    ends_at = np.r_[bounds, len(p_out)]
    varint(out, len(starts_at))
    for a, b in zip(starts_at.tolist(), ends_at.tolist()):
        varint(out, int(p_out[a]))
        varint(out, b - a)
        last = 0
        for i in range(a, b):
            n = int(n_out[i])
            varint(out, n - last)
            last = n
            out.append(int(scores[i]))
    with open(path, "wb") as f:
        f.write(out)
    print(f"bigrams: {len(p_out):,} pairs (min count {min_count:.1f}) after {len(starts_at):,} words, {len(out) / 1024:.0f} KiB -> {path}")


def write_ngrams(path, order, vocab, high_vocab, keys, counts, ctx_keys, ctx_counts, intercept, f_per_nat):
    bits = ID_BITS[order]
    ctx = keys >> np.uint64(bits)
    where = np.searchsorted(ctx_keys, ctx)
    where = np.minimum(where, len(ctx_keys) - 1)
    n_ctx = np.where(ctx_keys[where] == ctx, ctx_counts[where], 0.0)
    keep = (counts >= MIN_COUNT[order]) & (n_ctx >= MIN_CONTEXT[order])
    keys, counts, ctx, n_ctx = keys[keep], counts[keep], ctx[keep], n_ctx[keep]
    scores = scores_for(counts, n_ctx, intercept, f_per_nat)
    # Strongest continuations first within each context; keep the top few.
    sort = np.lexsort((-counts, -scores, ctx))
    keys, counts, ctx, scores, n_ctx = keys[sort], counts[sort], ctx[sort], scores[sort], n_ctx[sort]
    starts = np.r_[0, np.flatnonzero(np.diff(ctx)) + 1]
    rank = np.arange(len(ctx)) - np.repeat(starts, np.diff(np.r_[starts, len(ctx)]))
    keep = rank < MAX_PER_CONTEXT[order]
    keys, ctx, scores, n_ctx = keys[keep], ctx[keep], scores[keep], n_ctx[keep]
    # Spend the budget on the most frequent contexts.
    starts = np.r_[0, np.flatnonzero(np.diff(ctx)) + 1]
    sizes = np.diff(np.r_[starts, len(ctx)])
    by_freq = np.argsort(-n_ctx[starts], kind="stable")
    take = by_freq[np.cumsum(sizes[by_freq]) <= BUDGET[order]]
    chosen = np.zeros(len(starts), dtype=bool)
    chosen[take] = True
    keep = np.repeat(chosen, sizes)
    keys, ctx, scores = keys[keep], ctx[keep], scores[keep]

    mask = np.uint64((1 << bits) - 1)
    parts = [((keys >> np.uint64(bits * (order - 1 - j))) & mask).astype(np.int64) for j in range(order)]
    used = np.unique(np.concatenate(parts))
    names = {int(i): (BOS_WORD if i == BOS else vocab[high_vocab[i]]) for i in used}
    ordered = sorted(names, key=names.get)
    remap = np.zeros(int(used.max()) + 1, dtype=np.int64)
    for rank_, i in enumerate(ordered):
        remap[i] = rank_
    cols = [remap[p] for p in parts]
    sort = np.lexsort(tuple([np.arange(len(keys))] + cols[:order - 1][::-1]))
    cols = [c[sort] for c in cols]
    scores = scores[sort]
    ctx_cols = np.stack(cols[:order - 1], axis=1)
    change = np.r_[True, np.any(ctx_cols[1:] != ctx_cols[:-1], axis=1)]
    starts = np.flatnonzero(change)
    ends = np.r_[starts[1:], len(scores)]

    out = bytearray(b"RNG1")
    out.append(1)
    out.append(order)
    varint(out, len(ordered))
    for i in ordered:
        data = names[i].encode("utf-8")
        varint(out, len(data))
        out.extend(data)
    varint(out, len(starts))
    nxt_col = cols[order - 1]
    for a, b in zip(starts.tolist(), ends.tolist()):
        for k in range(order - 1):
            varint(out, int(ctx_cols[a, k]))
        varint(out, b - a)
        for i in range(a, b):
            varint(out, int(nxt_col[i]))
            out.append(int(scores[i]))
    with open(path, "wb") as f:
        f.write(out)
    print(f"{order}-grams: {len(starts):,} contexts, {len(scores):,} continuations, {len(ordered):,} words, {len(out) / 1024:.0f} KiB -> {path}")


def context_counts(counter, order):
    """Counts of the (order-1)-word contexts in the id space of [order]."""
    bits = ID_BITS[order]
    src_order = order - 1
    keys, counts = counter.acc[src_order]
    src_bits = ID_BITS[src_order]
    if src_bits == bits:
        return keys, counts
    # Re-pack into this order's id space, dropping n-grams with words outside it.
    src_vocab_to_full = counter.high_vocab_by_bits.get(src_bits)
    remap = counter.high_ids[bits]
    mask = np.uint64((1 << src_bits) - 1)
    new = np.zeros(len(keys), dtype=np.uint64)
    valid = np.ones(len(keys), dtype=bool)
    for j in range(src_order):
        part = ((keys >> np.uint64(src_bits * (src_order - 1 - j))) & mask).astype(np.int64)
        full = part if src_bits == 21 else src_vocab_to_full[part]
        mapped = remap[full]
        valid &= mapped > NONE
        new = (new << np.uint64(bits)) | mapped.astype(np.uint64)
    u, inv = np.unique(new[valid], return_inverse=True)
    return u, np.bincount(inv, weights=counts[valid])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("corpora", nargs="+", help="path[:weight]")
    parser.add_argument("--out", default=OUT)
    parser.add_argument("--holdout", action="store_true", help="hold out sentences for eval_predictions.py")
    parser.add_argument("--orders", type=int, nargs="+", default=[3, 4, 5])
    parser.add_argument("--workers", type=int, default=max(1, (os.cpu_count() or 2) - 2))
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    words = json.load(open(DICT, encoding="utf-8"))
    canonical = canonical_forms(words)
    vocab = [None, BOS_WORD] + sorted(set(canonical.values()))  # stream ids: 0 none, 1 start, 2.. words
    index = {w: i for i, w in enumerate(vocab) if i > BOS}
    # Longer n-grams use compact ids: the most frequent dictionary words (by dictionary frequency).
    by_freq = sorted(range(2, len(vocab)), key=lambda i: -words[vocab[i]])
    high_ids, high_vocab_by_bits = {}, {}
    for bits in set(ID_BITS.values()):
        if bits == 21:
            continue
        top = by_freq[: (1 << bits) - 2]
        remap = np.zeros(len(vocab), dtype=np.int64)
        remap[BOS] = BOS
        remap[top] = np.arange(2, len(top) + 2)
        back = np.zeros(len(top) + 2, dtype=np.int64)
        back[BOS] = BOS
        back[2:] = top
        high_ids[bits], high_vocab_by_bits[bits] = remap, back
    counter = Counter(len(vocab), high_ids)
    counter.high_vocab_by_bits = high_vocab_by_bits

    holdouts = {}
    if args.holdout:
        holdouts = {"chat": open(os.path.join(args.out, "holdout_chat.txt"), "w", encoding="utf-8"),
                    "web": open(os.path.join(args.out, "holdout_web.txt"), "w", encoding="utf-8")}
    with Pool(args.workers, initializer=_init, initargs=(canonical, index)) as pool:
        for spec in args.corpora:
            weighted = re.match(r"^(.*):(\d+(?:\.\d+)?)$", spec)
            path, weight = (weighted.group(1), float(weighted.group(2))) if weighted else (spec, 1.0)
            web = is_web(path)
            hold = holdouts.get("web" if web else "chat")
            every = (HOLDOUT_WEB if web else HOLDOUT_CHAT) if hold else 0
            chunk, size, tokens = [], 0, 0
            for part in pool.imap(_tokenize, batches(path, hold, every), chunksize=2):
                chunk.append(part)
                size += len(part)
                if size >= CHUNK_TOKENS:
                    counter.add(np.concatenate(chunk), weight, prune=web)
                    tokens += size
                    chunk, size = [], 0
                    print(f"  {os.path.basename(path)}: {tokens / 1e6:.0f}M ids counted", flush=True)
            if chunk:
                counter.add(np.concatenate(chunk), weight, prune=web)
                tokens += size
            print(f"{os.path.basename(path)} (weight {weight:g}): {tokens / 1e6:.1f}M ids", flush=True)
    for f in holdouts.values():
        f.close()

    uni = counter.uni
    uni[NONE] = uni[BOS] = 0
    total = float(uni.sum())
    fit = np.flatnonzero(uni >= MIN_FIT_COUNT)
    xs = np.log(uni[fit] / total)
    ys = np.array([words[vocab[i]] for i in fit], dtype=np.float64)
    f_per_nat = float(((xs - xs.mean()) * (ys - ys.mean())).sum() / ((xs - xs.mean()) ** 2).sum())
    intercept = float(ys.mean() - f_per_nat * xs.mean())
    print(f"weighted tokens {total:,.0f}; scale fit over {len(fit):,} words: freq = {intercept:.1f} + {f_per_nat:.2f} * ln P")

    keys, counts = counter.acc[2]
    write_bigrams(os.path.join(args.out, "bigrams.bin"), vocab, uni, total, intercept, f_per_nat, keys, counts)
    names = {3: "trigrams.bin", 4: "fourgrams.bin", 5: "fivegrams.bin"}
    for order in args.orders:
        keys, counts = counter.acc[order]
        ctx_keys, ctx_counts = context_counts(counter, order)
        write_ngrams(os.path.join(args.out, names[order]), order, vocab, high_vocab_by_bits[ID_BITS[order]],
                     keys, counts, ctx_keys, ctx_counts, intercept, f_per_nat)


if __name__ == "__main__":
    main()
