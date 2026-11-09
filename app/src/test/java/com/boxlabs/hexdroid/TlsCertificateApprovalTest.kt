package com.boxlabs.hexdroid

import kotlinx.coroutines.*
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.*
import org.junit.Assert.*
import org.junit.Test

class TlsCertificateApprovalTest {
    private val keys get()=KeyStore.getInstance("PKCS12").apply {
        TlsCertificateApprovalTest::class.java.getResourceAsStream("/tls/self-signed.p12")!!.use { load(it,"test-only-password".toCharArray()) }
    }
    private class Server(keys:KeyStore):AutoCloseable {
        private val context=SSLContext.getInstance("TLS").apply {
            val km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"test-only-password".toCharArray());init(km.keyManagers,null,null)
        }
        val socket=context.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        val lines=CopyOnWriteArrayList<String>()
        @Volatile var peer:SSLSocket?=null
        init { kotlin.concurrent.thread(isDaemon=true) { runCatching {
            (socket.accept() as SSLSocket).use { s -> peer=s;s.soTimeout=5000;s.startHandshake()
                val reader=s.inputStream.bufferedReader()
                while(true) { val line=reader.readLine() ?: break;lines+=line
                    if(line.startsWith("CAP LS")) { s.outputStream.write(":srv CAP * LS :\r\n".toByteArray());s.outputStream.flush() }
                }
            }
        } } }
        override fun close() { runCatching { peer?.close() };socket.close() }
    }
    private fun client(port:Int,allow:Boolean=false,pin:String?=null)=IrcClient(IrcConfig(host="localhost",port=port,useTls=true,allowInvalidCerts=allow,
        nick="me",altNick=null,username="u",realname="r",serverPassword="secret-never-send",tlsTofuFingerprint=pin)).apply { strings={id,_->"s$id"};plurals={id,q,_->"p$id/$q"} }
    private suspend fun waitFor(condition:()->Boolean) { withTimeout(15000) { while(!condition())delay(10) } }
    @Test fun untrustedCertificateRequestsApprovalBeforeSendingIrcCredentials()=runBlocking {
        Server(keys).use { server ->
            val client=client(server.socket.localPort);val events=CopyOnWriteArrayList<IrcEvent>()
            val job=launch(Dispatchers.IO) { client.events().collect { events+=it } }
            try {
                waitFor { events.any { it is IrcEvent.CertificateApprovalRequired } }
                val request=events.filterIsInstance<IrcEvent.CertificateApprovalRequired>().single().request
                assertEquals("localhost",request.host);assertEquals(server.socket.localPort,request.port)
                assertTrue(validCertificateFingerprint(request.certificate.fingerprint));assertTrue(request.certificate.subject.contains("localhost"))
                assertTrue(server.lines.isEmpty());assertTrue(events.none { it is IrcEvent.Registered })
            } finally { client.forceClose();job.cancelAndJoin() }
        }
    }
    @Test fun acceptedExactFingerprintAllowsHandshakeButAnotherFingerprintRequiresApprovalAgain()=runBlocking {
        val keys=keys;val fingerprint=computeFingerprintSha256(keys.getCertificate("test") as X509Certificate)
        for(pin in listOf(fingerprint,"00:".repeat(31)+"00")) Server(keys).use { server ->
            val client=client(server.socket.localPort,true,pin);val events=CopyOnWriteArrayList<IrcEvent>()
            val job=launch(Dispatchers.IO) { client.events().collect { events+=it } }
            try {
                if(pin==fingerprint) { waitFor { server.lines.any { it.startsWith("NICK ") } };assertTrue(events.none { it is IrcEvent.CertificateApprovalRequired }) }
                else { waitFor { events.any { it is IrcEvent.CertificateApprovalRequired } };assertTrue(server.lines.isEmpty());assertEquals(fingerprint,events.filterIsInstance<IrcEvent.CertificateApprovalRequired>().single().request.certificate.fingerprint) }
            } finally { client.forceClose();job.cancelAndJoin() }
        }
    }
    @Test fun stsDisallowsCertificateClickThrough()=runBlocking {
        Server(keys).use { server ->
            val config=IrcConfig(host="localhost",port=server.socket.localPort,useTls=true,allowInvalidCerts=false,nick="me",altNick=null,username="u",realname="r",certificateApprovalAllowed=false)
            val client=IrcClient(config).apply { strings={id,_->"s$id"} };val events=CopyOnWriteArrayList<IrcEvent>()
            val job=launch(Dispatchers.IO) { client.events().collect { events+=it } }
            try { waitFor { events.any { it is IrcEvent.CertificateApprovalRequired } };assertFalse(events.filterIsInstance<IrcEvent.CertificateApprovalRequired>().single().request.allowed);assertTrue(server.lines.isEmpty()) }
            finally { client.forceClose();job.cancelAndJoin() }
        }
    }
}
