package com.media.downloader.model

enum class DownloadType(val displayName: String) {
    VIDEO("Video"),
    AUDIO_MP3("Audio (MP3)"),
    SUBTITLES("Subtitles")
}

enum class Platform(val displayName: String, val badgeColorHex: String) {
    YOUTUBE("YouTube", "#FF0000"),
    FACEBOOK("Facebook", "#1877F2"),
    INSTAGRAM("Instagram", "#E1306C"),
    GENERIC("Web Video", "#4CAF50")
}
