package com.boxlabs.hexdroid

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

fun IrcViewModel.rejectCertificate(netId:String,requestId:String) {
    val pending=state.value.connections[netId]?.certificateApproval ?: return
    if(pending.id!=requestId || pending.saving)return
    setNetConn(netId) { it.copy(certificateApproval=null) }
}
fun IrcViewModel.approveCertificate(netId:String,requestId:String) {
    val request=state.value.connections[netId]?.certificateApproval ?: return
    if(request.id!=requestId || request.saving || !request.allowed)return
    val profile=state.value.networks.firstOrNull { it.id==netId } ?: return
    if(profile.acceptCertificate(request)==null) { rejectCertificate(netId,requestId);return }
    setNetConn(netId) { it.copy(certificateApproval=request.copy(saving=true,error=null)) }
    viewModelScope.launch {
        try {
            withNetLock(netId) {
                check(state.value.connections[netId]?.certificateApproval?.id==requestId) { "La solicitud de certificado cambió" }
                var saved=false
                repo.updateNetworkProfile(netId) { current ->
                    current.acceptCertificate(request)?.also { saved=true } ?: current
                }
                check(saved) { "El servidor cambió. Vuelve a conectar para revisar su certificado." }
                val current=state.value.networks.firstOrNull { it.id==netId }
                val accepted=current?.acceptCertificate(request)
                check(accepted!=null) { "El servidor cambió durante la autorización" }
                updateNetworkInState(accepted)
                setNetConn(netId) { it.copy(certificateApproval=null,tlsPinMismatch=false,tlsPinMismatchActualFp=null,tlsHostnameMismatchIdentities=null) }
            }
            connectNetwork(netId,force=true,clearAuthBlock=true)
        } catch(e:Exception) {
            setNetConn(netId) { conn ->
                if(conn.certificateApproval?.id!=requestId)conn else conn.copy(certificateApproval=request.copy(error="No se pudo guardar la autorización: ${e.message}"))
            }
        }
    }
}
