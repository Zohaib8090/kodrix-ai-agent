package com.example.linux

/** Termux/apt-style package commands become Alpine's apk. Anything else is returned unchanged. */
fun translatePackageCommand(command: String): String {
    val t = command.trim().split(Regex("\\s+"))
    if (t.isEmpty() || t[0] !in setOf("pkg", "apt", "apt-get")) return command
    val rest = t.drop(1)
    val names = rest.drop(1).filter { !it.startsWith("-") }
    return when (rest.firstOrNull()) {
        "install", "i" -> if (names.isEmpty()) command else "apk add --no-cache " + names.joinToString(" ") { if (it == "nodejs-lts") "nodejs" else it }
        "remove", "uninstall", "purge" -> if (names.isEmpty()) command else "apk del " + names.joinToString(" ")
        "update", "upgrade", "full-upgrade" -> "apk update && apk upgrade"
        "search" -> "apk search " + rest.drop(1).joinToString(" ")
        else -> command
    }
}
