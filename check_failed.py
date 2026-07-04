import sys

with open('extracted.txt', 'r', encoding='utf-8') as f:
    text = f.read()

path = r'c:\Users\cheth\OneDrive\Desktop\PC2\rewordium\rewordium\rewordium\rewordium\android\reboard_keyboard\src\main\kotlin\com\noxquill\rewordium\keyboard\app\settings\stickerstudio\StickerEditorScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

chunks = text.split('Target:\n')
failed = []
for chunk in chunks[1:]:
    try:
        parts = chunk.split('Replace:\n', 1)
        if len(parts) < 2: continue
        target = parts[0]
        replace = parts[1]
        
        idx1 = replace.rfind('\n--- STEP')
        if idx1 != -1: replace = replace[:idx1]
            
        idx2 = replace.rfind('\nChunk')
        if idx2 != -1: replace = replace[:idx2]
            
        if replace.endswith('\n') and not target.endswith('\n'):
            replace = replace[:-1]
            
        if target not in content:
            failed.append(target[:50])
    except Exception as e:
        pass

print("Failed chunks:", failed)
