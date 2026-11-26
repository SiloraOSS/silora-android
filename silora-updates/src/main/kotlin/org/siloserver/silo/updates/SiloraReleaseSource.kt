package org.siloserver.silo.updates

import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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
internal class SiloraReleaseSource(private val isTv: Boolean, private val http: UpdateHttp) {
    fun earliestCheckAt(): Long = maxOf(http.retryAt(API_HOST), http.retryAt(RELEASE_HOST))

    suspend fun latest(): SiloraRelease {
        // Every check needs build-info.json. Avoid spending API quota while that host is unavailable.
        http.requireAvailable(RELEASE_HOST)
        val release = JSONObject(text("https://$API_HOST/repos/$REPO/releases/latest"))
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
            val expected = "https://$RELEASE_HOST/$REPO/releases/download/$tag/$name"
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

    suspend fun download(release: SiloraRelease, target: File, requireSpace: (File, Long) -> Unit) {
        val partial = File(target.parentFile, "${target.name}.part")
        partial.delete()
        if (canReuse(release, target)) return

        target.delete()
        requireSpace(checkNotNull(target.parentFile), release.bytes)
        try {
            http.read(release.downloadUrl, APK_TIMEOUT_MILLIS) { input ->
                partial.outputStream().use { output ->
                    require(digest(input, release.bytes, output).equals(release.digest, ignoreCase = true)) {
                        "The Silora download did not match its release checksum."
                    }
                }
            }
            coroutineContext.ensureActive()
            check(partial.renameTo(target)) { "Could not save the verified Silora APK." }
        } finally {
            partial.delete()
        }
    }

    private suspend fun canReuse(release: SiloraRelease, target: File): Boolean {
        if (!target.isFile || target.length() != release.bytes) return false
        return try {
            target.inputStream().use { digest(it, release.bytes) }.equals(release.digest, ignoreCase = true)
        } catch (_: IOException) {
            false
        } catch (_: IllegalArgumentException) {
            // Cache pressure can also truncate a file while we are checking its length.
            false
        }
    }

    private suspend fun digest(input: InputStream, expected: Long, output: OutputStream? = null): String {
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

    private suspend fun text(url: String): String =
        http.read(url, METADATA_TIMEOUT_MILLIS) { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                require(output.size() + count <= 512 * 1024) { "Silora release metadata is too large." }
                output.write(buffer, 0, count)
            }
            output.toString("UTF-8")
        }

    companion object {
        private const val REPO = "SiloraOSS/silora-android"
        private const val API_HOST = "api.github.com"
        private const val RELEASE_HOST = "github.com"
        private const val METADATA_TIMEOUT_MILLIS = 30_000L
        private const val APK_TIMEOUT_MILLIS = 5 * 60_000L
        private const val MAX_APK_BYTES = 512L * 1024 * 1024

        internal fun versionFor(base: Long, isTv: Boolean): Long {
            require(base in 1..1_049_999_999) { "Invalid Silora version code." }
            return base * 2 + if (isTv) 1 else 0
        }
    }
}
