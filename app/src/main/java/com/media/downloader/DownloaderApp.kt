package com.media.downloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloaderApp : Application() {

    companion object {
        const val TAG = "DownloaderApp"
        const val CHANNEL_ID_DOWNLOAD = "media_download_channel"
        const val CHANNEL_NAME_DOWNLOAD = "Media Downloads"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initEngines()
    }

    private fun initEngines() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Initialize yt-dlp engine
                YoutubeDL.getInstance().init(this@DownloaderApp)
                Log.d(TAG, "YoutubeDL engine initialized successfully")
            } catch (e: YoutubeDLException) {
                Log.e(TAG, "Failed to initialize YoutubeDL", e)
            }

            try {
                // Initialize FFmpeg engine for merging video/audio & MP3 conversion
                FFmpeg.getInstance().init(this@DownloaderApp)
                Log.d(TAG, "FFmpeg initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize FFmpeg", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_DOWNLOAD,
                CHANNEL_NAME_DOWNLOAD,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time download and conversion progress"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
