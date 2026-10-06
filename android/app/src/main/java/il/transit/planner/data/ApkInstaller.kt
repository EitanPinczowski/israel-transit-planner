package il.transit.planner.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import il.transit.core.api.MotisClient
import il.transit.core.update.UpdateCheck
import il.transit.core.update.UpdateFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Downloads a release APK into the app's own cache (no storage permission) and hands it to
 * Android's installer. Android itself refuses an APK signed with another key; core's UpdateFlow
 * refuses one whose sha256 is not the digest GitHub published.
 */
open class ApkInstaller(
    private val context: Context,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    sealed interface Download {
        data class Done(val file: File, val sha256: String) : Download
        data class Failed(val reason: UpdateFailure) : Download
    }

    /** Has the user let this app install apps ("Install unknown apps")? */
    open fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** One request (plus GitHub's redirect to its asset host). Progress as bytes read / total (≤ 0 unknown). */
    open suspend fun download(url: String, onProgress: (Long, Long) -> Unit): Download = withContext(Dispatchers.IO) {
        if (!UpdateCheck.isAllowedApkUrl(url)) return@withContext Download.Failed(UpdateFailure.BAD_URL)
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // one APK at a time; older ones are spent
        val out = File(dir, "update.apk")
        try {
            val req = Request.Builder().url(url).header("User-Agent", MotisClient.USER_AGENT).build()
            http.newCall(req).execute().use { r ->
                if (!redirectsAllowed(r)) return@withContext Download.Failed(UpdateFailure.BAD_URL)
                val body = r.body
                if (!r.isSuccessful || body == null) return@withContext Download.Failed(UpdateFailure.NETWORK)
                val total = body.contentLength()
                if (total > 0 && dir.usableSpace < total + SPARE_BYTES) return@withContext Download.Failed(UpdateFailure.NO_SPACE)
                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            read += n
                            onProgress(read, total)
                        }
                    }
                }
                if (total > 0 && read != total) return@withContext Download.Failed(UpdateFailure.NETWORK)
                Download.Done(out, digest.digest().joinToString("") { "%02x".format(it) })
            }
        } catch (e: IOException) {
            out.delete()
            val full = e.message?.contains("ENOSPC") == true || dir.usableSpace < SPARE_BYTES
            Download.Failed(if (full) UpdateFailure.NO_SPACE else UpdateFailure.NETWORK)
        }
    }

    /** Every hop GitHub redirected through must be an allowed https host too. */
    private fun redirectsAllowed(r: Response): Boolean {
        var hop: Response? = r
        while (hop != null) {
            if (!UpdateCheck.isAllowedApkUrl(hop.request.url.toString())) return false
            hop = hop.priorResponse
        }
        return true
    }

    companion object {
        /** Under cacheDir; matches `res/xml/update_paths.xml`. */
        const val DIR = "updates"
        private const val SPARE_BYTES = 20L * 1024 * 1024

        /** Android's "Install unknown apps" screen for this app; the user comes back with Back. */
        fun permissionIntent(context: Context): Intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

        /** Android's "Update this app?" prompt for a verified APK, answering with a result code. */
        @Suppress("DEPRECATION") // ACTION_INSTALL_PACKAGE still works and, unlike ACTION_VIEW, reports failures.
        fun installIntent(context: Context, file: String): Intent {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", File(file))
            return Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }

        /** The code Android's installer puts in its failure result (PackageManager.INSTALL_FAILED_*). */
        const val EXTRA_INSTALL_RESULT = "android.intent.extra.INSTALL_RESULT"
    }
}
