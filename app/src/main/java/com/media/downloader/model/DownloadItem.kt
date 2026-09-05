package com.media.downloader.model

import java.io.File

data class DownloadItem(
    val file: File,
    val title: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val type: DownloadType,
    val lastModified: Long
)
