package com.media.downloader.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.media.downloader.DownloaderApp
import com.media.downloader.MainActivity
import com.media.downloader.R
import com.media.downloader.model.DownloadType
import com.media.downloader.util.MediaUtils
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

class DownloadService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var currentProcessId: String? = null

    companion object {
        private const val TAG = "DownloadService"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_DOWNLOAD = "com.media.downloader.action.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.media.downloader.action.CANCEL_DOWNLOAD"

        const val EXTRA_URL = "extra_url"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_SUBTITLE_LANG = "extra_subtitle_lang"

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
            subtitleLang: String?
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TYPE, type.name)
                putExtra(EXTRA_QUALITY, quality)
                putExtra(EXTRA_SUBTITLE_LANG, subtitleLang)
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

                startForegroundNotification()
                executeDownload(url, type, quality, subtitleLang)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelCurrentDownload()
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
        subtitleLang: String?
    ) {
        val processId = "dl_${System.currentTimeMillis()}"
        currentProcessId = processId

        serviceScope.launch {
            _progressState.value = DownloadProgress(isDownloading = true, progress = 0f)

            try {
                val outputDir = MediaUtils.getDownloadDir(this@DownloadService)
                val request = MediaUtils.buildYoutubeDLRequest(
                    url = url,
                    type = type,
                    quality = quality,
                    subtitleLang = subtitleLang,
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

                // Scan all recently modified files in outputDir
                outputDir.listFiles()?.forEach { file ->
                    MediaUtils.scanMediaFile(this@DownloadService, file)
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
