/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.boxlabs.hexdroid.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class Dreams109HistoryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private suspend fun until(condition: () -> Boolean) { withTimeout(15_000) { while (!condition()) delay(25) } }

    private suspend fun setTestTreeAccess(grant: Boolean): Uri {
        val source = instrumentation.context.packageName
        val tree = DocumentsContract.buildTreeDocumentUri(source + ".historytest", "root")
        val command = "am start -W -n $source/com.boxlabs.hexdroid.HistoryTestGrantActivity --es targetPackage ${context.packageName} --ez grant $grant"
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
        val modes = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        until { (context.checkUriPermission(tree, android.os.Process.myPid(), android.os.Process.myUid(), modes) == android.content.pm.PackageManager.PERMISSION_GRANTED) == grant }
        if (grant) context.contentResolver.takePersistableUriPermission(tree, modes)
        return tree
    }

    private suspend fun withModel(block: suspend (IrcViewModel, SettingsRepository, String) -> Unit) {
        val repo = SettingsRepository(context)
        val networks = repo.networksFlow.first()
        val settings = repo.settingsFlow.first()
        val id = "history-options-${java.util.UUID.randomUUID()}"
        val net = repo.defaultNetworks().first().copy(id = id, name = "Historial de prueba", host = "irc.example.invalid", autoConnect = false)
        val store = ViewModelStore()
        lateinit var vm: IrcViewModel
        var created = false
        try {
            repo.saveNetworks(listOf(net))
            repo.updateSettings { it.copy(loggingEnabled = true, logFolderUri = null, logDailyFiles = true, logCombineConversations = false,
                notificationsEnabled = false, keepAliveInBackground = false, showConnectionStatusNotification = false,
                autoReconnectEnabled = false, smartFilter = false, hideJoins = false, hideParts = false, hideQuits = false).withPersonal { copy(encryptedLogEnabled = false) } }
            main { vm = IrcViewModel(repo, context); store.put("history-options", vm); created = true }
            until { vm.state.value.settingsLoaded && vm.state.value.networks.any { it.id == id } && vm.state.value.settings.logDailyFiles }
            block(vm, repo, id)
        } finally {
            if (created) {
                withContext(vm.logLane) { }
                val canonical = vm.historyArchive.list().filter { it.path.startsWith(vm.personalLogNetwork(id) + "/") }.map { it.path }.toSet()
                vm.historyArchive.delete(canonical)
                vm.organizedLogs.closeAll()
                // Test-only network directories are unique; leave unrelated test/app histories alone.
                val target = organizedHistoryTarget(id, net.name, "#demo", 0, false, false, "")
                vm.organizedLogs.logFileInternal(target.network, "#demo").parentFile.deleteRecursively()
                main { store.clear(); vm.privateRequests.close(); vm.historyArchive.close() }
            }
            repo.saveNetworks(networks); repo.updateSettings { settings }
        }
    }

    @Test fun dreams109OrganizedTxtWritesRotateByEventDayAndNeverDuplicateAReplay() = runBlocking {
        withModel { vm, _, id ->
            val ts = Instant.parse("2026-11-04T12:00:00Z").toEpochMilli()
            val first = organizedHistoryTarget(id, "Historial de prueba", "#demo", ts, true, false, "")
            val second = organizedHistoryTarget(id, "Historial de prueba", "#demo", ts + 86_400_000, true, false, "")
            coroutineScope {
                val writerScope = this
                main {
                    vm.writeLogLine("$id::#demo", ts, "DemoNick01", "Hola ñ 🙂", false, scope = writerScope, msgId = "organized-demo-1")
                    vm.writeLogLine("$id::#demo", ts, "DemoNick01", "Hola ñ 🙂", false, scope = writerScope, msgId = "organized-demo-1", replay = true)
                    vm.writeLogLine("$id::#demo", ts + 86_400_000, "DemoNick02", "Otro día", false, scope = writerScope, msgId = "organized-demo-2")
                }
            }
            val file = vm.organizedLogs.logFileInternal(first.network, first.buffer)
            assertEquals(1, file.readLines(Charsets.UTF_8).size)
            assertTrue(file.readText(Charsets.UTF_8).contains("<DemoNick01> Hola ñ 🙂"))
            assertTrue(vm.organizedLogs.logFileInternal(second.network, second.buffer).readText().contains("Otro día"))
            val canonical = vm.logs.logFileInternal(vm.personalLogNetwork(id), "#demo")
            assertEquals(2, canonical.readLines(Charsets.UTF_8).size)
        }
    }

    @Test fun dreams109CombinedFilesIncludeTheirConversationAndKeepThePrivateArchive() = runBlocking {
        withModel { vm, repo, id ->
            repo.updateSettings { it.copy(logCombineConversations = true, logDailyFiles = true) }
            until { vm.state.value.settings.logCombineConversations }
            val ts = Instant.parse("2026-11-04T12:00:00Z").toEpochMilli()
            val target = organizedHistoryTarget(id, "Historial de prueba", "#demo", ts, true, true, "")
            val file = vm.organizedLogs.logFileInternal(target.network, target.buffer)
            val old = if (file.exists()) file.readBytes() else null
            try {
                coroutineScope {
                    val writerScope = this
                    main {
                        vm.writeLogLine("$id::#demo", ts, "DemoNick01", "Sala de prueba", false, scope = writerScope, msgId = "combined-room-$id")
                        vm.writeLogLine("$id::DemoNick02", ts, "DemoNick02", "Privado de prueba", false, scope = writerScope, msgId = "combined-pv-$id")
                    }
                }
                val text = file.readText(Charsets.UTF_8)
                assertTrue(text.contains(" / #demo]")); assertTrue(text.contains(" / demonick02]"))
                assertTrue(text.contains("Privado de prueba"))
                assertTrue(vm.logs.logFileInternal(vm.personalLogNetwork(id), "#demo").exists())
                assertTrue(vm.logs.logFileInternal(vm.personalLogNetwork(id), "demonick02").exists())
            } finally { vm.organizedLogs.closeAll(); if (old == null) file.delete() else file.writeBytes(old) }
        }
    }

    @Test fun dreams109FolderAndLayoutOptionsPersistInTheRepositoryAndCompatibleBackups() = runBlocking {
        val repo = SettingsRepository(context)
        val old = repo.settingsFlow.first()
        val networks = repo.networksFlow.first()
        try {
            val uri = "content://example.invalid/tree/demo"
            repo.updateSettings { it.copy(logFolderUri = uri, logDailyFiles = true, logCombineConversations = true,
                chatZonaAvatarsEnabled = false, chatZonaAvatarsInMessages = true) }
            val saved = SettingsRepository(context).settingsFlow.first()
            assertEquals(uri, saved.logFolderUri); assertTrue(saved.logDailyFiles); assertTrue(saved.logCombineConversations)
            assertFalse(saved.chatZonaAvatarsEnabled); assertTrue(saved.chatZonaAvatarsInMessages)
            val backup = repo.exportBackupJson(networks, saved)
            repo.updateSettings { it.copy(logFolderUri = null, logDailyFiles = false, logCombineConversations = false) }
            repo.importBackup(backup)
            val restored = repo.settingsFlow.first()
            assertEquals(uri, restored.logFolderUri); assertTrue(restored.logDailyFiles); assertTrue(restored.logCombineConversations)
            val legacy = JSONObject(backup)
            val obj = legacy.getJSONObject("settings")
            listOf("logDailyFiles", "logCombineConversations", "chatZonaAvatarsEnabled", "chatZonaAvatarsInMessages").forEach { obj.remove(it) }
            repo.importBackup(legacy.toString())
            assertFalse(repo.settingsFlow.first().logDailyFiles); assertFalse(repo.settingsFlow.first().logCombineConversations)
        } finally { repo.saveNetworks(networks); repo.updateSettings { old } }
    }

    @Test fun dreams109ActualSafProviderCreatesAndAppendsUnicodeTextInTheChosenFolder() = runBlocking {
        val tree = setTestTreeAccess(true)
        val writer = LogWriter(context, "saf-test-private", flushEachLine = true)
        val group = "saf-demo-${java.util.UUID.randomUUID()}"
        try {
            assertNull(writer.append(group, "#demo", "Primero ñ 🙂", tree.toString()))
            assertNull(writer.append(group, "#demo", "Segundo mensaje", tree.toString()))
            writer.closeAll()
            val rootChildren = DocumentsContract.buildChildDocumentsUriUsingTree(tree, "root")
            val dirId = context.contentResolver.query(rootChildren, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)!!.use { cursor ->
                var id: String? = null
                while (cursor.moveToNext()) if (cursor.getString(1) == group) id = cursor.getString(0)
                requireNotNull(id)
            }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirId)
            val fileId = context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)!!.use { it.moveToFirst(); it.getString(0) }
            val file = DocumentsContract.buildDocumentUriUsingTree(tree, fileId)
            val text = context.contentResolver.openInputStream(file)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
            assertEquals("Primero ñ 🙂\nSegundo mensaje\n", text)
            DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, dirId))
        } finally { writer.closeAll(); setTestTreeAccess(false) }
    }

    @Test fun dreams109MissingFolderReportsFailureWhileCanonicalHistoryStillRecordsTheMessage() = runBlocking {
        withModel { vm, repo, id ->
            val uri = DocumentsContract.buildTreeDocumentUri(instrumentation.context.packageName + ".historytest", "missing-${java.util.UUID.randomUUID()}").toString()
            repo.updateSettings { it.copy(logFolderUri = uri) }
            until { vm.state.value.settings.logFolderUri == uri }
            coroutineScope {
                val writerScope = this
                main { vm.writeLogLine("$id::#demo", System.currentTimeMillis(), "DemoNick01", "Conservar ante error", false, scope = writerScope, msgId = "missing-$id") }
            }
            assertTrue(vm.logs.logFileInternal(vm.personalLogNetwork(id), "#demo").readText().contains("Conservar ante error"))
            until { vm.state.value.logFolderUnreadable }
            assertTrue(vm.state.value.buffers.getValue("$id::*server*").messages.any { it.text.contains("historial privado se conservó") })
        }
    }

    @Test fun dreams109RegrantingTheSameFolderAllowsWritesAgainWithoutChangingItsSavedUri() = runBlocking {
        withModel { vm, repo, id ->
            val uri = setTestTreeAccess(false).toString()
            repo.updateSettings { it.copy(logFolderUri = uri) }
            until { vm.state.value.settings.logFolderUri == uri }
            val ts = System.currentTimeMillis()
            coroutineScope {
                val writerScope = this
                main { vm.writeLogLine("$id::#demo", ts, "DemoNick01", "Antes del permiso", false, scope = writerScope, msgId = "before-grant-$id") }
            }
            until { vm.state.value.logFolderUnreadable }
            setTestTreeAccess(true)
            try {
                main { vm.onLogFolderGranted(uri) }
                coroutineScope {
                    val writerScope = this
                    main { vm.writeLogLine("$id::#demo", ts + 1000, "DemoNick01", "Después del permiso", false, scope = writerScope, msgId = "after-grant-$id") }
                }
                until { !vm.state.value.logFolderUnreadable }
                assertEquals(uri, repo.settingsFlow.first().logFolderUri)
                assertEquals(2, vm.logs.logFileInternal(vm.personalLogNetwork(id), "#demo").readLines().size)
            } finally { setTestTreeAccess(false) }
        }
    }

    @Test fun dreams109OrganizedCopiesRespectSecretRedactionAndTheEncryptedLoggingPreference() = runBlocking {
        withModel { vm, _, id ->
            val ts = System.currentTimeMillis()
            coroutineScope {
                val writerScope = this
                main {
                    vm.writeLogLine("$id::#demo", ts, "OwnDemoNick", "/identify not-a-real-secret", false, scope = writerScope)
                    vm.writeLogLine("$id::NickServ", ts, "OwnDemoNick", "IDENTIFY not-a-real-secret", false, scope = writerScope)
                    vm.writeLogLine("$id::#demo", ts, "DemoNick01", "token=not-a-real-token", false, scope = writerScope)
                    vm.writeLogLine("$id::#demo", ts, "DemoNick02", "Texto cifrado de prueba", false, scope = writerScope, encryption = com.boxlabs.hexdroid.crypto.E2eScheme.AGM)
                }
            }
            val target = organizedHistoryTarget(id, "Historial de prueba", "#demo", ts, true, false, "")
            val text = vm.organizedLogs.logFileInternal(target.network, target.buffer).readText(Charsets.UTF_8)
            assertTrue(text.contains("[REDACTADO]"))
            assertFalse(text.contains("not-a-real-token")); assertFalse(text.contains("not-a-real-secret"))
            assertFalse(text.contains("Texto cifrado de prueba"))
            assertEquals(1, text.lineSequence().filter { it.isNotBlank() }.count())
        }
    }

    @Test fun dreams109MembershipEventsStayVisibleWithoutUnreadButOrdinaryMessagesStillCount() = runBlocking {
        withModel { vm, repo, id ->
            repo.updateSettings { it.copy(ircHistoryCountsAsUnread = true) }
            until { vm.state.value.settings.ircHistoryCountsAsUnread }
            val room = "$id::#demo"
            main {
                vm._state.value = vm.state.value.copy(screen = AppScreen.NETWORKS, activeNetworkId = id,
                    selectedBuffer = "", buffers = mapOf(room to UiBuffer("#demo")), connections = mapOf(id to NetConnState(myNick = "OwnDemoNick")))
                vm.handleEvent(id, IrcEvent.Joined("#demo", "DemoNick01", "ident@host.invalid"))
                vm.handleEvent(id, IrcEvent.Parted("#demo", "DemoNick01", "ident@host.invalid", "Salida"))
                vm.handleEvent(id, IrcEvent.Joined("#demo", "DemoNick02", "ident@host.invalid"))
                vm.handleEvent(id, IrcEvent.Quit("DemoNick02", "ident@host.invalid", "Fin"))
                assertEquals(0, vm.state.value.buffers.getValue(room).unread)
                assertEquals(0, vm.state.value.buffers.getValue(room).highlights)
                assertEquals(4, vm.state.value.buffers.getValue(room).messages.size)
                vm.handleEvent(id, IrcEvent.ChatMessage("DemoNick03", "#demo", "Mensaje de prueba", isPrivate = false, msgId = "ordinary-$id"))
                assertEquals(1, vm.state.value.buffers.getValue(room).unread)
            }
        }
    }
}
