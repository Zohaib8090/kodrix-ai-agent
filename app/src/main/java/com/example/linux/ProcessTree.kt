package com.example.linux

import java.io.File

/**
 * Finds and kills a process's descendants. Needed because killing `proot` alone leaves its guest
 * processes running (a SIGKILLed proot cannot clean up its children).
 */
internal object ProcessTree {

    /** The pid of [p]: Process.pid() on API 33+, otherwise the private `pid` field. */
    fun pidOf(p: Process): Int? = try {
        (Process::class.java.getMethod("pid").invoke(p) as Long).toInt()
    } catch (_: Exception) {
        try {
            p.javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(p)
        } catch (_: Exception) {
            null
        }
    }

    /** Parent pid from the text of /proc/<pid>/stat. The command name may contain spaces and parentheses. */
    fun parsePpid(stat: String): Int? {
        val close = stat.lastIndexOf(')')
        if (close < 0) return null
        val fields = stat.substring(close + 1).trim().split(' ')
        return fields.getOrNull(1)?.toIntOrNull() // fields: state, ppid, ...
    }

    /** All descendants of [rootPid], parents before children (so reverse it to kill leaves first). */
    fun descendants(rootPid: Int, procDir: File = File("/proc")): List<Int> {
        val children = HashMap<Int, MutableList<Int>>()
        procDir.listFiles()?.forEach { d ->
            val pid = d.name.toIntOrNull() ?: return@forEach
            val ppid = try { parsePpid(File(d, "stat").readText()) } catch (_: Exception) { null } ?: return@forEach
            children.getOrPut(ppid) { mutableListOf() }.add(pid)
        }
        val out = mutableListOf<Int>()
        val queue = ArrayDeque<Int>().apply { add(rootPid) }
        while (queue.isNotEmpty()) {
            for (c in children[queue.removeFirst()].orEmpty()) {
                if (c !in out) { out += c; queue += c }
            }
        }
        return out
    }
}
