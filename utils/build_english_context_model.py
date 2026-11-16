#!/usr/bin/env python3
"""
Build the English context model (app/src/main/assets/lm/en_context.sflm).

The model scores how likely a word is after the one or two words before it.
It's a pruned table of word pair and word triple frequencies counted in
English web text, read on the phone by ime/nlp/context/ContextModel.kt with
"stupid backoff" (Brants et al. 2007): an unseen triple falls back to the
pair times alpha, an unseen pair to the word's own frequency times alpha.

The shipped file was counted from one shard of C4 (allenai/c4 on Hugging
Face, ODC-BY, en/c4-train.00000-of-01024.json.gz, 356,317 web pages):

    python utils/build_english_context_model.py count c4-train.00000-of-01024.json.gz \
        app/src/main/assets/ime/dict/data.json counts/
    python utils/build_english_context_model.py merge counts/
    python utils/build_english_context_model.py build counts/ \
        app/src/main/assets/lm/en_context.sflm --vocab 50000 --min-count 12 --margin 0.5

Those settings give a 5.9 MB file. On AutocorrectAccuracyHarnessTest it
fixed 52 careful and 378 sloppy slips, against 53 and 378 for a 9.4 MB
file (--min-count 8 --margin 0.4) and 53 and 379 for a 30 MB one
(--vocab 64000 --min-count 3 --margin 0.3).

The shard holds 127 million words. Counting them took about a minute here,
and the counts take about 1 GB of disk. numpy is the only dependency.

Words are what TypingContextExtractor treats as words (letters joined by
apostrophes or hyphens), lowercased, with the typographic apostrophe folded
into the plain one. Sentences end at . ! ? and line breaks. A word missing
from the dictionary breaks every pair and triple it touches, so the model
never learns a context the keyboard can't type from its own word list.

File layout, big-endian, version 1:

    "SFLM", int version, int V, float alpha, int quantScale
    int wordBytesLength, word bytes (the V words in UTF-8 byte order, joined
        by '\\n'), int[V + 1] word start offsets (the last one is length + 1)
    byte[V + 1] unigram q (index 0 is the sentence start and unused)
    int[V + 2] bigram offsets by context id (0 is the sentence start),
        int B, u16[B] next word ids, byte[B] q
    int C, int[C] trigram context keys (a << 16 | b, sorted as signed ints),
        int[C + 1] offsets, int T, u16[T] next word ids, byte[T] q

Word ids are 1..V in byte order, and next word ids are sorted within each
context. q is round(-log10(score) * quantScale), clamped to 0..255.

Pruning keeps a pair or triple only when it was seen min-count times and its
score is at least margin powers of ten away from what backoff would give
without it, so it carries information the shorter context doesn't.
"""

import argparse
import glob
import gzip
import json
import os
import re
import struct
import sys
import time
from pathlib import Path

import numpy as np

FORMAT_VERSION = 1
QUANT_SCALE = 25
ID_BITS = 18
ID_MASK = (1 << ID_BITS) - 1
START = 0
UNK = ID_MASK - 1
BREAK = ID_MASK
MAX_VOCAB = 65534
BIGRAM_PARTITIONS = 4
TRIGRAM_PARTITIONS = 16
CHUNK_TOKENS = 12_000_000

SENTENCE_END = re.compile(r"[.!?\n]+")
WORD = re.compile(r"[a-z]+(?:['\-][a-z]+)*")
RIGHT_SINGLE_QUOTE = chr(0x2019)


