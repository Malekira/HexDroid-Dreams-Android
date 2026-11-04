/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/** Installed only with the instrumentation APK; never part of the distributed app. */
class HistoryTestDocumentsProvider : DocumentsProvider() {
    private val base get() = File(context!!.filesDir, "test-saf-history").apply { mkdirs() }
    private fun file(id: String): File {
        val file = if (id == "root") base else File(base, id).canonicalFile
        require(file.canonicalPath == base.canonicalPath || file.canonicalPath.startsWith(base.canonicalPath + File.separator))
        return file
    }
    private fun id(file: File) = if (file == base) "root" else file.relativeTo(base).invariantSeparatorsPath
    private val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE)
    private fun row(cursor: MatrixCursor, file: File) {
        val values = mapOf<String, Any?>(Document.COLUMN_DOCUMENT_ID to id(file), Document.COLUMN_DISPLAY_NAME to file.name,
            Document.COLUMN_MIME_TYPE to if (file.isDirectory) Document.MIME_TYPE_DIR else "text/plain",
            Document.COLUMN_FLAGS to if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE,
            Document.COLUMN_SIZE to file.length())
        cursor.addRow(cursor.columnNames.map { values[it] }.toTypedArray())
    }
    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS)).apply {
        val values = mapOf<String, Any>(Root.COLUMN_ROOT_ID to "history", Root.COLUMN_DOCUMENT_ID to "root", Root.COLUMN_TITLE to "Historial de prueba", Root.COLUMN_FLAGS to Root.FLAG_SUPPORTS_CREATE)
        addRow(columnNames.map { values[it] }.toTypedArray())
    }
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val f = file(documentId); if (!f.exists()) throw FileNotFoundException(documentId)
        return MatrixCursor(projection ?: columns).also { row(it, f) }
    }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: columns).also { cursor -> file(parentDocumentId).listFiles()?.forEach { row(cursor, it) } }
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        require(displayName.isNotBlank() && '/' !in displayName && displayName !in setOf(".", ".."))
        val result = File(file(parentDocumentId), displayName)
        if (mimeType == Document.MIME_TYPE_DIR) check(result.mkdir()) else check(result.createNewFile())
        return id(result)
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    override fun isChildDocument(parentDocumentId: String, documentId: String) = file(documentId).canonicalPath.startsWith(file(parentDocumentId).canonicalPath + File.separator)
    override fun deleteDocument(documentId: String) { require(documentId != "root"); check(file(documentId).deleteRecursively()) }
}
