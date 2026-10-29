"""Measures next-word and phrase prediction on held-out sentences.

    python tools/build_english_ngrams.py --holdout --out lm_eval <corpora...>
    python tools/eval_predictions.py lm_eval [holdout file]

For every word of every held-out sentence (sentences the models never saw),
the predictions shown before typing it are compared with the word itself:
top-1 = it was the first suggestion, top-3 = it was on the suggestion strip.
Only dictionary words count as targets. The prediction logic mirrors
LatinLanguageProvider.predictNextWords: the longest known context wins and
each shorter context a word falls back to costs BACKOFF nats ("stupid
backoff"), counted only for contexts that were actually known.

Phrases (LatinLanguageProvider.predictPhrase): the first prediction is
extended one word at a time while the next word is at least THETA likely in
the longest known context. Precision = the offered phrase is exactly what was
written next; coverage = how often a phrase of two or more words is offered.
"""
import json, math, os, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from build_english_ngrams import BOS_WORD, DICT, SENTENCE_SPLIT, TOKEN, canonical_forms, is_stock_name  # noqa: E402

BIGRAM_TOP = 40
BACKOFF_NATS = 0.92


def reader(path):
    data = open(path, "rb").read()
    pos = [0]

    def u8():
        v = data[pos[0]]
        pos[0] += 1
        return v

    def vi():
        r = s = 0
        while True:
            x = u8()
            r |= (x & 0x7F) << s
            if not x & 0x80:
                return r
            s += 7

    def text(n):
        t = data[pos[0]:pos[0] + n].decode("utf-8")
        pos[0] += n
        return t

    return u8, vi, text


def load_bigrams(path):
    u8, vi, text = reader(path)
    text(4)
    version = u8()
    vi()
    f_per_nat = vi() / 1000
    intercept = vi() / 1000 if version >= 4 else None
    if version >= 4:
        vi()
    words = []
    for _ in range(vi()):
        words.append(text(vi()))
        u8()
    model = {}
    for _ in range(vi()):
        prev = words[vi()]
        last = 0
        pairs = []
        for _ in range(vi()):
            last += vi()
            pairs.append((words[last], u8()))
        pairs.sort(key=lambda p: -p[1])
        model[prev] = pairs[:BIGRAM_TOP]
    return model, f_per_nat, intercept


def load_ngrams(path):
    if not os.path.exists(path):
        return None
    u8, vi, text = reader(path)
    text(4), u8()
    order = u8()
    words = [text(vi()) for _ in range(vi())]
    model = {}
    for _ in range(vi()):
        ctx = tuple(words[vi()] for _ in range(order - 1))
        model[ctx] = [(words[vi()], u8()) for _ in range(vi())]
    return model


class Predictor:
    def __init__(self, model_dir, orders):
        self.bigrams, self.f_per_nat, self.intercept = load_bigrams(os.path.join(model_dir, "bigrams.bin"))
        files = {3: "trigrams.bin", 4: "fourgrams.bin", 5: "fivegrams.bin"}
        self.models = []
        for order in sorted(orders, reverse=True):
            m = load_ngrams(os.path.join(model_dir, files[order]))
            if m is not None:
                self.models.append((order, m))

    def scored(self, ctx, use_bos=True):
        scores = {}
        cost = 0.0
        penalty = BACKOFF_NATS * self.f_per_nat

        def offer(pairs):
            for w, s in pairs:
                v = s - cost
                if v > scores.get(w, -1e9):
                    scores[w] = v

        for order, model in self.models:
            n = order - 1
            if len(ctx) >= n and tuple(ctx[-n:]) in model:
                offer(model[tuple(ctx[-n:])])
                cost += penalty
        if ctx[-1] != BOS_WORD or use_bos:
            offer(self.bigrams.get(ctx[-1], ()))
        scores.pop(ctx[-1], None)
        scores.pop(BOS_WORD, None)
        return sorted(scores.items(), key=lambda kv: -kv[1])

    def predict(self, ctx, k=3, use_bos=True):
        return [w for w, _ in self.scored(ctx, use_bos)[:k]]

    def confident_next(self, ctx, theta):
        """The top next word when the longest known context gives it at least [theta] probability."""
        if self.intercept is None:
            return None
        for order, model in self.models + [(2, None)]:
            n = order - 1
            if len(ctx) < n:
                continue
            pairs = model.get(tuple(ctx[-n:])) if model is not None else self.bigrams.get(ctx[-1])
            if not pairs:
                continue
            word, score = pairs[0]
            if word in (ctx[-1], BOS_WORD):
                return None
            p = math.exp((score - self.intercept) / self.f_per_nat)
            return word if p >= theta else None
        return None

    def phrase(self, ctx, theta, max_words=3):
        first = self.predict(ctx, 1)
        if not first or self.confident_next(ctx, theta) != first[0]:
            return []
        out = [first[0]]
        while len(out) < max_words:
            nxt = self.confident_next(ctx + out, theta)
            if nxt is None:
                break
            out.append(nxt)
        return out


