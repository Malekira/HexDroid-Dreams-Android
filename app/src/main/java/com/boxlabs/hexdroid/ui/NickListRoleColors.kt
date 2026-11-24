/* HexDreams IRC — GPL-3.0-or-later. */
package com.boxlabs.hexdroid.ui

import androidx.compose.ui.graphics.Color
import com.boxlabs.hexdroid.NickListRole

internal fun nickListRoleColor(role: NickListRole, backgroundLuminance: Float, normal: Color): Color {
    val dark = backgroundLuminance < 0.35f
    return when (role) {
        NickListRole.ADMIN -> if (dark) Color(0xFFFF8A80) else Color(0xFFAF2020)
        NickListRole.IRC_OPER -> if (dark) Color(0xFFD3B5FF) else Color(0xFF7134A8)
        NickListRole.BOT -> if (dark) Color(0xFF5EEAD4) else Color(0xFF006B60)
        NickListRole.OP -> if (dark) Color(0xFF82B9FF) else Color(0xFF175AA0)
        NickListRole.HALFOP -> if (dark) Color(0xFF70D6EA) else Color(0xFF00687B)
        NickListRole.VOICE -> if (dark) Color(0xFFA3D982) else Color(0xFF356A17)
        NickListRole.NORMAL -> normal
    }
}
