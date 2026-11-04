/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Test APK only. Java avoids depending on the target APK's Kotlin runtime in this separate process. */
public class HistoryTestDocumentsProvider extends DocumentsProvider {
    private File base;
    private static final String[] COLUMNS = { Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE };

    @Override public boolean onCreate() {
        try { base = new File(getContext().getFilesDir(), "test-saf-history").getCanonicalFile(); }
        catch (IOException e) { return false; }
        return base.isDirectory() || base.mkdirs();
    }
    private File file(String id) throws FileNotFoundException {
        try {
            File result = id.equals("root") ? base : new File(base, id).getCanonicalFile();
            if (!result.getCanonicalPath().equals(base.getCanonicalPath()) &&
                !result.getCanonicalPath().startsWith(base.getCanonicalPath() + File.separator)) throw new FileNotFoundException(id);
            return result;
        } catch (IOException e) { throw new FileNotFoundException(id); }
    }
    private String id(File file) {
        return file.equals(base) ? "root" : file.getAbsolutePath().substring(base.getAbsolutePath().length() + 1);
    }
    private void row(MatrixCursor cursor, File file) {
        Map<String, Object> values = new HashMap<>();
        values.put(Document.COLUMN_DOCUMENT_ID, id(file));
        values.put(Document.COLUMN_DISPLAY_NAME, file.getName());
        values.put(Document.COLUMN_MIME_TYPE, file.isDirectory() ? Document.MIME_TYPE_DIR : "text/plain");
        values.put(Document.COLUMN_FLAGS, file.isDirectory() ? Document.FLAG_DIR_SUPPORTS_CREATE : Document.FLAG_SUPPORTS_WRITE);
        values.put(Document.COLUMN_SIZE, file.length());
        Object[] result = new Object[cursor.getColumnCount()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(cursor.getColumnName(i));
        cursor.addRow(result);
    }
    @Override public Cursor queryRoots(String[] projection) {
        String[] cols = projection == null ? new String[]{Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS} : projection;
        MatrixCursor cursor = new MatrixCursor(cols);
        Map<String, Object> values = new HashMap<>();
        values.put(Root.COLUMN_ROOT_ID, "history"); values.put(Root.COLUMN_DOCUMENT_ID, "root");
        values.put(Root.COLUMN_TITLE, "Historial de prueba"); values.put(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE);
        Object[] result = new Object[cols.length];
        for (int i = 0; i < result.length; i++) result[i] = values.get(cols[i]);
        cursor.addRow(result); return cursor;
    }
    @Override public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        File value = file(documentId); if (!value.exists()) throw new FileNotFoundException(documentId);
        MatrixCursor cursor = new MatrixCursor(projection == null ? COLUMNS : projection); row(cursor, value); return cursor;
    }
    @Override public Cursor queryChildDocuments(String parentId, String[] projection, String sortOrder) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(projection == null ? COLUMNS : projection);
        File parent = file(parentId); if (!parent.isDirectory()) throw new FileNotFoundException(parentId);
        File[] children = parent.listFiles();
        if (children != null) for (File child : children) row(cursor, child);
        return cursor;
    }
    @Override public String createDocument(String parentId, String mimeType, String displayName) throws FileNotFoundException {
        if (displayName.isEmpty() || displayName.contains("/") || displayName.equals(".") || displayName.equals("..")) throw new FileNotFoundException(displayName);
        File parent = file(parentId); if (!parent.isDirectory()) throw new FileNotFoundException(parentId);
        File result = new File(parent, displayName);
        try {
            if (Document.MIME_TYPE_DIR.equals(mimeType)) {
                if (!result.isDirectory() && !result.mkdir()) throw new FileNotFoundException(displayName);
            } else if (!result.exists() && !result.createNewFile()) throw new FileNotFoundException(displayName);
            return id(result);
        } catch (IOException e) { throw new FileNotFoundException(displayName); }
    }
    @Override public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode));
    }
    @Override public boolean isChildDocument(String parentId, String documentId) {
        try { return file(documentId).getAbsolutePath().startsWith(file(parentId).getAbsolutePath() + File.separator); }
        catch (FileNotFoundException e) { return false; }
    }
    private boolean delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) if (!delete(child)) return false;
        return file.delete();
    }
    @Override public void deleteDocument(String documentId) throws FileNotFoundException {
        if (documentId.equals("root") || !delete(file(documentId))) throw new FileNotFoundException(documentId);
    }
}
