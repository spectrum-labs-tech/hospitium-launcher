// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import tech.spectrumlabs.hospitium.launcher.protocol.ProtocolJson
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device token: AES-GCM encrypted under a non-exportable Android Keystore key, ciphertext in
 * private SharedPreferences. Config cache: a file in no-backup storage. Backup is disabled for the
 * whole app (manifest), so neither ever leaves the TV.
 */
class AndroidDeviceStore(context: Context) : DeviceStore {
    private val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
    private val cacheFile = AtomicFile(File(context.noBackupFilesDir, "config.json"))

    override fun serverUrl(): String? = prefs.getString(KEY_URL, null)

    override fun saveServerUrl(url: String) = prefs.edit(commit = true) { putString(KEY_URL, url) }

    override fun deviceToken(): String? {
        val sealed = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            TokenCipher.open(sealed)
        } catch (e: GeneralSecurityException) {
            // The key is gone (e.g. keystore reset). The token is unrecoverable; pair again.
            Log.w(TAG, "device token unreadable, pairing again: ${e.javaClass.simpleName}")
            clearPairing()
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "device token corrupt, pairing again")
            clearPairing()
            null
        }
    }

    // commit, not apply: the token exists exactly once, so it must be on disk before we go on.
    override fun saveDeviceToken(token: String) {
        val ok = prefs.edit().putString(KEY_TOKEN, TokenCipher.seal(token)).commit()
        check(ok) { "could not persist the device token" }
    }

    override fun cachedConfig(): CachedConfig? = try {
        ProtocolJson.decodeFromString(CachedConfig.serializer(), String(cacheFile.readFully(), Charsets.UTF_8))
    } catch (_: java.io.FileNotFoundException) {
        null
    } catch (e: Exception) { // a corrupt cache is just a missing cache
        Log.w(TAG, "discarding unreadable config cache: ${e.javaClass.simpleName}")
        cacheFile.delete()
        null
    }

    override fun saveCachedConfig(cached: CachedConfig) {
        val out = cacheFile.startWrite()
        try {
            out.write(ProtocolJson.encodeToString(CachedConfig.serializer(), cached).toByteArray(Charsets.UTF_8))
            cacheFile.finishWrite(out)
        } catch (e: java.io.IOException) {
            cacheFile.failWrite(out)
            Log.w(TAG, "could not write config cache: ${e.javaClass.simpleName}")
        }
    }

    override fun clearPairing() {
        prefs.edit(commit = true) { remove(KEY_TOKEN) }
        cacheFile.delete()
    }

    override fun clearAll() {
        clearPairing()
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val TAG = "HospitiumStore"
        const val KEY_URL = "server_url"
        const val KEY_TOKEN = "device_token_sealed"
    }
}

private object TokenCipher {
    private const val ALIAS = "hospitium-device-token"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun seal(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun open(sealed: String): String {
        val raw = Base64.decode(sealed, Base64.NO_WRAP)
        require(raw.size > IV_BYTES) { "sealed token too short" }
        val c = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV_BYTES))
        }
        return String(c.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
    }
}
