import re

with open('app/src/main/java/com/example/ui/screens/SettingsScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
in_dialog = False
for i, line in enumerate(lines):
    new_lines.append(line)
    if "text = \"Add Custom AI Provider\"" in line:
        in_dialog = True
    
    if in_dialog and "value = name," in line and "OutlinedTextField(" in lines[i-1]:
        # We found the first text field in the dialog!
        # Insert our box before the OutlinedTextField
        box_code = """                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = providerType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Provider Type") },
                            trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        androidx.compose.material3.Surface(
                            modifier = Modifier.matchParentSize().clickable { typeDropdownExpanded = true },
                            color = androidx.compose.ui.graphics.Color.Transparent
                        ) {}
                        DropdownMenu(
                            expanded = typeDropdownExpanded,
                            onDismissRequest = { typeDropdownExpanded = false }
                        ) {
                            types.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        providerType = type
                                        typeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
"""
        new_lines.insert(-1, box_code) # insert before the previous line which is OutlinedTextField
        in_dialog = False

with open('app/src/main/java/com/example/ui/screens/SettingsScreen.kt', 'w') as f:
    f.writelines(new_lines)

print("Done")
