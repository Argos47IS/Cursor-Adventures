package ai.openclaw.app.crypto

import android.util.Base64
import ai.openclaw.app.data.SecurePreferences
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Security
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class DeviceIdentity(private val prefs: SecurePreferences) {

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    val deviceId: String
        get() {
            var id = prefs.deviceId
            if (id == null) {
                ensureKeyPair()
                id = prefs.deviceId!!
            }
            return id
        }

    val publicKeyBase64: String
        get() {
            ensureKeyPair()
            return prefs.devicePublicKey!!
        }

    private fun ensureKeyPair() {
        if (prefs.devicePublicKey != null && prefs.devicePrivateKey != null && prefs.deviceId != null) {
            return
        }

        val paramSpec = ECNamedCurveTable.getParameterSpec("P-256")
        val generator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
        generator.initialize(paramSpec)
        val keyPair = generator.generateKeyPair()

        val publicKeyBytes = keyPair.public.encoded
        val privateKeyBytes = keyPair.private.encoded

        prefs.devicePublicKey = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
        prefs.devicePrivateKey = Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP)

        val fingerprint = sha256Hex(publicKeyBytes)
        prefs.deviceId = fingerprint
    }

    fun sign(payload: ByteArray): String {
        ensureKeyPair()
        val privateKeyBytes = Base64.decode(prefs.devicePrivateKey, Base64.NO_WRAP)
        val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val keyFactory = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
        val privateKey = keyFactory.generatePrivate(keySpec) as ECPrivateKey

        val signature = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME)
        signature.initSign(privateKey)
        signature.update(payload)
        val sigBytes = signature.sign()

        return Base64.encodeToString(sigBytes, Base64.NO_WRAP)
    }

    fun signConnectPayload(
        nonce: String,
        clientId: String,
        role: String,
        scopes: List<String>,
        token: String?,
        platform: String = "android",
        deviceFamily: String = "phone"
    ): Pair<String, Long> {
        val signedAt = System.currentTimeMillis()
        val scopesStr = scopes.joinToString(",")
        val tokenPart = token ?: ""

        // v3 payload: binds platform and deviceFamily
        val payloadStr = "$deviceId|$clientId|$role|$scopesStr|$tokenPart|$nonce|$platform|$deviceFamily|$signedAt"
        val payloadBytes = payloadStr.toByteArray(Charsets.UTF_8)
        val sig = sign(payloadBytes)
        return Pair(sig, signedAt)
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
