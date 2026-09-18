with open('app/src/main/java/com/example/ui/screens/SettingsScreen.kt', 'r') as f:
    content = f.read()

bad_block = """                    OutlinedTextField(
                    Box(modifier = Modifier.fillMaxWidth()) {"""

good_block = """                    Box(modifier = Modifier.fillMaxWidth()) {
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
                    OutlinedTextField("""

target_to_replace = """                    OutlinedTextField(
                    Box(modifier = Modifier.fillMaxWidth()) {
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
                    }"""

content = content.replace(target_to_replace, good_block, 1)

with open('app/src/main/java/com/example/ui/screens/SettingsScreen.kt', 'w') as f:
    f.write(content)

