package com.ihy2ln.weaverse.data.sync

import com.ihy2ln.weaverse.sync.SyncTls
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Trust-on-first-use TLS for LAN sync. An empty pin accepts the peer certificate
 * (so the first pair can happen); a stored SHA-256 pin rejects anything else.
 */
object SyncTlsPinning {
    fun trustManager(expectedSha256: String?): X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val cert = chain.firstOrNull() ?: error("Empty certificate chain")
            val actual = SyncTls.fingerprint(cert)
            val expected = expectedSha256?.trim().orEmpty()
            if (expected.isNotBlank() && !expected.equals(actual, ignoreCase = true)) {
                error("TLS fingerprint mismatch (TOFU pin failed)")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    fun hostnameVerifier(): HostnameVerifier = HostnameVerifier { _, _ -> true }

    fun apply(builder: OkHttpClient.Builder, expectedSha256: String?): OkHttpClient.Builder {
        val tm = trustManager(expectedSha256)
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf(tm), SecureRandom())
        return builder
            .sslSocketFactory(ctx.socketFactory, tm)
            .hostnameVerifier(hostnameVerifier())
    }
}
