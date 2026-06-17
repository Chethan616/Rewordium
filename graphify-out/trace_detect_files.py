import sys
sys.stdout.reconfigure(encoding='utf-8')
import os
from pathlib import Path
from graphify.detect import _load_graphifyignore, _is_noise_dir, _is_ignored, classify_file, count_words
import time

root = Path('.').resolve()
ignore_patterns = _load_graphifyignore(root)

files = []
t0 = time.time()
print("Starting file collection...", flush=True)
for dirpath, dirnames, filenames in os.walk(root):
    dp = Path(dirpath)
    has_negation = any(p.startswith("!") for _, p in ignore_patterns)
    dirnames[:] = [
        d for d in dirnames
        if not _is_noise_dir(d, dp)
        and (has_negation or not _is_ignored(dp / d, root, ignore_patterns))
    ]
    for fname in filenames:
        p = dp / fname
        if not _is_ignored(p, root, ignore_patterns):
            files.append(p)
            if len(files) % 500 == 0:
                try:
                    rel = p.relative_to(root)
                except ValueError:
                    rel = p
                print(f"  Collected {len(files)} files... Last: {rel} (time: {time.time() - t0:.2f}s)", flush=True)

print(f"Total non-ignored files: {len(files)}")
print("\nScanning and counting words:", flush=True)
t0 = time.time()
for i, f in enumerate(files):
    ftype = classify_file(f)
    if ftype:
        try:
            w = count_words(f)
            if w > 50000:
                print(f"  Large file: {f.relative_to(root)} ({w} words)", flush=True)
        except Exception as e:
            print(f"  Error on {f.relative_to(root)}: {e}", flush=True)
    if i > 0 and i % 500 == 0:
        print(f"  Processed {i}/{len(files)} files...", flush=True)

print(f"Finished in {time.time() - t0:.2f} seconds")
