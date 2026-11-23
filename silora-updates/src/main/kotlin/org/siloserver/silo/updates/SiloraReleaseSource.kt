package org.siloserver.silo.updates

import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

internal const val SILORA_PACKAGE = "org.silora.app"

data class SiloraRelease(
    val tag: String,
    val versionName: String,
    val versionCode: Long,
    internal val downloadUrl: String,
    internal val bytes: Long,
    internal val digest: String,
) {
    internal fun toJson(): String = JSONObject().apply {
        put("tag", tag)
        put("name", versionName)
        put("code", versionCode)
        put("url", downloadUrl)
        put("size", bytes)
        put("digest", digest)
    }.toString()

    internal companion object {
        fun fromJson(json: String): SiloraRelease = JSONObject(json).let {
            SiloraRelease(it.getString("tag"), it.getString("name"), it.getLong("code"),
                it.getString("url"), it.getLong("size"), it.getString("digest"))
        }
    }
}

/** The same build-info.json version and exact asset contract used by Silora Updater. */
internal class SiloraReleaseSource(private val isTv: Boolean) {
    fun latest(): SiloraRelease {
        val release = JSONObject(text("https://api.github.com/repos/$REPO/releases/latest"))
        require(!release.getBoolean("draft") && !release.getBoolean("prerelease")) {
            "GitHub did not return a public stable release."
        }
        val tag = release.getString("tag_name")
        require(Regex("[A-Za-z0-9._-]+").matches(tag)) { "Invalid Silora release tag." }
        val assets = release.getJSONArray("assets")
        val assetName = if (isTv) "silora-tv.apk" else "silora-phone.apk"
        val listed = (0 until assets.length()).map(assets::getJSONObject)
        fun asset(name: String) = listed.firstOrNull { it.getString("name") == name }
            ?: throw IOException("The Silora release is missing $name.")
        fun url(name: String): String {
            val expected = "https://github.com/$REPO/releases/download/$tag/$name"
            require(asset(name).getString("browser_download_url") == expected) { "Unexpected Silora download URL." }
            return expected
        }

        val apk = asset(assetName)
        val info = JSONObject(text(url("build-info.json")))
        require(info.getString("tag") == tag) { "Silora build information does not match the release." }
        val name = info.getString("version_name")
        val size = apk.getLong("size")
        require(name.isNotBlank() && size in 1..MAX_APK_BYTES) { "Invalid Silora APK metadata." }
        val digest = apk.optString("digest", "").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
            ?: text(url("SHA256SUMS")).lineSequence().map { it.trim().split(Regex("\\s+"), limit = 2) }
                .firstOrNull { it.getOrNull(1)?.removePrefix("*") == assetName }?.first()
        require(digest != null && digest.matches(Regex("[a-fA-F0-9]{64}"))) { "Missing Silora APK checksum." }
        return SiloraRelease(tag, name, versionFor(info.getLong("version_code_base"), isTv), url(assetName), size, digest)
    }

    suspend fun download(release: SiloraRelease, target: File) {
        if (target.isFile && target.length() == release.bytes &&
            target.inputStream().use { digest(it, release.bytes) }.equals(release.digest, ignoreCase = true)
        ) return
        val partial = File(target.parentFile, "${target.name}.part")
        try {
            request(release.downloadUrl) { connection ->
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        require(digest(input, release.bytes, output).equals(release.digest, ignoreCase = true)) {
                            "The Silora download did not match its release checksum."
                        }
                    }
                }
            }
            check(partial.renameTo(target)) { "Could not save the verified Silora APK." }
        } finally {
            partial.delete()
        }
    }

    private suspend fun digest(input: java.io.InputStream, expected: Long, output: java.io.OutputStream? = null): String {
        val sha = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            require(total <= expected) { "The Silora APK exceeds its listed size." }
            sha.update(buffer, 0, count)
            output?.write(buffer, 0, count)
        }
        require(total == expected) { "The Silora APK download was incomplete." }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }

    private fun text(url: String): String = request(url) { connection ->
        connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                require(output.size() + count <= 512 * 1024) { "Silora release metadata is too large." }
                output.write(buffer, 0, count)
            }
            output.toString("UTF-8")
        }
    }

    private inline fun <T> request(url: String, use: (HttpURLConnection) -> T): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 40_000
            connection.setRequestProperty("User-Agent", "Silora-Android-Updater")
            if (connection.responseCode !in 200..299) {
                throw IOException("GitHub returned HTTP ${connection.responseCode}. Try again later.")
            }
            return use(connection)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val REPO = "SiloraOSS/silora-android"
        private const val MAX_APK_BYTES = 512L * 1024 * 1024

        internal fun versionFor(base: Long, isTv: Boolean): Long {
            require(base in 1..1_049_999_999) { "Invalid Silora version code." }
            return base * 2 + if (isTv) 1 else 0
        }
    }
}
