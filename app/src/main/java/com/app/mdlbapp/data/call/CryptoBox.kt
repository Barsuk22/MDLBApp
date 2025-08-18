package com.app.mdlbapp.data.call

object CryptoBox {
    private fun b64d(s: String) =
        android.util.Base64.decode(s, android.util.Base64.DEFAULT)

    private fun b64e(b: ByteArray) =
        android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)

    private fun newIv() = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) } // 96-bit IV

    fun encryptMap(keyB64: String, map: Map<String, Any?>): String {
        val json = org.json.JSONObject(map).toString().toByteArray(Charsets.UTF_8)
        val iv = newIv()
        val sk = javax.crypto.spec.SecretKeySpec(b64d(keyB64), "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(javax.crypto.Cipher.ENCRYPT_MODE, sk, javax.crypto.spec.GCMParameterSpec(128, iv))
        }
        val ct = cipher.doFinal(json)
        return b64e(iv + ct)
    }

    fun decryptMap(keyB64: String, b64: String): Map<String, Any?> {
        val pack = b64d(b64)
        val iv = pack.copyOfRange(0, 12)
        val ct = pack.copyOfRange(12, pack.size)
        val sk = javax.crypto.spec.SecretKeySpec(b64d(keyB64), "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(javax.crypto.Cipher.DECRYPT_MODE, sk, javax.crypto.spec.GCMParameterSpec(128, iv))
        }
        val json = String(cipher.doFinal(ct), Charsets.UTF_8)
        val obj = org.json.JSONObject(json)
        return obj.keys().asSequence().associateWith { obj.get(it) }
    }
}