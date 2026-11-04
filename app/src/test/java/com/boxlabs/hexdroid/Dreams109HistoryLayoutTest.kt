/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class Dreams109HistoryLayoutTest {
    private val utc = ZoneId.of("UTC")
    private val stamp = Instant.parse("2026-11-04T23:59:59Z").toEpochMilli()
    private fun target(daily: Boolean, combined: Boolean, time: Long = stamp, id: String = "demo-a", buffer: String = "#demo") =
        organizedHistoryTarget(id, "Red de prueba", buffer, time, daily, combined, "[fecha hora] <DemoNick01> ñ 🙂", utc)

    @Test fun dailyRotationUsesTheEventDateAndSupportsBothGroupingModes() {
        for (combined in listOf(false, true)) {
            val before = target(true, combined)
            val after = target(true, combined, stamp + 1000)
            assertTrue(before.buffer.endsWith("--2026-11-04"))
            assertTrue(after.buffer.endsWith("--2026-11-05"))
            assertNotEquals(before.buffer, after.buffer)
            assertEquals(target(false, combined).buffer, target(false, combined, stamp + 1000).buffer)
        }
    }

    @Test fun conversationFilesDistinguishNetworksWithTheSameNameAndDifferentTargets() {
        assertNotEquals(target(false, false).network, target(false, false, id = "demo-b").network)
        assertNotEquals(target(false, false).buffer, target(false, false, buffer = "DemoNick02").buffer)
        assertEquals("[fecha hora] <DemoNick01> ñ 🙂", target(false, false).line)
    }

    @Test fun combinedFilesHaveOneDestinationAndIncludeNetworkAndConversationInEveryLine() {
        val a = target(false, true)
        val b = target(false, true, id = "demo-b", buffer = "DemoNick02")
        assertEquals(a.network, b.network); assertEquals(a.buffer, b.buffer)
        assertTrue(a.line.contains("#demo")); assertTrue(b.line.contains("DemoNick02"))
        assertNotEquals(a.line, b.line)
        assertTrue(a.line.endsWith("<DemoNick01> ñ 🙂"))
    }

    @Test fun userSuppliedLabelsCannotInsertNewlinesIntoCombinedRecordHeaders() {
        val value = organizedHistoryTarget("demo-id", "Red\nextra", "#demo\r\notro", stamp, false, true, "Contenido ñ 🙂", utc)
        assertFalse(value.line.contains('\n')); assertFalse(value.line.contains('\r'))
        assertTrue(value.line.endsWith("Contenido ñ 🙂"))
    }
}
