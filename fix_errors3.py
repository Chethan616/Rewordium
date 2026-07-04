import os

path = r'c:\Users\cheth\OneDrive\Desktop\PC2\rewordium\rewordium\rewordium\rewordium\android\reboard_keyboard\src\main\kotlin\com\noxquill\rewordium\keyboard\app\settings\stickerstudio\StickerEditorScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('val aiManager = com.noxquill.rewordium.keyboard.ime.ai.AIManager.get(context)',
              'val aiManager = com.noxquill.rewordium.keyboard.ime.ai.AIManager(context)')

c = c.replace('val res = aiManager.rewriteTextWithPrompt("Generate a short, funny meme caption suitable for a sticker (max 4 words). Output just the caption without quotes.").getOrNull()',
              'val res = aiManager.rewriteTextWithPrompt("Generate a short, funny meme caption suitable for a sticker (max 4 words). Output just the caption without quotes.").getOrNull() as? String')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print("Fixed AIManager instantiation and result type.")
