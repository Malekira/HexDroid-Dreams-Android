package com.boxlabs.hexdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.boxlabs.hexdroid.TlsCertificateApproval
import java.text.DateFormat
import java.util.Date

@Composable
internal fun CertificateApprovalDialog(request:TlsCertificateApproval,onAccept:()->Unit,onReject:()->Unit) {
    val format=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
    AlertDialog(onDismissRequest={ if(!request.saving)onReject() },modifier=Modifier.testTag("tls-certificate-dialog"),
        title={ Text("Certificado SSL/TLS") },
        text={ SelectionContainer { Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${request.host}:${request.port}",style=MaterialTheme.typography.titleSmall)
            Text(request.reason)
            Text("Titular: ${request.certificate.subject}\nEmisor: ${request.certificate.issuer}")
            Text("Válido desde: ${format.format(Date(request.certificate.notBefore))}\nHasta: ${format.format(Date(request.certificate.notAfter))}")
            Text("Huella SHA-256:\n${request.certificate.fingerprint}")
            Text(if(request.allowed) "Acepta solo si reconoces el servidor y su certificado. Se guardará esta huella para este servidor y puerto; si cambia, volveremos a avisarte."
                else "Este servidor aplica una política STS que exige un certificado válido. No permite autorizar excepciones.")
            request.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        } } },
        confirmButton={ if(request.allowed) TextButton(onClick=onAccept,enabled=!request.saving,modifier=Modifier.testTag("tls-accept")) { Text(if(request.saving) "Guardando…" else "Aceptar certificado") } },
        dismissButton={ TextButton(onClick=onReject,enabled=!request.saving,modifier=Modifier.testTag("tls-reject")) { Text("Cancelar") } })
}
