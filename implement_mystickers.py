import os

path = r'c:\Users\cheth\OneDrive\Desktop\PC2\rewordium\rewordium\rewordium\rewordium\android\reboard_keyboard\src\main\kotlin\com\noxquill\rewordium\keyboard\app\settings\stickerstudio\MyStickersScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# 1. Add imports
c = c.replace('import androidx.compose.material3.TextButton\n',
'''import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.item
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
''')

# 2. Add actions to FlorisScreen
c = c.replace('scrollable = false  // grid scrolls itself\n',
'''scrollable = false  // grid scrolls itself

    var showCreatePackDialog by remember { mutableStateOf(false) }
    actions {
        IconButton(onClick = { showCreatePackDialog = true }) {
            Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Create Pack")
        }
    }
''')

# 3. Add pack states
c = c.replace('val sortedEntries = remember(entries) { entries.sortedByDescending { it.t } }\n',
'''val sortedEntries = remember(entries) { entries.sortedByDescending { it.t } }
    val packs by store.packsFlow.collectAsState()
''')

# 4. Replace Grid
target_grid = '''        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(sortedEntries, key = { it.id }) { entry ->
                val file = store.fileFor(entry)
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .pointerInput(entry.id) {
                            detectTapGestures(
                                onLongPress = { actionTarget = entry },
                                onTap = { actionTarget = entry },
                            )
                        },
                ) {
                    AsyncImage(
                        model = Uri.fromFile(file),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                }
            }
        }'''
replacement_grid = '''        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            val packsList = packs.sortedByDescending { it.t }
            for (pack in packsList) {
                val packEntries = sortedEntries.filter { it.packId == pack.id }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pack.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        var expanded by remember { mutableStateOf(false) }
                        var showRename by remember { mutableStateOf(false) }
                        
                        Box {
                            IconButton(onClick = { expanded = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.MoreVert, null)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { expanded = false; showRename = true })
                                DropdownMenuItem(text = { Text("Delete Pack") }, onClick = { 
                                    expanded = false
                                    scope.launch { store.deletePack(pack.id) }
                                })
                            }
                        }
                        
                        if (showRename) {
                            var newName by remember { mutableStateOf(pack.name) }
                            AlertDialog(
                                onDismissRequest = { showRename = false },
                                title = { Text("Rename Pack") },
                                text = {
                                    OutlinedTextField(
                                        value = newName,
                                        onValueChange = { newName = it },
                                        singleLine = true
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        if (newName.isNotBlank()) {
                                            scope.launch { store.renamePack(pack.id, newName.trim()) }
                                        }
                                        showRename = false
                                    }) { Text("Rename") }
                                },
                                dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
                            )
                        }
                    }
                }
                
                if (packEntries.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("No stickers in this pack.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                } else {
                    items(packEntries, key = { it.id }) { entry ->
                        StickerItem(entry, store) { actionTarget = entry }
                    }
                }
            }
            
            val uncategorized = sortedEntries.filter { it.packId == null }
            if (uncategorized.isNotEmpty() || packsList.isEmpty()) {
                if (packsList.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("Uncategorized", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    }
                }
                items(uncategorized, key = { it.id }) { entry ->
                    StickerItem(entry, store) { actionTarget = entry }
                }
            }
        }'''
c = c.replace(target_grid, replacement_grid)

# 5. Add UI components
c = c.replace('actionTarget?.let { entry ->',
'''if (showCreatePackDialog) {
        var packName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePackDialog = false },
            title = { Text("New Sticker Pack") },
            text = {
                OutlinedTextField(
                    value = packName,
                    onValueChange = { packName = it },
                    label = { Text("Pack Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (packName.isNotBlank()) {
                        scope.launch { store.createPack(packName.trim()) }
                    }
                    showCreatePackDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePackDialog = false }) { Text("Cancel") }
            }
        )
    }

    actionTarget?.let { entry ->''')

# 6. Add StickerItem helper
c = c.replace('}\n\n',
'''}

@Composable
private fun StickerItem(entry: UserStickerStore.Entry, store: UserStickerStore, onAction: () -> Unit) {
    val file = store.fileFor(entry)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(entry.id) {
                detectTapGestures(
                    onLongPress = { onAction() },
                    onTap = { onAction() },
                )
            },
    ) {
        AsyncImage(
            model = Uri.fromFile(file),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(8.dp),
        )
    }
}

''')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print("Updated MyStickersScreen successfully")