def count(args):
    """Count unigrams, bigrams and trigrams of dictionary words, sentence by sentence."""
    out = Path(args.counts)
    out.mkdir(parents=True, exist_ok=True)
    words = list(json.loads(Path(args.dictionary).read_text(encoding="utf-8")).keys())
    ids = {word: index + 1 for index, word in enumerate(words)}
    if len(ids) + 1 >= UNK:
        sys.exit(f"{len(ids)} dictionary words don't fit in {ID_BITS}-bit ids")
    (out / "words.json").write_text(json.dumps(words), encoding="utf-8")

    unigrams = np.zeros(UNK, dtype=np.int64)
    buffer = []
    chunk = 0
    docs = 0
    tokens = 0
    started = time.time()

    def flush():
        nonlocal buffer, chunk
        if not buffer:
            return
        seq = np.array(buffer, dtype=np.int64)
        buffer = []
        is_word = (seq != START) & (seq != UNK) & (seq != BREAK)
        unigrams[:] += np.bincount(seq[is_word], minlength=UNK)[:UNK]
        # A pair (a, b): b a word, a a word or the sentence start.
        a, b = seq[:-1], seq[1:]
        pairs = is_word[1:] & (is_word[:-1] | (a == START))
        pair_keys = (a[pairs] << ID_BITS) | b[pairs]
        # A triple (a, b, c): b and c words, a a word or the sentence start.
        a3, b3, c3 = seq[:-2], seq[1:-1], seq[2:]
        triples = is_word[2:] & is_word[1:-1] & (is_word[:-2] | (a3 == START))
        triple_keys = (a3[triples] << (2 * ID_BITS)) | (b3[triples] << ID_BITS) | c3[triples]
        for name, keys, partitions in (("bi", pair_keys, BIGRAM_PARTITIONS), ("tri", triple_keys, TRIGRAM_PARTITIONS)):
            unique, counts = np.unique(keys, return_counts=True)
            partition = unique % partitions
            for p in range(partitions):
                selected = partition == p
                np.save(out / f"{name}_p{p:02d}_c{chunk:03d}_k.npy", unique[selected])
                np.save(out / f"{name}_p{p:02d}_c{chunk:03d}_n.npy", counts[selected].astype(np.int32))
        chunk += 1
        print(f"chunk {chunk}: {docs:,} pages, {tokens:,} words, {time.time() - started:.0f}s", flush=True)

    with gzip.open(args.shard, "rt", encoding="utf-8") as shard:
        for line in shard:
            if docs >= args.max_docs:
                break
            docs += 1
            text = json.loads(line)["text"].lower().replace(RIGHT_SINGLE_QUOTE, "'")
            for sentence in SENTENCE_END.split(text):
                sentence_words = WORD.findall(sentence)
                if not sentence_words:
                    continue
                buffer.append(BREAK)
                buffer.append(START)
                buffer.extend(ids.get(word, UNK) for word in sentence_words)
                tokens += len(sentence_words)
            if len(buffer) >= CHUNK_TOKENS:
                flush()
    flush()
    np.save(out / "unigrams.npy", unigrams[: len(words) + 1])
    print(f"counted {docs:,} pages, {tokens:,} words in {chunk} chunks, {time.time() - started:.0f}s")


def merge(args):
    """Merge the per-chunk counts into one sorted key and count array per partition."""
    root = Path(args.counts)
    for name in ("bi", "tri"):
        chunk_files = glob.glob(str(root / f"{name}_p*_c*_k.npy"))
        partitions = sorted({re.findall(r"_p(\d+)_c", path)[-1] for path in chunk_files})
        total = 0
        for part in partitions:
            key_files = sorted(glob.glob(str(root / f"{name}_p{part}_c*_k.npy")))
            keys = np.concatenate([np.load(path) for path in key_files])
            counts = np.concatenate([np.load(path.replace("_k.npy", "_n.npy")) for path in key_files]).astype(np.int64)
            unique, inverse = np.unique(keys, return_inverse=True)
            merged = np.bincount(inverse, weights=counts).astype(np.int64)
            np.save(root / f"{name}_p{part}_k.npy", unique)
            np.save(root / f"{name}_p{part}_n.npy", merged)
            for path in key_files:
                os.remove(path)
                os.remove(path.replace("_k.npy", "_n.npy"))
            total += len(unique)
            print(f"{name} p{part}: {len(unique):,} distinct", flush=True)
        print(f"{name}: {total:,} distinct in {len(partitions)} partitions", flush=True)


