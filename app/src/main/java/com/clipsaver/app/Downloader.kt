package com.clipsaver.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class SavedVideo(val name: String, val uri: Uri)

object Downloader {
    private const val PROCESS_ID = "clipsaver-download"
    private const val ALBUM = "Movies/ClipSaver"

    private val initLock = Mutex()
    @Volatile private var ready = false

    /** Unpacks the bundled yt-dlp + FFmpeg. Safe to call repeatedly. */
    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        initLock.withLock {
            if (!ready) {
                val app = context.applicationContext
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                ready = true
            }
        }
    }

    /**
     * Downloads one video into the app's cache, then publishes it to the
     * phone's gallery under Movies/ClipSaver. Returns the saved file.
     */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (percent: Float, line: String) -> Unit,
    ): SavedVideo = withContext(Dispatchers.IO) {
        init(context)

        val workDir = File(context.cacheDir, "download").apply {
            deleteRecursively()
            mkdirs()
        }

        val request = YoutubeDLRequest(url).apply {
            addOption("-o", "${workDir.absolutePath}/%(uploader)s_%(id)s.%(ext)s")
            // Prefer a single MP4 with audio; merge separate streams if that's all there is.
            addOption("-f", "best[ext=mp4][vcodec!=none][acodec!=none]/bv*[ext=mp4]+ba[ext=m4a]/best")
            addOption("--merge-output-format", "mp4")
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--restrict-filenames")
        }

        YoutubeDL.getInstance().execute(
            request = request,
            processId = PROCESS_ID,
            redirectErrorStream = false,
            callback = { progress, _, line -> onProgress(progress, line) },
        )

        val file = workDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.maxByOrNull { it.length() }
            ?: error("Download finished but no video file was produced.")

        try {
            SavedVideo(file.name, publishToGallery(context, file))
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun publishToGallery(context: Context, file: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, ALBUM)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: error("Couldn't create the file in your gallery.")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: error("Couldn't write to your gallery.")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    fun cancel() {
        YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
    }

    /** TikTok changes often; updating yt-dlp is the usual fix when downloads start failing. */
    suspend fun updateEngine(context: Context): String = withContext(Dispatchers.IO) {
        init(context)
        val status = YoutubeDL.getInstance()
            .updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)
        val version = YoutubeDL.getInstance().version(context.applicationContext) ?: "unknown"
        when (status) {
            YoutubeDL.UpdateStatus.DONE -> "Updated download engine to $version"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Engine already up to date ($version)"
            null -> "Engine version $version"
        }
    }

    /** Pulls the first http(s) link out of text shared from another app. */
    fun extractUrl(text: String?): String? =
        text?.let { Regex("""https?://\S+""").find(it)?.value?.trimEnd('.', ',', ')') }
}
