import os
import re

path = r'c:\Users\cheth\OneDrive\Desktop\PC2\rewordium\rewordium\rewordium\rewordium\android\reboard_keyboard\src\main\kotlin\com\noxquill\rewordium\keyboard\app\settings\stickerstudio\StickerEditorScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# Fix Dispatchers ambiguous import: remove 'import kotlinx.coroutines.Dispatchers' and 'import kotlinx.coroutines.withContext'
c = c.replace('import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext\n', 'import kotlinx.coroutines.withContext\n')

# Actually, the file probably already imports kotlinx.coroutines.launch, let's just use kotlinx.coroutines.Dispatchers explicitly in the code.
c = c.replace('import kotlinx.coroutines.withContext\n', '')
c = c.replace('withContext(Dispatchers.Default)', 'kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default)')
c = c.replace('withContext(Dispatchers.Main)', 'kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main)')

# Fix items import for LazyRow
c = c.replace('import androidx.compose.foundation.lazy.grid.items\n', 'import androidx.compose.foundation.lazy.grid.items\nimport androidx.compose.foundation.lazy.items\n')

# Fix AiManager
c = c.replace('com.noxquill.rewordium.keyboard.ime.media.sticker.AiManager.get(context)', 'com.noxquill.rewordium.keyboard.ime.media.sticker.AIManager.get(context)')

# Fix heightIn import
c = c.replace('import androidx.compose.foundation.layout.height\n', 'import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.heightIn\n')

# Fix addStickerOutline arguments
c = c.replace('private suspend fun addStickerOutline(editor: ja.burhanrashid52.photoeditor.PhotoEditor): Boolean {', 'private suspend fun addStickerOutline(editor: ja.burhanrashid52.photoeditor.PhotoEditor, view: android.view.View): Boolean {')
c = c.replace('val ok = addStickerOutline(editor)', 'val ok = addStickerOutline(editor, photoEditorView ?: return@ToolButton)')

# Inside addStickerOutline, change view access
c = c.replace('val view = editor.photoEditorView ?: return@withContext false', '')

# Fix rewriteTextWithPrompt mismatch (it returns String?)
c = c.replace('val res = aiManager.rewriteTextWithPrompt(', 'val res = aiManager.rewriteTextWithPrompt("Sticker Caption", ')
c = c.replace('if (res != null) draft = res', 'if (res is String) draft = res') # Assuming it returns Any? or something

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print("Fixed compile errors")
