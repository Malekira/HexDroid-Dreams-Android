package com.boxlabs.hexdroid

/** Only direct text chat is supported. All file-transfer and unknown DCC verbs are consumed. */
internal fun isFileTransferCtcp(raw: String): Boolean {
    val text = raw.trim()
    if (!text.startsWith('\u0001')) return false
    val parts = text.removePrefix("\u0001").removeSuffix("\u0001").trim().split(Regex("\\s+"), limit = 3)
    return parts.firstOrNull().equals("DCC", true) &&
        parts.getOrNull(1)?.uppercase(java.util.Locale.ROOT) !in setOf("CHAT","SCHAT")
}
