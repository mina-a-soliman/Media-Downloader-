package com.media.downloader.model

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import java.io.File

data class NamedColor(
    val name: String,
    val color: Int
)

data class FontOption(
    val displayName: String,
    val familyName: String,
    val file: File
)

data class SubtitleStyle(
    val fontFamily: String = DEFAULT_FONT_FAMILY,
    val fontPath: String? = DEFAULT_FONT_PATH,
    val fontSize: Int = 32,
    val textColor: Int = Color.WHITE,
    val outlineColor: Int = Color.BLACK,
    val outlineWidth: Int = 2,
    val shadow: Int = 1,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val alignment: Int = 2,
    val marginV: Int = 40,
    val backgroundBox: Boolean = false
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_FONT_FAMILY, fontFamily)
        putString(KEY_FONT_PATH, fontPath)
        putInt(KEY_FONT_SIZE, fontSize)
        putInt(KEY_TEXT_COLOR, textColor)
        putInt(KEY_OUTLINE_COLOR, outlineColor)
        putInt(KEY_OUTLINE_WIDTH, outlineWidth)
        putInt(KEY_SHADOW, shadow)
        putBoolean(KEY_BOLD, bold)
        putBoolean(KEY_ITALIC, italic)
        putInt(KEY_ALIGNMENT, alignment)
        putInt(KEY_MARGIN_V, marginV)
        putBoolean(KEY_BOX, backgroundBox)
    }

    fun toForceStyle(): String {
        val borderStyle = if (backgroundBox) 3 else 1
        return buildString {
            append("FontName=").append(fontFamily)
            append(",FontSize=").append(fontSize)
            append(",PrimaryColour=").append(toAssColor(textColor))
            append(",SecondaryColour=").append(toAssColor(textColor))
            append(",OutlineColour=").append(toAssColor(outlineColor))
            append(",BackColour=").append(toAssColor(outlineColor))
            append(",Bold=").append(if (bold) -1 else 0)
            append(",Italic=").append(if (italic) -1 else 0)
            append(",Underline=0,StrikeOut=0")
            append(",BorderStyle=").append(borderStyle)
            append(",Outline=").append(outlineWidth)
            append(",Shadow=").append(shadow)
            append(",Alignment=").append(alignment.coerceIn(1, 9))
            append(",MarginL=24,MarginR=24,MarginV=").append(marginV)
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_FONT_FAMILY, fontFamily)
            .putString(KEY_FONT_PATH, fontPath)
            .putInt(KEY_FONT_SIZE, fontSize)
            .putInt(KEY_TEXT_COLOR, textColor)
            .putInt(KEY_OUTLINE_COLOR, outlineColor)
            .putInt(KEY_OUTLINE_WIDTH, outlineWidth)
            .putInt(KEY_SHADOW, shadow)
            .putBoolean(KEY_BOLD, bold)
            .putBoolean(KEY_ITALIC, italic)
            .putInt(KEY_ALIGNMENT, alignment)
            .putInt(KEY_MARGIN_V, marginV)
            .putBoolean(KEY_BOX, backgroundBox)
            .apply()
    }

    companion object {
        const val DEFAULT_FONT_FAMILY = "Roboto"
        const val DEFAULT_FONT_PATH = "/system/fonts/Roboto-Regular.ttf"
        private const val PREFS_NAME = "subtitle_style"

        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_FONT_PATH = "font_path"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_TEXT_COLOR = "text_color"
        private const val KEY_OUTLINE_COLOR = "outline_color"
        private const val KEY_OUTLINE_WIDTH = "outline_width"
        private const val KEY_SHADOW = "shadow"
        private const val KEY_BOLD = "bold"
        private const val KEY_ITALIC = "italic"
        private const val KEY_ALIGNMENT = "alignment"
        private const val KEY_MARGIN_V = "margin_v"
        private const val KEY_BOX = "background_box"

        val TEXT_COLORS = listOf(
            NamedColor("White", Color.WHITE),
            NamedColor("Yellow", Color.YELLOW),
            NamedColor("Cyan", Color.CYAN),
            NamedColor("Black", Color.BLACK),
            NamedColor("Red", Color.parseColor("#FF5252")),
            NamedColor("Green", Color.parseColor("#69F0AE")),
            NamedColor("Orange", Color.parseColor("#FFAB40")),
            NamedColor("Magenta", Color.parseColor("#E040FB")),
            NamedColor("Blue", Color.parseColor("#448AFF"))
        )

        val OUTLINE_COLORS = listOf(
            NamedColor("Black", Color.BLACK),
            NamedColor("White", Color.WHITE),
            NamedColor("Dark gray", Color.DKGRAY),
            NamedColor("Navy", Color.parseColor("#0D47A1")),
            NamedColor("Maroon", Color.parseColor("#B71C1C"))
        )

        fun fromBundle(bundle: Bundle?): SubtitleStyle {
            if (bundle == null) return SubtitleStyle()
            return SubtitleStyle(
                fontFamily = bundle.getString(KEY_FONT_FAMILY) ?: DEFAULT_FONT_FAMILY,
                fontPath = bundle.getString(KEY_FONT_PATH),
                fontSize = bundle.getInt(KEY_FONT_SIZE, 32),
                textColor = bundle.getInt(KEY_TEXT_COLOR, Color.WHITE),
                outlineColor = bundle.getInt(KEY_OUTLINE_COLOR, Color.BLACK),
                outlineWidth = bundle.getInt(KEY_OUTLINE_WIDTH, 2),
                shadow = bundle.getInt(KEY_SHADOW, 1),
                bold = bundle.getBoolean(KEY_BOLD, false),
                italic = bundle.getBoolean(KEY_ITALIC, false),
                alignment = bundle.getInt(KEY_ALIGNMENT, 2),
                marginV = bundle.getInt(KEY_MARGIN_V, 40),
                backgroundBox = bundle.getBoolean(KEY_BOX, false)
            )
        }

        fun load(context: Context): SubtitleStyle {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.contains(KEY_FONT_SIZE)) return SubtitleStyle()
            return SubtitleStyle(
                fontFamily = prefs.getString(KEY_FONT_FAMILY, DEFAULT_FONT_FAMILY) ?: DEFAULT_FONT_FAMILY,
                fontPath = prefs.getString(KEY_FONT_PATH, DEFAULT_FONT_PATH),
                fontSize = prefs.getInt(KEY_FONT_SIZE, 32),
                textColor = prefs.getInt(KEY_TEXT_COLOR, Color.WHITE),
                outlineColor = prefs.getInt(KEY_OUTLINE_COLOR, Color.BLACK),
                outlineWidth = prefs.getInt(KEY_OUTLINE_WIDTH, 2),
                shadow = prefs.getInt(KEY_SHADOW, 1),
                bold = prefs.getBoolean(KEY_BOLD, false),
                italic = prefs.getBoolean(KEY_ITALIC, false),
                alignment = prefs.getInt(KEY_ALIGNMENT, 2),
                marginV = prefs.getInt(KEY_MARGIN_V, 40),
                backgroundBox = prefs.getBoolean(KEY_BOX, false)
            )
        }

        fun discoverFonts(): List<FontOption> {
            val skip = listOf(
                "emoji", "symbol", "clock", "fallback", "ui-", "notocoloremoji"
            )
            val files = File("/system/fonts").listFiles().orEmpty()
                .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }
                .filter { file ->
                    val name = file.name.lowercase()
                    skip.none { name.contains(it) }
                }

            val grouped = files.groupBy { familyFromFileName(it.name) }
            val options = grouped.map { (family, group) ->
                val file = group.firstOrNull { it.name.contains("Regular", ignoreCase = true) }
                    ?: group.first()
                FontOption(displayName = family, familyName = family, file = file)
            }.sortedBy { it.displayName }

            if (options.isNotEmpty()) return options

            val fallback = File(DEFAULT_FONT_PATH)
            return listOf(
                FontOption(DEFAULT_FONT_FAMILY, DEFAULT_FONT_FAMILY, fallback)
            )
        }

        fun verticalFromAlignment(alignment: Int): Int = when (alignment) {
            in 7..9 -> 7
            in 4..6 -> 4
            else -> 1
        }

        fun horizontalFromAlignment(alignment: Int): Int = (alignment - 1) % 3

        fun alignmentFrom(verticalBase: Int, horizontalOffset: Int): Int =
            (verticalBase + horizontalOffset).coerceIn(1, 9)

        private fun familyFromFileName(fileName: String): String {
            FAMILY_OVERRIDES[fileName]?.let { return it }
            val stem = fileName.substringBeforeLast('.')
                .substringBefore('-')
                .substringBefore('_')
            return stem.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
                .replace(Regex("(?<=[A-Z])(?=[A-Z][a-z])"), " ")
                .ifBlank { DEFAULT_FONT_FAMILY }
        }

        private fun toAssColor(color: Int): Long {
            val red = Color.red(color).toLong()
            val green = Color.green(color).toLong()
            val blue = Color.blue(color).toLong()
            return (blue shl 16) or (green shl 8) or red
        }

        private val FAMILY_OVERRIDES = mapOf(
            "Roboto-Regular.ttf" to "Roboto",
            "Roboto-Bold.ttf" to "Roboto",
            "Roboto-Italic.ttf" to "Roboto",
            "NotoSerif-Regular.ttf" to "Noto Serif",
            "NotoNaskhArabic-Regular.ttf" to "Noto Naskh Arabic",
            "NotoSansArabic-Regular.ttf" to "Noto Sans Arabic",
            "DancingScript-Regular.ttf" to "Dancing Script",
            "CutiveMono.ttf" to "Cutive Mono",
            "ComingSoon.ttf" to "Coming Soon",
            "CarroisGothicSC-Regular.ttf" to "Carrois Gothic SC"
        )
    }
}
