with open('app/src/main/java/com/example/ui/screens/SettingsScreen.kt', 'r') as f:
    content = f.read()

target = """                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {"""

replacement = target + """
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

content = content.replace(target, replacement, 1)  # Only replace the first match! Wait, is there more than one?
# The dialog one is specific. Let's find a more specific target.

specific_target = """            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Provider Name") }"""

specific_replacement = """            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Provider Name") }"""

if specific_target in content:
    content = content.replace(specific_target, specific_replacement, 1)
    with open('app/src/main/java/com/example/ui/screens/SettingsScreen.kt', 'w') as f:
        f.write(content)
    print("Successfully injected.")
else:
    print("Target not found.")

