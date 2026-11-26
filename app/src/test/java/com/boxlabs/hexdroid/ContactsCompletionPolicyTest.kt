package com.boxlabs.hexdroid

import org.junit.Assert.*
import org.junit.Test

class ContactsCompletionPolicyTest {
    private val decorated = NickCompletionStyle(startTemplate = "«{nick}» ",middleTemplate = "[{nick}] ",
        colors = "4,9,12",bold = true)
    @Test fun noticeAndMessageRecipientsStayPlainWhileCycling() {
        for (command in listOf("notice","msg","query","whois","invite","ignore","ctcp")) {
            val draft = "/" + command + " Al"
            val first = advanceNickCompletion(draft,draft.length,listOf("@Alice","+Alfred"),null,style = decorated)!!
            assertEquals("/" + command + " Alfred ",first.text)
            assertFalse(first.text.any { it.isISOControl() })
            val next = advanceNickCompletion(first.text,first.cursor,listOf("@Alice","+Alfred"),first,style = decorated)!!
            assertEquals("/" + command + " Alice ",next.text)
        }
    }
    @Test fun commandTargetsPreserveTextOnBothSidesAndCommaSeparatedNicks() {
        val text = "/notice Bob,Al mensaje intacto"
        val token = nickToken(text,text.indexOf(" mensaje"))!!
        assertEquals("/notice Bob,Alice mensaje intacto",completeNickToken(text,token,"@Alice",decorated).first)
        val kick = "/kick #dreams Al razón intacta"
        assertEquals("/kick #dreams Alice razón intacta",
            completeNickToken(kick,nickToken(kick,kick.indexOf(" razón"))!!,"Alice",decorated).first)
        assertEquals("/notice Alice ",completeNickToken("/notice ",nickToken("/notice ",8)!!,"Alice",decorated).first)
    }
    @Test fun commandBodiesKeepMulticolorButBotCommandsRemainPlain() {
        val text = "/msg Bob Al"
        val result = completeNickToken(text,nickToken(text,text.length)!!,"Alice",decorated).first
        assertEquals("/msg Bob [Alice] ",stripIrcFormatting(result))
        assertTrue(result.contains("\u000304"))
        val bot = "/msg Nick Ghost Al"
        assertEquals("/msg Nick Ghost Alice ",completeNickToken(bot,nickToken(bot,bot.length)!!,"Alice",decorated).first)
    }
    @Test fun staffPrefixesAndIrcCaseMappingAreRecognized() {
        assertEquals(listOf("Alice"),completionMatches(listOf("!Alice"),"Al","ascii"))
        assertEquals(2,completionMatches(listOf("[Alice]","{Bob}"),"[","rfc1459").size)
        assertEquals(listOf("[Alice]"),completionMatches(listOf("[Alice]","{Bob}"),"[","ascii"))
        assertNull(nickToken("hola ",5))
    }
    @Test fun contactsAreValidatedDeduplicatedAndBoundedWithoutLeakingAcrossCaseMappings() {
        assertEquals(listOf("Alice","[Bob]","{Bob}"),cleanNotifyContacts(listOf("@Alice","alice","bad nick","[Bob]","{Bob}")))
        assertEquals(listOf("[Bob]"),cleanNotifyContacts(listOf("[Bob]","{Bob}"),"rfc1459"))
        assertEquals(64,cleanNotifyContacts((1..100).map { "Nick" + it }).size)
    }
    @Test fun onlyRealContactTransitionsAlert() {
        assertFalse(contactTransition(null,true)); assertFalse(contactTransition(null,false))
        assertFalse(contactTransition(true,true)); assertFalse(contactTransition(false,false))
        assertTrue(contactTransition(false,true)); assertTrue(contactTransition(true,false))
    }
    @Test fun monitorBatchesRespectIrcLineLimitsAndNeverClearManualWatchLists() {
        val nicks = (1..64).map { "Nickname_abcdefghijklmnop" + it }
        val lines = monitorContactCommands('+',nicks)
        assertTrue(lines.size > 1)
        assertTrue(lines.all { it.toByteArray(Charsets.UTF_8).size <= 400 && it.startsWith("MONITOR + ") })
        assertEquals(nicks,lines.flatMap { it.substringAfter("MONITOR + ").split(",") })
        assertEquals(listOf("MONITOR - Alice"),monitorContactCommands('-',listOf("Alice")))
        assertTrue(monitorContactCommands('+',emptyList()).isEmpty())
    }
}
