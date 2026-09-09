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
        audioQuality: String?,
        playlist: Boolean,
        outputDir: File
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)

        // General output template: Title.ext
        val outputName = if (playlist) {
            "%(playlist_title|Playlist)s/%(playlist_index)03d - %(title)s.%(ext)s"
        } else {
            "%(title)s.%(ext)s"
        }
        val template = "${outputDir.absolutePath}/$outputName"
        request.addOption("-o", template)
        request.addOption("--no-mtime")
        request.addOption(if (playlist) "--yes-playlist" else "--no-playlist")
        request.addOption("--socket-timeout", "30")

        when (type) {
            DownloadType.VIDEO -> {
                val formatSelection = when {
                    quality?.contains("1080") == true -> "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
                    quality?.contains("720") == true -> "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
                    quality?.contains("480") == true -> "bestvideo[height<=480]+bestaudio/best[height<=480]/best"
                    quality?.contains("360") == true -> "bestvideo[height<=360]+bestaudio/best[height<=360]/best"
                    else -> "bestvideo+bestaudio/best"
                }
                request.addOption("-f", formatSelection)
                request.addOption("--merge-output-format", "mp4")
            }

            DownloadType.AUDIO_MP3 -> {
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                val mp3Quality = when {
                    audioQuality?.contains("320") == true -> "320K"
                    audioQuality?.contains("256") == true -> "256K"
                    audioQuality?.contains("192") == true -> "192K"
                    audioQuality?.contains("128") == true -> "128K"
                    else -> "0"
                }
                request.addOption("--audio-quality", mp3Quality)
                request.addOption("--embed-metadata")
            }

            DownloadType.SUBTITLES -> {
                request.addOption("--skip-download")
                request.addOption("--write-sub")
                request.addOption("--write-auto-sub")
                request.addOption("--sub-format", "srt/vtt/best")

                val lang = when {
                    subtitleLang?.contains("(en)", ignoreCase = true) == true || subtitleLang?.contains("English", ignoreCase = true) == true -> "en.*"
                    subtitleLang?.contains("(zh)", ignoreCase = true) == true || subtitleLang?.contains("Chinese", ignoreCase = true) == true || subtitleLang?.contains("中文") == true -> "zh.*"
                    subtitleLang?.contains("(ar)", ignoreCase = true) == true || subtitleLang?.contains("Arabic", ignoreCase = true) == true -> "ar.*"
                    subtitleLang?.contains("(es)", ignoreCase = true) == true || subtitleLang?.contains("Spanish", ignoreCase = true) == true -> "es.*"
                    subtitleLang?.contains("(fr)", ignoreCase = true) == true || subtitleLang?.contains("French", ignoreCase = true) == true -> "fr.*"
                    else -> "all"
                }
                request.addOption("--sub-langs", lang)
            }
        }

        return request
    }
}