def clauses(path, canonical):
    """Held-out clauses as lists of canonical words, None where a token breaks the context."""
    with open(path, encoding="utf-8") as f:
        for sentence in f:
            for clause in SENTENCE_SPLIT.split(sentence.strip()):
                seq = [] if clause.startswith((";", ":")) else [BOS_WORD]
                for m in TOKEN.finditer(clause):
                    raw = m.group(0).replace("’", "'")
                    seq.append(None if is_stock_name(raw) else canonical.get(raw.lower()))
                yield seq


def cases_from(path, canonical, limit):
    cases = []
    for seq in clauses(path, canonical):
        for i in range(1, len(seq)):
            if seq[i] is None:
                continue
            ctx = []
            for w in reversed(seq[:i]):
                if w is None:
                    break
                ctx.insert(0, w)
            following = []
            for w in seq[i:i + 3]:
                if w is None:
                    break
                following.append(w)
            cases.append((ctx, following))
        if len(cases) >= limit:
            break
    return cases


def evaluate(model_dir, holdout, limit=60000):
    canonical = canonical_forms(json.load(open(DICT, encoding="utf-8")))
    cases = cases_from(holdout, canonical, limit)
    print(f"== {os.path.basename(model_dir)} on {os.path.basename(holdout)}: {len(cases):,} word positions")
    available = [o for o, f in ((3, "trigrams.bin"), (4, "fourgrams.bin"), (5, "fivegrams.bin")) if os.path.exists(os.path.join(model_dir, f))]
    configs = [("word pairs", [])] + [(f"up to {o}-grams", available[: i + 1]) for i, o in enumerate(available)]
    full = None
    for name, orders in configs:
        p = Predictor(model_dir, orders)
        top1 = top3 = empty = 0
        start = time.perf_counter()
        for ctx, following in cases:
            out = p.predict(ctx) if ctx else []
            if not out:
                empty += 1
            if out[:1] == following[:1]:
                top1 += 1
            if following[0] in out:
                top3 += 1
        per = (time.perf_counter() - start) / len(cases) * 1e6
        n = len(cases)
        print(f"  {name:16} top-1 {100 * top1 / n:5.1f}%  top-3 {100 * top3 / n:5.1f}%  no suggestion {100 * empty / n:4.1f}%  ({per:.0f} us/prediction in Python)")
        full = p
    if full is not None and full.intercept is not None:
        for theta in (0.2, 0.3, 0.4, 0.5):
            offered = exact = words_saved = 0
            for ctx, following in cases:
                if not ctx:
                    continue
                phrase = full.phrase(ctx, theta)
                if len(phrase) < 2:
                    continue
                offered += 1
                if following[:len(phrase)] == phrase:
                    exact += 1
                    words_saved += len(phrase)
            n = len(cases)
            print(f"  phrases theta {theta:.1f}: offered at {100 * offered / n:4.1f}% of positions, exactly right {100 * exact / max(1, offered):5.1f}% of the time")


if __name__ == "__main__":
    model_dir = sys.argv[1]
    holdouts = sys.argv[2:] or [os.path.join(model_dir, f) for f in ("holdout_chat.txt", "holdout_web.txt", "holdout.txt") if os.path.exists(os.path.join(model_dir, f))]
    for h in holdouts:
        evaluate(model_dir, h)