def load_merged(root, name):
    keys, counts = [], []
    for path in sorted(glob.glob(str(root / f"{name}_p*_k.npy"))):
        if "_c" in os.path.basename(path):
            sys.exit(f"{path} isn't merged yet, run merge first")
        keys.append(np.load(path))
        counts.append(np.load(path.replace("_k.npy", "_n.npy")))
    return keys, counts


def quantize(score):
    return np.clip(np.rint(-np.log10(score) * QUANT_SCALE), 0, 255).astype(np.uint8)


def build(args):
    """Prune the merged counts into a context model and write it."""
    root = Path(args.counts)
    alpha = args.alpha
    words = json.loads((root / "words.json").read_text(encoding="utf-8"))
    unigrams = np.load(root / "unigrams.npy").astype(np.int64)
    total = unigrams[1:].sum()

    # The vocabulary: the most frequent dictionary words, ids in UTF-8 byte order.
    by_count = np.argsort(-unigrams[1:], kind="stable") + 1
    by_count = by_count[unigrams[by_count] >= args.min_count][: min(args.vocab, MAX_VOCAB)]
    chosen = sorted(by_count.tolist(), key=lambda old: words[old - 1].encode("utf-8"))
    remap = np.full(len(words) + 1, -1, dtype=np.int64)
    remap[0] = START
    for new_id, old in enumerate(chosen, start=1):
        remap[old] = new_id
    vocab = len(chosen)
    word_probability = unigrams / total
    print(f"vocabulary {vocab:,} words, covering {unigrams[by_count].sum() / total:.2%} of {total:,} words")

    pair_parts, pair_count_parts = load_merged(root, "bi")
    pair_keys = np.concatenate(pair_parts)
    pair_counts = np.concatenate(pair_count_parts)
    order = np.argsort(pair_keys)
    pair_keys, pair_counts = pair_keys[order], pair_counts[order]
    first, second = pair_keys >> ID_BITS, pair_keys & ID_MASK
    context_totals = np.bincount(first, weights=pair_counts, minlength=len(words) + 1)
    pair_scores = pair_counts / context_totals[first]
    keep = (remap[first] >= 0) & (remap[second] >= 1) & (pair_counts >= args.min_count)
    keep &= np.abs(np.log10(pair_scores) - np.log10(alpha * word_probability[second])) >= args.margin
    kept_pair_keys, kept_pair_scores = pair_keys[keep], pair_scores[keep]
    print(f"pairs: {len(pair_keys):,} distinct, kept {keep.sum():,}")

    triple_a, triple_b, triple_c, triple_scores = [], [], [], []
    seen_triples = 0
    for keys, counts in zip(*load_merged(root, "tri")):
        seen_triples += len(keys)
        a, b, c = keys >> (2 * ID_BITS), (keys >> ID_BITS) & ID_MASK, keys & ID_MASK
        known = (remap[a] >= 0) & (remap[b] >= 1) & (remap[c] >= 1) & (counts >= args.min_count)
        a, b, c, counts = a[known], b[known], c[known], counts[known]
        context_keys = (a << ID_BITS) | b
        at = np.searchsorted(pair_keys, context_keys)
        assert np.all(pair_keys[at] == context_keys)
        scores = counts / pair_counts[at]
        backoff_keys = (b << ID_BITS) | c
        kept_at = np.searchsorted(kept_pair_keys, backoff_keys).clip(0, len(kept_pair_keys) - 1)
        has_pair = kept_pair_keys[kept_at] == backoff_keys
        shorter = np.where(has_pair, kept_pair_scores[kept_at], alpha * word_probability[c])
        informative = np.abs(np.log10(scores) - np.log10(alpha * shorter)) >= args.margin
        triple_a.append(remap[a[informative]])
        triple_b.append(remap[b[informative]])
        triple_c.append(remap[c[informative]])
        triple_scores.append(scores[informative])
    ta, tb, tc, ts = (np.concatenate(parts) for parts in (triple_a, triple_b, triple_c, triple_scores))
    print(f"triples: {seen_triples:,} distinct, kept {len(ta):,}")

    # Pairs by context id, next words in id order.
    context_ids, next_ids = remap[kept_pair_keys >> ID_BITS], remap[kept_pair_keys & ID_MASK]
    order = np.lexsort((next_ids, context_ids))
    context_ids, next_ids, pair_q = context_ids[order], next_ids[order], quantize(kept_pair_scores[order])
    pair_offsets = np.searchsorted(context_ids, np.arange(vocab + 2)).astype(np.int64)

    # Triple contexts as signed 32-bit keys a << 16 | b, next words in id order.
    triple_contexts = ((ta << 16) | tb).astype(np.uint32).view(np.int32).astype(np.int64)
    order = np.lexsort((tc, triple_contexts))
    triple_contexts, tc, triple_q = triple_contexts[order], tc[order], quantize(ts[order])
    contexts, starts = np.unique(triple_contexts, return_index=True)
    triple_offsets = np.append(starts, len(triple_contexts)).astype(np.int64)

    word_bytes = [words[old - 1].encode("utf-8") for old in chosen]
    blob = b"\n".join(word_bytes)
    word_starts, position = [], 0
    for word in word_bytes:
        word_starts.append(position)
        position += len(word) + 1
    word_starts.append(len(blob) + 1)
    unigram_q = np.zeros(vocab + 1, dtype=np.uint8)
    unigram_q[1:] = quantize(word_probability[np.array(chosen)])

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "wb") as f:
        f.write(b"SFLM")
        f.write(struct.pack(">iifi", FORMAT_VERSION, vocab, alpha, QUANT_SCALE))
        f.write(struct.pack(">i", len(blob)))
        f.write(blob)
        f.write(np.array(word_starts, dtype=">i4").tobytes())
        f.write(unigram_q.tobytes())
        f.write(pair_offsets.astype(">i4").tobytes())
        f.write(struct.pack(">i", len(next_ids)))
        f.write(next_ids.astype(">u2").tobytes())
        f.write(pair_q.tobytes())
        f.write(struct.pack(">i", len(contexts)))
        f.write(contexts.astype(">i4").tobytes())
        f.write(triple_offsets.astype(">i4").tobytes())
        f.write(struct.pack(">i", len(tc)))
        f.write(tc.astype(">u2").tobytes())
        f.write(triple_q.tobytes())
    print(f"wrote {out}: {out.stat().st_size:,} bytes, {len(next_ids):,} pairs, "
          f"{len(contexts):,} triple contexts, {len(tc):,} triples")


