package com.media.downloader.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import com.media.downloader.DownloaderApp
import com.media.downloader.MainActivity
import com.media.downloader.R
import com.media.downloader.model.DownloadType
import com.media.downloader.util.MediaUtils
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DownloadService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var currentProcessId: String? = null

    companion object {
        private const val TAG = "DownloadService"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_DOWNLOAD = "com.media.downloader.action.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.media.downloader.action.CANCEL_DOWNLOAD"
        const val ACTION_EXTRACT_AUDIO = "com.media.downloader.action.EXTRACT_AUDIO"
        const val ACTION_BURN_SUBTITLES = "com.media.downloader.action.BURN_SUBTITLES"

        const val EXTRA_URL = "extra_url"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_SUBTITLE_LANG = "extra_subtitle_lang"
        const val EXTRA_AUDIO_QUALITY = "extra_audio_quality"
        const val EXTRA_PLAYLIST = "extra_playlist"
        const val EXTRA_OUTPUT_TREE_URI = "extra_output_tree_uri"
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_SUBTITLE_URI = "extra_subtitle_uri"

        data class DownloadProgress(
            val isDownloading: Boolean = false,
            val progress: Float = 0f,
            val etaSeconds: Long = 0L,
            val logLine: String = "",
            val isCompleted: Boolean = false,
            val error: String? = null
        )

        private val _progressState = MutableStateFlow(DownloadProgress())
        val progressState = _progressState.asStateFlow()

        private val _completedEvent = MutableSharedFlow<File>()
        val completedEvent = _completedEvent.asSharedFlow()

        fun startDownload(
            context: Context,
            url: String,
            type: DownloadType,
            quality: String?,
            subtitleLang: String?,
            audioQuality: String?,
            playlist: Boolean,
            outputTreeUri: String?
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TYPE, type.name)
                putExtra(EXTRA_QUALITY, quality)
                putExtra(EXTRA_SUBTITLE_LANG, subtitleLang)
                putExtra(EXTRA_AUDIO_QUALITY, audioQuality)
                putExtra(EXTRA_PLAYLIST, playlist)
                putExtra(EXTRA_OUTPUT_TREE_URI, outputTreeUri)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelDownload(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
            }
            context.startService(intent)
        }

        fun startAudioExtraction(
            context: Context,
            videoUri: String,
            audioQuality: String?,
            outputTreeUri: String?
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_EXTRACT_AUDIO
                putExtra(EXTRA_VIDEO_URI, videoUri)
                putExtra(EXTRA_AUDIO_QUALITY, audioQuality)
                putExtra(EXTRA_OUTPUT_TREE_URI, outputTreeUri)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startSubtitleBurn(
            context: Context,
            videoUri: String,
            subtitleUri: String,
            outputTreeUri: String?
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_BURN_SUBTITLES
                putExtra(EXTRA_VIDEO_URI, videoUri)
                putExtra(EXTRA_SUBTITLE_URI, subtitleUri)
                putExtra(EXTRA_OUTPUT_TREE_URI, outputTreeUri)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val typeName = intent.getStringExtra(EXTRA_TYPE) ?: DownloadType.VIDEO.name
                val type = DownloadType.valueOf(typeName)
                val quality = intent.getStringExtra(EXTRA_QUALITY)
                val subtitleLang = intent.getStringExtra(EXTRA_SUBTITLE_LANG)
                val audioQuality = intent.getStringExtra(EXTRA_AUDIO_QUALITY)
                val playlist = intent.getBooleanExtra(EXTRA_PLAYLIST, false)
                val outputTreeUri = intent.getStringExtra(EXTRA_OUTPUT_TREE_URI)

                startForegroundNotification()
                executeDownload(url, type, quality, subtitleLang, audioQuality, playlist, outputTreeUri)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelCurrentDownload()
            }
            ACTION_EXTRACT_AUDIO -> {
                val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI) ?: return START_NOT_STICKY
                startForegroundNotification()
                executeAudioExtraction(
                    Uri.parse(videoUri),
                    intent.getStringExtra(EXTRA_AUDIO_QUALITY),
                    intent.getStringExtra(EXTRA_OUTPUT_TREE_URI)
                )
            }
            ACTION_BURN_SUBTITLES -> {
                val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI) ?: return START_NOT_STICKY
                val subtitleUri = intent.getStringExtra(EXTRA_SUBTITLE_URI) ?: return START_NOT_STICKY
                startForegroundNotification()
                executeSubtitleBurn(
                    Uri.parse(videoUri),
                    Uri.parse(subtitleUri),
                    intent.getStringExtra(EXTRA_OUTPUT_TREE_URI)
                )
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val notification = createNotification("Starting download…", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(content: String, progress: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, DownloaderApp.CHANNEL_ID_DOWNLOAD)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(100, progress, progress <= 0)
            .build()
    }

    private fun updateNotification(content: String, progress: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(content, progress))
    }

    private fun executeDownload(
        url: String,
        type: DownloadType,
        quality: String?,
        subtitleLang: String?,
        audioQuality: String?,
        playlist: Boolean,
        outputTreeUri: String?
    ) {
        val processId = "dl_${System.currentTimeMillis()}"
        currentProcessId = processId

        serviceScope.launch {
            _progressState.value = DownloadProgress(isDownloading = true, progress = 0f)

            try {
                val selectedTree = outputTreeUri?.let(Uri::parse)
                val outputDir = if (selectedTree == null) {
                    MediaUtils.getDownloadDir(this@DownloadService)
                } else {
                    File(cacheDir, "download-staging/$processId").apply { mkdirs() }
                }
                val request = MediaUtils.buildYoutubeDLRequest(
                    url = url,
                    type = type,
                    quality = quality,
                    subtitleLang = subtitleLang,
                    audioQuality = audioQuality,
                    playlist = playlist,
                    outputDir = outputDir
                )

                YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
                    val cleanLine = line.orEmpty()
                    _progressState.value = DownloadProgress(
                        isDownloading = true,
                        progress = progress,
                        etaSeconds = etaInSeconds,
                        logLine = cleanLine
                    )
                    updateNotification(
                        "Downloading: ${progress.toInt()}%",
                        progress.toInt()
                    )
                }

                if (selectedTree != null) {
                    _progressState.value = DownloadProgress(
                        isDownloading = true,
                        progress = 100f,
                        logLine = "Saving files to selected folder…"
                    )
                    copyToDocumentTree(outputDir, selectedTree, preserveDirectories = playlist)
                    outputDir.deleteRecursively()
                } else {
                    outputDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        MediaUtils.scanMediaFile(this@DownloadService, file)
                    }
                }

                _progressState.value = DownloadProgress(
                    isDownloading = false,
                    progress = 100f,
                    isCompleted = true,
                    logLine = "Download completed!"
                )
                _completedEvent.emit(outputDir)

            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _progressState.value = DownloadProgress(
                    isDownloading = false,
                    error = e.message ?: "Download failed"
                )
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun executeAudioExtraction(videoUri: Uri, audioQuality: String?, outputTreeUri: String?) {
        serviceScope.launch {
            val workDir = File(cacheDir, "audio-extraction/${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                _progressState.value = DownloadProgress(isDownloading = true, logLine = "Preparing video…")
                val displayName = queryDisplayName(videoUri) ?: "video.mp4"
                val baseName = displayName.substringBeforeLast('.')
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .ifBlank { "extracted-audio" }
                val input = File(workDir, "input.${displayName.substringAfterLast('.', "mp4")}")
                contentResolver.openInputStream(videoUri)?.use { source ->
                    FileOutputStream(input).use { target -> source.copyTo(target) }
                } ?: error("Could not open the selected video")

                val selectedTree = outputTreeUri?.let(Uri::parse)
                val outputDir = if (selectedTree == null) {
                    MediaUtils.getDownloadDir(this@DownloadService)
                } else {
                    File(workDir, "output").apply { mkdirs() }
                }
                val output = uniqueFile(outputDir, "$baseName.mp3")
                val ffmpeg = findFfmpegBinary()
                val qualityArgs = when (audioQuality) {
                    "320 kbps" -> listOf("-b:a", "320k")
                    "256 kbps" -> listOf("-b:a", "256k")
                    "192 kbps" -> listOf("-b:a", "192k")
                    "128 kbps" -> listOf("-b:a", "128k")
                    else -> listOf("-q:a", "0")
                }

                _progressState.value = DownloadProgress(isDownloading = true, logLine = "Extracting MP3 audio…")
                updateNotification(getString(R.string.status_extracting), 0)
                val command = mutableListOf(
                    ffmpeg.absolutePath, "-y", "-i", input.absolutePath,
                    "-vn", "-codec:a", "libmp3lame"
                )
                command.addAll(qualityArgs)
                command.add(output.absolutePath)
                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["LD_LIBRARY_PATH"] =
                            ffmpeg.parentFile?.parentFile?.resolve("lib")?.absolutePath.orEmpty()
                    }
                    .start()
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        _progressState.value = DownloadProgress(isDownloading = true, logLine = line)
                    }
                }
                if (process.waitFor() != 0 || !output.exists()) {
                    error("FFmpeg could not extract audio from this video")
                }

                if (selectedTree != null) {
                    copyToDocumentTree(outputDir, selectedTree, preserveDirectories = false)
                } else {
                    MediaUtils.scanMediaFile(this@DownloadService, output, "audio/mpeg")
                }
                _progressState.value = DownloadProgress(
                    isCompleted = true,
                    progress = 100f,
                    logLine = "MP3 extraction completed!"
                )
                _completedEvent.emit(output)
            } catch (e: Exception) {
                Log.e(TAG, "Audio extraction failed", e)
                _progressState.value = DownloadProgress(error = e.message ?: "Audio extraction failed")
            } finally {
                workDir.deleteRecursively()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun executeSubtitleBurn(videoUri: Uri, subtitleUri: Uri, outputTreeUri: String?) {
        serviceScope.launch {
            val workDir = File(cacheDir, "subtitle-burn/${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                _progressState.value = DownloadProgress(isDownloading = true, logLine = "Preparing video and subtitles…")
                val videoName = queryDisplayName(videoUri) ?: "video.mp4"
                val subtitleName = queryDisplayName(subtitleUri) ?: "subtitles.srt"
                val baseName = videoName.substringBeforeLast('.')
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .ifBlank { "video-with-subtitles" }
                val input = File(workDir, "input.${videoName.substringAfterLast('.', "mp4")}")
                val subtitle = File(workDir, "subtitles.${subtitleName.substringAfterLast('.', "srt")}")
                copyUriToFile(videoUri, input)
                copyUriToFile(subtitleUri, subtitle)

                val selectedTree = outputTreeUri?.let(Uri::parse)
                val outputDir = if (selectedTree == null) {
                    MediaUtils.getDownloadDir(this@DownloadService)
                } else {
                    File(workDir, "output").apply { mkdirs() }
                }
                val output = uniqueFile(outputDir, "$baseName-hardcoded.mp4")
                val ffmpeg = findFfmpegBinary()
                val subtitlePath = subtitle.absolutePath
                    .replace("\\", "\\\\")
                    .replace(":", "\\:")
                    .replace("'", "\\'")

                updateNotification("Burning subtitles into video…", 0)
                val command = listOf(
                    ffmpeg.absolutePath, "-y", "-i", input.absolutePath,
                    "-vf", "subtitles='$subtitlePath'",
                    "-map", "0:v:0", "-map", "0:a?",
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "18",
                    "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart",
                    output.absolutePath
                )
                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["LD_LIBRARY_PATH"] =
                            ffmpeg.parentFile?.parentFile?.resolve("lib")?.absolutePath.orEmpty()
                    }
                    .start()
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        _progressState.value = DownloadProgress(isDownloading = true, logLine = line)
                    }
                }
                if (process.waitFor() != 0 || !output.exists()) {
                    error("FFmpeg could not burn subtitles into this video")
                }

                if (selectedTree != null) {
                    copyToDocumentTree(outputDir, selectedTree, preserveDirectories = false)
                } else {
                    MediaUtils.scanMediaFile(this@DownloadService, output, "video/mp4")
                }
                _progressState.value = DownloadProgress(
                    isCompleted = true,
                    progress = 100f,
                    logLine = "Subtitles burned into video!"
                )
                _completedEvent.emit(output)
            } catch (e: Exception) {
                Log.e(TAG, "Subtitle burn failed", e)
                _progressState.value = DownloadProgress(error = e.message ?: "Subtitle burn failed")
            } finally {
                workDir.deleteRecursively()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun copyUriToFile(uri: Uri, destination: File) {
        contentResolver.openInputStream(uri)?.use { source ->
            FileOutputStream(destination).use { target -> source.copyTo(target) }
        } ?: error("Could not open selected file")
    }

    private fun queryDisplayName(uri: Uri): String? = contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    private fun uniqueFile(directory: File, requestedName: String): File {
        var candidate = File(directory, requestedName)
        var suffix = 1
        val stem = requestedName.substringBeforeLast('.')
        val extension = requestedName.substringAfterLast('.', "mp3")
        while (candidate.exists()) {
            candidate = File(directory, "$stem ($suffix).$extension")
            suffix++
        }
        return candidate
    }

    private fun findFfmpegBinary(): File {
        FFmpeg.getInstance().init(applicationContext)
        val root = File(noBackupFilesDir, "youtubedl-android/packages/ffmpeg")
        return root.walkTopDown().firstOrNull { it.isFile && it.name == "ffmpeg" }
            ?: error("FFmpeg executable is unavailable")
    }

    private fun copyToDocumentTree(
        sourceDir: File,
        treeUri: Uri,
        preserveDirectories: Boolean
    ) {
        val rootDocument = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        if (preserveDirectories) {
            copyDirectoryContents(sourceDir, rootDocument)
        } else {
            sourceDir.walkTopDown()
                .filter { it.isFile }
                .forEach { copyFileToDocument(it, rootDocument) }
        }
    }

    private fun copyDirectoryContents(sourceDir: File, destination: Uri) {
        sourceDir.listFiles()?.forEach { source ->
            if (source.isDirectory) {
                val childDirectory = DocumentsContract.createDocument(
                    contentResolver,
                    destination,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    source.name
                ) ?: error("Could not create folder ${source.name}")
                copyDirectoryContents(source, childDirectory)
            } else {
                copyFileToDocument(source, destination)
            }
        }
    }

    private fun copyFileToDocument(source: File, destination: Uri) {
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(source.extension.lowercase())
            ?: "application/octet-stream"
        val child = DocumentsContract.createDocument(
            contentResolver,
            destination,
            mimeType,
            source.name
        ) ?: error("Could not create ${source.name}")
        FileInputStream(source).use { input ->
            contentResolver.openOutputStream(child, "w")?.use { output ->
                input.copyTo(output)
            } ?: error("Could not write ${source.name}")
        }
    }

    private fun cancelCurrentDownload() {
        currentProcessId?.let { id ->
            try {
                YoutubeDL.getInstance().destroyProcessById(id)
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying process $id", e)
            }
        }
        _progressState.value = DownloadProgress(isDownloading = false, logLine = "Download canceled")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
