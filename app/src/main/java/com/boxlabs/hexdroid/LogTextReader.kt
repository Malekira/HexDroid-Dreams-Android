/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import java.io.Reader

data class HistoryPage(val lines: List<String>, val next: Long?)

/** Stream one search page; offsets count original lines, not just matching lines. Closes the input. */
internal fun readLogPage(reader: Reader, query: String, offset: Long, size: Int = 100): HistoryPage {
    require(offset >= 0 && size in 1..1000)
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val result = mutableListOf<String>()
    var position = 0L
    var next: Long? = null
    reader.buffered().useLines { lines ->
        val iterator = lines.iterator()
        while (iterator.hasNext()) {
            val line = iterator.next()
            val at = position++
            if (at < offset || !terms.all { line.contains(it, ignoreCase = true) }) continue
            if (result.size == size) { next = at; break }
            result += line
        }
    }
    return HistoryPage(result, next)
}
