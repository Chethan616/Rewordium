import os

path = r'c:\Users\cheth\OneDrive\Desktop\PC2\rewordium\rewordium\rewordium\rewordium\android\reboard_keyboard\src\main\kotlin\com\noxquill\rewordium\keyboard\ime\media\sticker\UserStickerStore.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('fun getPacks(): List<StickerPack> {', 'fun getSortedPacks(): List<StickerPack> {')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print("Fixed UserStickerStore clash")
