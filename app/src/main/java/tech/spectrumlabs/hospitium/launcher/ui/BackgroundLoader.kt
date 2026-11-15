// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.ui

import android.graphics.BitmapFactory
import android.util.AtomicFile
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.spectrumlabs.hospitium.launcher.domain.Backgrounds
import java.io.File
import java.io.IOException

/** A decoded background and the scrim strength to draw over it. */
data class BackgroundImage(val sha256: String, val bitmap: ImageBitmap, val scrim: Float)

/**
 * Downloads, verifies, caches and decodes the chosen background. Media is content-addressed, so a
 * cached file never goes stale: it is reused until a different address is chosen. Bytes that don't
 * hash to their address are discarded, wherever they came from.
 */
class BackgroundLoader(cacheRoot: File, private val download: suspend (path: String) -> ByteArray?) {
    private val dir = File(cacheRoot, "media")

    suspend fun load(choice: Backgrounds.Choice, displayW: Int, displayH: Int): BackgroundImage? = withContext(Dispatchers.IO) {
        val file = AtomicFile(File(dir, "${choice.sha256}.jpg"))
        val bytes = cached(file, choice.sha256) ?: download(choice.path)?.takeIf { Backgrounds.verified(it, choice.sha256) }
            ?.also { save(file, it) }
            ?: return@withContext null
        prune(keep = choice.sha256)
        decode(bytes, displayW, displayH)?.let { BackgroundImage(choice.sha256, it, choice.scrim) }
    }

    private fun cached(file: AtomicFile, sha: String): ByteArray? = try {
        file.readFully().takeIf { Backgrounds.verified(it, sha) }
    } catch (_: IOException) {
        null
    }

    private fun save(file: AtomicFile, bytes: ByteArray) {
        dir.mkdirs()
        val out = try {
            file.startWrite()
        } catch (e: IOException) {
            Log.w(TAG, "can't cache background: ${e.javaClass.simpleName}")
            return
        }
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            Log.w(TAG, "can't cache background: ${e.javaClass.simpleName}")
        }
    }

    /** One background is cached at a time; older ones are dropped. */
    private fun prune(keep: String) {
        dir.listFiles()?.filter { !it.name.startsWith(keep) }?.forEach { it.delete() }
    }

    /** Decodes no larger than the display: a 4K JPEG on a 1080p interface is subsampled 2x. */
    private fun decode(bytes: ByteArray, displayW: Int, displayH: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = Backgrounds.sampleSize(bounds.outWidth, bounds.outHeight, displayW, displayH)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    }

    private companion object {
        const val TAG = "HospitiumBackground"
    }
}
