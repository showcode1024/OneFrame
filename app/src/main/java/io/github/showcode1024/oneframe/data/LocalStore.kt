package io.github.showcode1024.oneframe.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalStore(context: Context) {
    // Legacy storage and key aliases are intentionally kept for in-place upgrades.
    private val preferences = context.getSharedPreferences("prism", Context.MODE_PRIVATE)
    val softwareDecode: Boolean get() = preferences.getBoolean("software", false)
    fun setSoftwareDecode(value: Boolean) { preferences.edit().putBoolean("software", value).apply() }
    val experimentalP5Hardware: Boolean get() = preferences.getBoolean("p5_hardware_experiment", false)
    fun setExperimentalP5Hardware(value: Boolean) { preferences.edit().putBoolean("p5_hardware_experiment", value).apply() }
    val fastScaling: Boolean get() = preferences.getBoolean("fast_scaling", false)
    fun setFastScaling(value: Boolean) { preferences.edit().putBoolean("fast_scaling", value).apply() }

    fun recents(): List<RecentMedia> = runCatching {
        val values = JSONArray(preferences.getString("recents", "[]"))
        List(values.length()) { i -> values.getJSONObject(i).let {
            RecentMedia(it.getString("uri"), it.getString("title"), it.optDouble("position", 0.0))
        } }
    }.getOrDefault(emptyList())

    @Synchronized fun remember(media: RecentMedia) {
        val entries = listOf(media) + recents().filter { it.uri != media.uri }
        val json = JSONArray()
        entries.take(20).forEach { json.put(JSONObject().put("uri", it.uri).put("title", it.title).put("position", it.position)) }
        preferences.edit().putString("recents", json.toString()).apply()
    }

    fun forget(uri: String) {
        val entries = JSONArray()
        recents().filter { it.uri != uri }.forEach {
            entries.put(JSONObject().put("uri", it.uri).put("title", it.title).put("position", it.position))
        }
        preferences.edit().putString("recents", entries.toString()).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("prism-session-v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("prism-session-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    private fun sessionJson(session: EmbySession) = JSONObject().put("url", session.baseUrl).put("userId", session.userId)
        .put("token", session.token).put("name", session.userName)
    private fun parseSession(json: JSONObject) = EmbySession(json.getString("url"), json.getString("userId"), json.getString("token"), json.getString("name"))
    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }

    fun session(): EmbySession? = runCatching {
        parseSession(JSONObject(decrypt(preferences.getString("session", null) ?: return null)))
    }.getOrNull()

    fun sessions(): List<EmbySession> {
        val saved = preferences.getString("sessions", null) ?: return listOfNotNull(session())
        return runCatching { val json = JSONArray(decrypt(saved)); List(json.length()) { parseSession(json.getJSONObject(it)) } }
            .getOrElse { listOfNotNull(session()) }
    }

    @Synchronized fun saveSession(session: EmbySession) {
        val entries = sessions().filterNot { it.baseUrl == session.baseUrl && it.userId == session.userId } + session
        val json = JSONArray().apply { entries.forEach { put(sessionJson(it)) } }
        preferences.edit().putString("session", encrypt(sessionJson(session).toString()))
            .putString("sessions", encrypt(json.toString())).apply()
    }

    @Synchronized fun removeSession(session: EmbySession): EmbySession? {
        val entries = sessions().filterNot { it.baseUrl == session.baseUrl && it.userId == session.userId }
        val active = this.session()?.let { current ->
            if (current.baseUrl == session.baseUrl && current.userId == session.userId) entries.firstOrNull() else current
        }
        val json = JSONArray().apply { entries.forEach { put(sessionJson(it)) } }
        val edit = preferences.edit().putString("sessions", encrypt(json.toString()))
        if (active == null) edit.remove("session") else edit.putString("session", encrypt(sessionJson(active).toString()))
        edit.apply()
        return active
    }
    fun clearSession() { session()?.let { removeSession(it) } ?: preferences.edit().remove("session").apply() }
    val deviceId: String get() = preferences.getString("deviceId", null) ?: java.util.UUID.randomUUID().toString().also {
        preferences.edit().putString("deviceId", it).apply()
    }
}
