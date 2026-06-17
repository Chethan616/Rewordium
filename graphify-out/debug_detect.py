import sys
from pathlib import Path
from graphify.detect import detect
import time

print("Starting detect...", flush=True)
t0 = time.time()
result = detect(Path('.'))
print(f"Finished in {time.time() - t0:.2f} seconds", flush=True)
import json
print(f"Found {result.get('total_files', 0)} files")
# Let's write the result to graphify-out/.graphify_detect.json directly
Path('graphify-out/.graphify_detect.json').write_text(json.dumps(result, ensure_ascii=False), encoding='utf-8')
print("Wrote result to graphify-out/.graphify_detect.json")