def main():
    parser = argparse.ArgumentParser(description="Build the English context model (en_context.sflm).")
    commands = parser.add_subparsers(dest="command", required=True)

    count_parser = commands.add_parser("count", help="count n-grams in a C4 shard")
    count_parser.add_argument("shard", help="a C4 en .json.gz shard")
    count_parser.add_argument("dictionary", help="app/src/main/assets/ime/dict/data.json")
    count_parser.add_argument("counts", help="directory for the counts")
    count_parser.add_argument("--max-docs", type=int, default=10**12, help="stop after this many pages")
    count_parser.set_defaults(run=count)

    merge_parser = commands.add_parser("merge", help="merge the per-chunk counts")
    merge_parser.add_argument("counts")
    merge_parser.set_defaults(run=merge)

    build_parser = commands.add_parser("build", help="prune the counts and write the .sflm file")
    build_parser.add_argument("counts")
    build_parser.add_argument("out")
    build_parser.add_argument("--vocab", type=int, default=50000, help="most frequent words to keep")
    build_parser.add_argument("--min-count", type=int, default=12, help="times a pair or triple must be seen")
    build_parser.add_argument("--margin", type=float, default=0.5,
                              help="powers of ten a pair or triple must differ from its backoff")
    build_parser.add_argument("--alpha", type=float, default=0.4, help="backoff weight")
    build_parser.set_defaults(run=build)

    args = parser.parse_args()
    args.run(args)


if __name__ == "__main__":
    main()
