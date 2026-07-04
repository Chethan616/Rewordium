import os

path = r'c:\Users\cheth\OneDrive\Desktop\PC2\rewordium\rewordium\rewordium\rewordium\android\reboard_keyboard\src\main\kotlin\com\noxquill\rewordium\keyboard\app\settings\stickerstudio\StickerEditorScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# Fix addStickerOutline signature and usage
c = c.replace('private suspend fun addStickerOutline(editor: ja.burhanrashid52.photoeditor.PhotoEditor, view: android.view.View): Boolean {',
'private suspend fun addStickerOutline(editor: ja.burhanrashid52.photoeditor.PhotoEditor, view: ja.burhanrashid52.photoeditor.PhotoEditorView): Boolean {')

c = c.replace('editor.photoEditorView?.source?.setImageBitmap(out)', 'view.source.setImageBitmap(out)')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print("Fixed view errors")
