/* HexDroidIRC — Copyright (C) 2026 boxlabs. GPL-3.0-or-later. */
package com.boxlabs.hexdroid;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;

/** Test APK only: the document owner grants/revokes the target's real tree capability. */
public class HistoryTestGrantActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String own = getPackageName();
        String target = own.endsWith(".test") ? own.substring(0, own.length() - 5) : "";
        if (!target.isEmpty() && target.equals(getIntent().getStringExtra("targetPackage"))) {
            Uri tree = DocumentsContract.buildTreeDocumentUri(own + ".historytest", "root");
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            if (getIntent().getBooleanExtra("grant", false)) {
                grantUriPermission(target, tree, flags | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            } else {
                revokeUriPermission(target, tree, flags);
            }
        }
        finish();
    }
}
