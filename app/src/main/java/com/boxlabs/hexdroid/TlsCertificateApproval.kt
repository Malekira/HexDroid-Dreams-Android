package com.boxlabs.hexdroid

import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Public certificate information only; no key material or IRC credentials. */
data class TlsCertificateDetails(val subject:String,val issuer:String,val notBefore:Long,val notAfter:Long,val fingerprint:String) {
    companion object {
        fun from(cert:X509Certificate)=TlsCertificateDetails(cert.subjectX500Principal.name,cert.issuerX500Principal.name,
            cert.notBefore.time,cert.notAfter.time,computeFingerprintSha256(cert))
    }
}
data class TlsCertificateApproval(val host:String,val port:Int,val certificate:TlsCertificateDetails,val reason:String,
    val allowed:Boolean=true,val id:String=java.util.UUID.randomUUID().toString(),val saving:Boolean=false,val error:String?=null)

internal class CertificateApprovalException(val certificate:TlsCertificateDetails,cause:CertificateException):CertificateException(cause.message,cause)

/** Keep platform trust validation; expose the presented certificate when it rejects the chain. */
internal class ApprovalTrustManager(private val delegate:X509TrustManager):javax.net.ssl.X509ExtendedTrustManager() {
    override fun getAcceptedIssuers()=delegate.acceptedIssuers
    override fun checkClientTrusted(chain:Array<X509Certificate>,authType:String)=delegate.checkClientTrusted(chain,authType)
    private fun verify(chain:Array<X509Certificate>,block:()->Unit) {
        try { block() }
        catch(e:CertificateException) { throw chain.firstOrNull()?.let { CertificateApprovalException(TlsCertificateDetails.from(it),e) } ?: e }
    }
    override fun checkServerTrusted(chain:Array<X509Certificate>,authType:String)=verify(chain) { delegate.checkServerTrusted(chain,authType) }
    override fun checkServerTrusted(chain:Array<X509Certificate>,authType:String,socket:java.net.Socket)=verify(chain) {
        if(delegate is javax.net.ssl.X509ExtendedTrustManager)delegate.checkServerTrusted(chain,authType,socket) else delegate.checkServerTrusted(chain,authType)
    }
    override fun checkServerTrusted(chain:Array<X509Certificate>,authType:String,engine:javax.net.ssl.SSLEngine)=verify(chain) {
        if(delegate is javax.net.ssl.X509ExtendedTrustManager)delegate.checkServerTrusted(chain,authType,engine) else delegate.checkServerTrusted(chain,authType)
    }
    override fun checkClientTrusted(chain:Array<X509Certificate>,authType:String,socket:java.net.Socket) {
        if(delegate is javax.net.ssl.X509ExtendedTrustManager)delegate.checkClientTrusted(chain,authType,socket) else delegate.checkClientTrusted(chain,authType)
    }
    override fun checkClientTrusted(chain:Array<X509Certificate>,authType:String,engine:javax.net.ssl.SSLEngine) {
        if(delegate is javax.net.ssl.X509ExtendedTrustManager)delegate.checkClientTrusted(chain,authType,engine) else delegate.checkClientTrusted(chain,authType)
    }
    companion object {
        fun platform():ApprovalTrustManager {
            val factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return ApprovalTrustManager(factory.trustManagers.filterIsInstance<X509TrustManager>().first())
        }
    }
}
internal fun certificateApprovalCause(error:Throwable):CertificateApprovalException? {
    val seen=HashSet<Throwable>();var next:Throwable?=error
    while(next!=null && seen.add(next)) { if(next is CertificateApprovalException)return next;next=next.cause }
    return null
}
internal fun validCertificateFingerprint(value:String)=value.replace(":","").matches(Regex("[a-fA-F0-9]{64}"))
internal fun com.boxlabs.hexdroid.data.NetworkProfile.acceptCertificate(request:TlsCertificateApproval):com.boxlabs.hexdroid.data.NetworkProfile? {
    if(!request.allowed || !useTls || !host.trim().equals(request.host.trim(),true) || port!=request.port || !validCertificateFingerprint(request.certificate.fingerprint))return null
    return copy(allowInvalidCerts=true,tlsTofuFingerprint=request.certificate.fingerprint,tlsTofuFingerprints=emptySet(),
        tlsApprovalHost=host.trim().lowercase(java.util.Locale.ROOT),tlsApprovalPort=port,tlsHostnameGrace=false,tlsAcceptedIdentities=emptySet())
}
