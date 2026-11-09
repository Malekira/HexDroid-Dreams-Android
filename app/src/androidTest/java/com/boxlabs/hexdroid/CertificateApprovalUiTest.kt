package com.boxlabs.hexdroid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.boxlabs.hexdroid.ui.CertificateApprovalDialog
import com.boxlabs.hexdroid.ui.theme.HexDroidIRCTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.flow.first

class CertificateApprovalUiTest {
    @get:Rule val compose=createComposeRule()
    private fun request(allowed:Boolean=true)=TlsCertificateApproval("irc.example",6697,TlsCertificateDetails("CN=irc.example","CN=issuer",0,4102444800000,"ab:".repeat(31)+"ab"),"Certificado no reconocido",allowed)
    @Test fun certificateDialogShowsEndpointAndFingerprintAndRequiresExplicitAcceptance() {
        val request=request();var accepted=0;var rejected=0
        compose.setContent { HexDroidIRCTheme { CertificateApprovalDialog(request,{accepted++},{rejected++}) } }
        compose.onNodeWithText("irc.example:6697").assertExists()
        compose.onNodeWithText(request.certificate.fingerprint,substring=true).assertExists()
        compose.runOnIdle { assertEquals(0,accepted) }
        compose.onNodeWithTag("tls-accept").performClick()
        compose.runOnIdle { assertEquals(1,accepted);assertEquals(0,rejected) }
    }
    @Test fun cancelNeverAcceptsAndStsHasNoAcceptButton() {
        var accepted=0;var rejected=0
        compose.setContent { HexDroidIRCTheme { CertificateApprovalDialog(request(false),{accepted++},{rejected++}) } }
        compose.onNodeWithTag("tls-accept").assertDoesNotExist()
        compose.onNodeWithTag("tls-reject").performClick()
        compose.runOnIdle { assertEquals(0,accepted);assertEquals(1,rejected) }
    }
    @Test fun approvedCertificateIsBoundToItsEndpointAndSurvivesStorage()=kotlinx.coroutines.runBlocking {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val repo=com.boxlabs.hexdroid.data.SettingsRepository(context)
        val profile=repo.defaultNetworks().first().copy(id="tls-test-${java.util.UUID.randomUUID()}",host="irc.example",port=6697,useTls=true)
        val request=request()
        val approved=profile.acceptCertificate(request)!!
        assertTrue(approved.toIrcConfig().allowInvalidCerts)
        assertEquals(request.certificate.fingerprint,approved.tlsTofuFingerprint)
        assertFalse(approved.copy(host="another.example").toIrcConfig().allowInvalidCerts)
        assertFalse(approved.copy(port=7000).toIrcConfig().allowInvalidCerts)
        assertNull(profile.acceptCertificate(request.copy(host="other.example")))
        assertNull(profile.acceptCertificate(request.copy(allowed=false)))
        try {
            repo.upsertNetwork(approved)
            val restored=repo.networksFlow.first().first { it.id==profile.id }
            assertEquals(approved.tlsApprovalHost,restored.tlsApprovalHost);assertEquals(6697,restored.tlsApprovalPort)
            assertEquals(approved.tlsTofuFingerprint,restored.tlsTofuFingerprint)
        } finally { repo.deleteNetwork(profile.id) }
    }
}
