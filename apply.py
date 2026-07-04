import sys

with open('extracted.txt', 'r', encoding='utf-8') as f:
    text = f.read()

# Load original StickerEditorScreen.kt
path = r'c:\Users\cheth\OneDrive\Desktop\PC2\rewordium\rewordium\rewordium\rewordium\android\reboard_keyboard\src\main\kotlin\com\noxquill\rewordium\keyboard\app\settings\stickerstudio\StickerEditorScreen.kt'

# First, restore it to pristine condition using git
import subprocess
subprocess.run(['git', 'restore', path], check=True)

with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

import re

# We split by 'Target:\n' and 'Replace:\n'
# To avoid taking in '--- STEP ... ---' or 'Chunk ...:', we can regex.
# Actually, the format is:
# --- STEP 137 ---
# Chunk 0:
# Target:
# ...
# Replace:
# ...
# --- STEP ... ---

chunks = text.split('Target:\n')
for chunk in chunks[1:]:
    try:
        parts = chunk.split('Replace:\n', 1)
        if len(parts) < 2: continue
        target = parts[0]
        replace = parts[1]
        
        # Now remove any trailing '--- STEP' or 'Chunk' from replace
        idx1 = replace.rfind('\n--- STEP')
        if idx1 != -1:
            replace = replace[:idx1]
            
        idx2 = replace.rfind('\nChunk')
        if idx2 != -1:
            replace = replace[:idx2]
            
        if replace.endswith('\n') and not target.endswith('\n'):
            replace = replace[:-1]
            
        if target in content:
            content = content.replace(target, replace, 1)
            print("Applied chunk")
        else:
            print("Failed to find target chunk:")
    except Exception as e:
        print("Error", e)

# Add luminance import manually
content = content.replace('import androidx.compose.ui.graphics.Color\n', 'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.luminance\n')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done patching.")
