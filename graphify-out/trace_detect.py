import os
from pathlib import Path
from graphify.detect import _load_graphifyignore, _is_noise_dir, _is_ignored

root = Path('.').resolve()
ignore_patterns = _load_graphifyignore(root)
print(f"Loaded {len(ignore_patterns)} ignore patterns.")

count = 0
for dirpath, dirnames, filenames in os.walk(root):
    dp = Path(dirpath)
    # Check if we should descend:
    has_negation = any(p.startswith("!") for _, p in ignore_patterns)
    
    # Prune dirnames
    original_dirs = list(dirnames)
    dirnames[:] = [
        d for d in dirnames
        if not _is_noise_dir(d, dp)
        and (has_negation or not _is_ignored(dp / d, root, ignore_patterns))
    ]
    
    pruned = set(original_dirs) - set(dirnames)
    if pruned and count < 50:
        try:
            rel_path = dp.relative_to(root)
        except ValueError:
            rel_path = dp
        print(f"At {rel_path}, pruned: {pruned}")
        
    if count < 50:
        try:
            rel_path = dp.relative_to(root)
        except ValueError:
            rel_path = dp
        print(f"Visiting: {rel_path} (has {len(filenames)} files, {len(dirnames)} subdirs)")
        count += 1
    else:
        # Just count total visited
        count += 1

print(f"Total directories walked: {count}")
