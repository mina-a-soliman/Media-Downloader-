package com.media.downloader.util

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.media.downloader.model.DownloadType
import com.media.downloader.model.Platform
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.text.DecimalFormat

object MediaUtils {

    fun detectPlatform(url: String): Platform {
        val lower = url.lowercase()
        return when {
            lower.contains("youtube.com") || lower.contains("youtu.be") -> Platform.YOUTUBE
            lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com") -> Platform.FACEBOOK
            lower.contains("instagram.com") || lower.contains("instagr.am") -> Platform.INSTAGRAM
            else -> Platform.GENERIC
        }
    }

    fun getDownloadDir(context: Context): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(publicDownloads, "MediaDownloader")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return if (targetDir.canWrite()) {
            targetDir
        } else {
            // Fallback to app external files dir if permission restricts direct public write
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val format = DecimalFormat("#,##0.#")
        return "${format.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }

    fun scanMediaFile(context: Context, file: File, mimeType: String? = null) {
        if (!file.exists()) return
        MediaScannerConnection.scanFile(
            context.applicationContext,
            arrayOf(file.absolutePath),
            if (mimeType != null) arrayOf(mimeType) else null
        ) { path, uri ->
            // File indexed by Android media store
        }
    }

    fun buildYoutubeDLRequest(
        url: String,
        type: DownloadType,
        quality: String?,
        subtitleLang: String?,
        outputDir: File
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)

        // General output template: Title.ext
        val template = "${outputDir.absolutePath}/%(title)s.%(ext)s"
        request.addOption("-o", template)
        request.addOption("--no-mtime")
        request.addOption("--no-playlist")
        request.addOption("--socket-timeout", "30")

        when (type) {
            DownloadType.VIDEO -> {
                val formatSelection = when (quality) {
                    "1080p (FHD)" -> "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
                    "720p (HD)" -> "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
                    "480p (SD)" -> "bestvideo[height<=480]+bestaudio/best[height<=480]/best"
                    "360p (Low)" -> "bestvideo[height<=360]+bestaudio/best[height<=360]/best"
                    else -> "bestvideo+bestaudio/best"
                }
                request.addOption("-f", formatSelection)
                request.addOption("--merge-output-format", "mp4")
            }

            DownloadType.AUDIO_MP3 -> {
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "0")
                request.addOption("--embed-metadata")
            }

            DownloadType.SUBTITLES -> {
                request.addOption("--skip-download")
                request.addOption("--write-sub")
                request.addOption("--write-auto-sub")
                request.addOption("--sub-format", "srt/vtt/best")

                val lang = when (subtitleLang) {
                    "English (en)" -> "en.*"
                    "Arabic (ar)" -> "ar.*"
                    "Spanish (es)" -> "es.*"
                    "French (fr)" -> "fr.*"
                    else -> "all"
                }
                request.addOption("--sub-langs", lang)
            }
        }

        return request
    }
}
