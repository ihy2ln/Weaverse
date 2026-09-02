package com.ihy2ln.weaverse.sync

import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Self-signed TLS material for optional LAN sync. The certificate is generated
 * once, persisted as PKCS12, and identified by a SHA-256 fingerprint that
 * peers pin after the PIN/password pairing step (trust-on-first-use).
 *
 * Implemented with the JDK only (no okhttp-tls) so sync-core stays a small JVM
 * module and minSdk 26 Android can load the same PKCS12.
 */
class SyncTlsMaterial(
    val keyPair: KeyPair,
    val certificate: X509Certificate,
)

object SyncTls {
    const val KEY_ALIAS = "weaverse"
    const val STORE_PASSWORD = "weaverse-sync"

    fun loadOrCreate(file: File): SyncTlsMaterial {
        if (file.exists() && file.length() > 0L) {
            return runCatching { load(file) }.getOrElse {
                file.delete()
                createAndSave(file)
            }
        }
        return createAndSave(file)
    }

    fun toKeyStore(held: SyncTlsMaterial, password: String = STORE_PASSWORD): KeyStore {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, password.toCharArray())
        ks.setKeyEntry(
            KEY_ALIAS,
            held.keyPair.private,
            password.toCharArray(),
            arrayOf(held.certificate),
        )
        return ks
    }

    fun fingerprint(cert: X509Certificate): String {
        val sha = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return sha.joinToString(":") { b -> "%02X".format(b) }
    }

    fun fingerprint(held: SyncTlsMaterial): String = fingerprint(held.certificate)

    private fun createAndSave(file: File): SyncTlsMaterial {
        val held = SelfSignedRsa.generate("Weaverse Sync", days = 3 * 365L)
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            toKeyStore(held).store(out, STORE_PASSWORD.toCharArray())
        }
        return held
    }

    private fun load(file: File): SyncTlsMaterial {
        val ks = KeyStore.getInstance("PKCS12")
        file.inputStream().use { ks.load(it, STORE_PASSWORD.toCharArray()) }
        val alias = ks.aliases().toList().first()
        val cert = ks.getCertificate(alias) as X509Certificate
        val key = ks.getKey(alias, STORE_PASSWORD.toCharArray()) as PrivateKey
        return SyncTlsMaterial(KeyPair(cert.publicKey, key), cert)
    }
}

/** Minimal SHA256-with-RSA self-signed X.509 v3 generator (JDK only). */
internal object SelfSignedRsa {
    private val sha256WithRsaOid = byteArrayOf(
        0x30, 0x0d,
        0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x0b,
        0x05, 0x00,
    )

    fun generate(commonName: String, days: Long): SyncTlsMaterial {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val kp = kpg.generateKeyPair()
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 86_400_000L)
        val notAfter = Date(now + days * 86_400_000L)
        val serial = BigInteger(64, SecureRandom()).abs().let { if (it == BigInteger.ZERO) BigInteger.ONE else it }
        val tbs = tbsCertificate(serial, commonName, notBefore, notAfter, kp)
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(kp.private)
            update(tbs)
        }.sign()
        val certDer = Der.sequence(tbs + sha256WithRsaOid + Der.bitString(signature))
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
        cert.verify(kp.public)
        return SyncTlsMaterial(kp, cert)
    }

    private fun tbsCertificate(
        serial: BigInteger,
        commonName: String,
        notBefore: Date,
        notAfter: Date,
        kp: KeyPair,
    ): ByteArray {
        val version = Der.tag(0xA0, Der.integer(BigInteger.TWO))
        val name = x500Cn(commonName)
        val validity = Der.sequence(utcTime(notBefore) + utcTime(notAfter))
        val spki = kp.public.encoded
        return Der.sequence(
            version +
                Der.integer(serial) +
                sha256WithRsaOid +
                name +
                validity +
                name +
                spki,
        )
    }

    private fun x500Cn(cn: String): ByteArray {
        // OID 2.5.4.3 = commonName
        val oid = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)
        val value = Der.tag(0x0C, cn.toByteArray(Charsets.UTF_8)) // UTF8String
        val atv = Der.sequence(oid + value)
        val rdn = Der.tag(0x31, atv) // SET
        return Der.sequence(rdn)
    }

    private fun utcTime(date: Date): ByteArray {
        val fmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return Der.tag(0x17, fmt.format(date).toByteArray(Charsets.US_ASCII))
    }
}

internal object Der {
    fun sequence(contents: ByteArray): ByteArray = tag(0x30, contents)

    fun integer(value: BigInteger): ByteArray {
        var bytes = value.toByteArray()
        if (bytes.size > 1 && bytes[0] == 0.toByte() && bytes[1] >= 0) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        return tag(0x02, bytes)
    }

    fun bitString(bytes: ByteArray): ByteArray = tag(0x03, byteArrayOf(0x00) + bytes)

    fun tag(tag: Int, contents: ByteArray): ByteArray {
        return byteArrayOf(tag.toByte()) + length(contents.size) + contents
    }

    private fun length(len: Int): ByteArray = when {
        len < 0x80 -> byteArrayOf(len.toByte())
        len <= 0xFF -> byteArrayOf(0x81.toByte(), len.toByte())
        len <= 0xFFFF -> byteArrayOf(
            0x82.toByte(),
            (len shr 8).toByte(),
            len.toByte(),
        )
        else -> byteArrayOf(
            0x83.toByte(),
            (len shr 16).toByte(),
            (len shr 8).toByte(),
            len.toByte(),
        )
    }
}
