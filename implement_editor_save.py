import os

path = r'c:\Users\cheth\OneDrive\Desktop\PC2\rewordium\rewordium\rewordium\rewordium\android\reboard_keyboard\src\main\kotlin\com\noxquill\rewordium\keyboard\app\settings\stickerstudio\StickerEditorScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# 1. State for packs
c = c.replace('var saveTagsText by remember { mutableStateOf("") }',
'''var saveTagsText by remember { mutableStateOf("") }
    var savePackId by remember { mutableStateOf<String?>(null) }
    val packs by store.packsFlow.collectAsState()''')

# 2. Add lazy row items import if not there, wait, I already added import androidx.compose.foundation.lazy.items in apply_v3.py.
# But wait, did I add it? Yes.
c = c.replace('import androidx.compose.foundation.lazy.items',
'''import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState''')

# 3. Update Save Dialog UI
target_dialog = '''    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save sticker") },
            text = {
                Column {
                    Text(
                        text = "Add tags to categorize this sticker (comma separated):",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = saveTagsText,
                        onValueChange = { saveTagsText = it },
                        label = { Text("Tags") },
                        placeholder = { Text("e.g. John, Reaction, Funny") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        val tagsList = saveTagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val editor = photoEditor ?: return@TextButton
                        val view = photoEditorView ?: return@TextButton
                        saving = true
                        scope.launch {
                            val ok = exportAndImport(context, editor, view, store, tagsList)'''

replacement_dialog = '''    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save sticker") },
            text = {
                Column {
                    if (packs.isNotEmpty()) {
                        Text("Sticker Pack:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = savePackId == null,
                                    onClick = { savePackId = null },
                                    label = { Text("None") }
                                )
                            }
                            items(packs) { pack ->
                                FilterChip(
                                    selected = savePackId == pack.id,
                                    onClick = { savePackId = pack.id },
                                    label = { Text(pack.name) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Text(
                        text = "Add tags to categorize this sticker (comma separated):",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = saveTagsText,
                        onValueChange = { saveTagsText = it },
                        label = { Text("Tags") },
                        placeholder = { Text("e.g. John, Reaction, Funny") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        val tagsList = saveTagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val editor = photoEditor ?: return@TextButton
                        val view = photoEditorView ?: return@TextButton
                        saving = true
                        scope.launch {
                            val ok = exportAndImport(context, editor, view, store, tagsList, savePackId)'''

c = c.replace(target_dialog, replacement_dialog)

# 4. Update exportAndImport signature
target_export = '''private suspend fun exportAndImport(
    context: android.content.Context,
    editor: PhotoEditor,
    view: PhotoEditorView,
    store: UserStickerStore,
    tags: List<String> = emptyList(),
): Boolean = withContext(Dispatchers.IO) {'''
replacement_export = '''private suspend fun exportAndImport(
    context: android.content.Context,
    editor: PhotoEditor,
    view: PhotoEditorView,
    store: UserStickerStore,
    tags: List<String> = emptyList(),
    packId: String? = null
): Boolean = withContext(Dispatchers.IO) {'''

c = c.replace(target_export, replacement_export)

# 5. Update call to store.import inside exportAndImport
target_store_import = '''store.import(Uri.fromFile(tmpFile), "image/png", tags)'''
replacement_store_import = '''store.import(Uri.fromFile(tmpFile), "image/png", tags, packId)'''

c = c.replace(target_store_import, replacement_store_import)

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print("Updated StickerEditorScreen successfully")
