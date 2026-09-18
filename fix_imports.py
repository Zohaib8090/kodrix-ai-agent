import os
for file in ["app/src/main/java/com/example/ui/screens/SettingsScreen.kt", "app/src/main/java/com/example/ui/screens/HistoryScreen.kt", "app/src/main/java/com/example/ui/screens/BuildTrackerScreen.kt"]:
    with open(file, "r") as f:
        lines = f.readlines()
    if lines[0].startswith("import"):
        # Remove the top imports and find package line
        imports = [lines[0], lines[1]]
        lines = lines[2:]
        package_idx = -1
        for i, l in enumerate(lines):
            if l.startswith("package "):
                package_idx = i
                break
        if package_idx != -1:
            lines.insert(package_idx + 1, imports[0])
            lines.insert(package_idx + 2, imports[1])
            with open(file, "w") as f:
                f.writelines(lines)
