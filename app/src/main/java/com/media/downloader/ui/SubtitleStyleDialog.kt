package com.media.downloader.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.media.downloader.R
import com.media.downloader.databinding.DialogSubtitleStyleBinding
import com.media.downloader.model.FontOption
import com.media.downloader.model.NamedColor
import com.media.downloader.model.SubtitleStyle
import java.io.File

object SubtitleStyleDialog {

    private val typefaceCache = mutableMapOf<String, Typeface>()

    fun show(activity: AppCompatActivity, onConfirm: (SubtitleStyle) -> Unit) {
        val binding = DialogSubtitleStyleBinding.inflate(activity.layoutInflater)
        val fonts = SubtitleStyle.discoverFonts()
        var style = SubtitleStyle.load(activity)

        val selectedFont = fonts.firstOrNull { it.file.absolutePath == style.fontPath }
            ?: fonts.firstOrNull { it.familyName.equals(style.fontFamily, ignoreCase = true) }
            ?: fonts.firstOrNull { it.familyName == SubtitleStyle.DEFAULT_FONT_FAMILY }
            ?: fonts.first()
        style = style.copy(
            fontFamily = selectedFont.familyName,
            fontPath = selectedFont.file.absolutePath
        )

        binding.actvFont.setAdapter(
            ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, fonts.map { it.displayName })
        )
        binding.actvFont.setText(selectedFont.displayName, false)
        binding.actvFont.setOnClickListener { binding.actvFont.showDropDown() }
        binding.actvFont.setOnItemClickListener { _, _, position, _ ->
            val selectedName = fonts.getOrNull(position)?.displayName
                ?: binding.actvFont.text?.toString()
            val font = fonts.firstOrNull { it.displayName == selectedName } ?: return@setOnItemClickListener
            style = style.copy(fontFamily = font.familyName, fontPath = font.file.absolutePath)
            renderPreview(binding, style, fonts)
        }

        setupColorDropdown(activity, binding.actvTextColor, SubtitleStyle.TEXT_COLORS, style.textColor) { color ->
            style = style.copy(textColor = color)
            tintSwatch(binding.swatchTextColor, color)
            renderPreview(binding, style, fonts)
        }
        setupColorDropdown(activity, binding.actvOutlineColor, SubtitleStyle.OUTLINE_COLORS, style.outlineColor) { color ->
            style = style.copy(outlineColor = color)
            tintSwatch(binding.swatchOutlineColor, color)
            renderPreview(binding, style, fonts)
        }
        tintSwatch(binding.swatchTextColor, style.textColor)
        tintSwatch(binding.swatchOutlineColor, style.outlineColor)

        binding.sliderSize.value = style.fontSize.toFloat()
        binding.sliderOutline.value = style.outlineWidth.toFloat()
        binding.sliderShadow.value = style.shadow.toFloat()
        binding.sliderMargin.value = style.marginV.toFloat()
        binding.cbBold.isChecked = style.bold
        binding.cbItalic.isChecked = style.italic
        binding.cbBackgroundBox.isChecked = style.backgroundBox

        when (SubtitleStyle.verticalFromAlignment(style.alignment)) {
            7 -> binding.toggleVertical.check(R.id.btnPosTop)
            4 -> binding.toggleVertical.check(R.id.btnPosMiddle)
            else -> binding.toggleVertical.check(R.id.btnPosBottom)
        }
        when (SubtitleStyle.horizontalFromAlignment(style.alignment)) {
            0 -> binding.toggleHorizontal.check(R.id.btnPosLeft)
            2 -> binding.toggleHorizontal.check(R.id.btnPosRight)
            else -> binding.toggleHorizontal.check(R.id.btnPosCenter)
        }

        fun currentAlignment(): Int {
            val vertical = when (binding.toggleVertical.checkedButtonId) {
                R.id.btnPosTop -> 7
                R.id.btnPosMiddle -> 4
                else -> 1
            }
            val horizontal = when (binding.toggleHorizontal.checkedButtonId) {
                R.id.btnPosLeft -> 0
                R.id.btnPosRight -> 2
                else -> 1
            }
            return SubtitleStyle.alignmentFrom(vertical, horizontal)
        }

        fun syncFromControls() {
            style = style.copy(
                fontSize = binding.sliderSize.value.toInt(),
                outlineWidth = binding.sliderOutline.value.toInt(),
                shadow = binding.sliderShadow.value.toInt(),
                marginV = binding.sliderMargin.value.toInt(),
                bold = binding.cbBold.isChecked,
                italic = binding.cbItalic.isChecked,
                backgroundBox = binding.cbBackgroundBox.isChecked,
                alignment = currentAlignment()
            )
            binding.tvSizeLabel.text = activity.getString(R.string.subtitle_size, style.fontSize)
            binding.tvOutlineLabel.text = activity.getString(R.string.subtitle_outline, style.outlineWidth)
            binding.tvShadowLabel.text = activity.getString(R.string.subtitle_shadow, style.shadow)
            binding.tvMarginLabel.text = activity.getString(R.string.subtitle_margin, style.marginV)
            renderPreview(binding, style, fonts)
        }

        binding.sliderSize.addOnChangeListener { _, _, _ -> syncFromControls() }
        binding.sliderOutline.addOnChangeListener { _, _, _ -> syncFromControls() }
        binding.sliderShadow.addOnChangeListener { _, _, _ -> syncFromControls() }
        binding.sliderMargin.addOnChangeListener { _, _, _ -> syncFromControls() }
        binding.cbBold.setOnCheckedChangeListener { _, _ -> syncFromControls() }
        binding.cbItalic.setOnCheckedChangeListener { _, _ -> syncFromControls() }
        binding.cbBackgroundBox.setOnCheckedChangeListener { _, _ -> syncFromControls() }
        binding.toggleVertical.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) syncFromControls()
        }
        binding.toggleHorizontal.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) syncFromControls()
        }

        syncFromControls()

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.subtitle_style_title)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel_button, null)
            .setPositiveButton(R.string.subtitle_style_confirm) { _, _ ->
                syncFromControls()
                style.save(activity)
                onConfirm(style)
            }
            .show()
    }

    private fun setupColorDropdown(
        activity: AppCompatActivity,
        view: android.widget.AutoCompleteTextView,
        colors: List<NamedColor>,
        selected: Int,
        onChanged: (Int) -> Unit
    ) {
        val names = colors.map { it.name }
        view.setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, names))
        val current = colors.firstOrNull { it.color == selected } ?: colors.first()
        view.setText(current.name, false)
        view.setOnClickListener { view.showDropDown() }
        view.setOnItemClickListener { _, _, position, _ ->
            onChanged(colors[position].color)
        }
    }

    private fun tintSwatch(view: android.view.View, color: Int) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(2, Color.parseColor("#BDBDBD"))
        }
        view.background = drawable
    }

    private fun renderPreview(
        binding: DialogSubtitleStyleBinding,
        style: SubtitleStyle,
        fonts: List<FontOption>
    ) {
        val preview = binding.tvPreview
        preview.setTextColor(style.textColor)
        preview.textSize = (style.fontSize * 0.7f).coerceIn(14f, 42f)
        preview.setShadowLayer(
            (style.outlineWidth + style.shadow).coerceAtLeast(1) * 1.4f,
            0f,
            style.shadow.toFloat(),
            style.outlineColor
        )
        if (style.backgroundBox) {
            val box = GradientDrawable().apply {
                setColor(
                    Color.argb(
                        0xB3,
                        Color.red(style.outlineColor),
                        Color.green(style.outlineColor),
                        Color.blue(style.outlineColor)
                    )
                )
                cornerRadius = 8f
            }
            preview.background = box
            preview.setPadding(16, 8, 16, 8)
        } else {
            preview.background = null
            preview.setPadding(8, 4, 8, 4)
        }

        val fontFile = fonts.firstOrNull { it.file.absolutePath == style.fontPath }?.file
            ?: style.fontPath?.let(::File)
        val baseTypeface = fontFile?.absolutePath?.let { path ->
            typefaceCache.getOrPut(path) {
                runCatching { Typeface.createFromFile(path) }.getOrDefault(Typeface.SANS_SERIF)
            }
        } ?: Typeface.SANS_SERIF
        val typeStyle = when {
            style.bold && style.italic -> Typeface.BOLD_ITALIC
            style.bold -> Typeface.BOLD
            style.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        preview.typeface = Typeface.create(baseTypeface, typeStyle)

        val gravity = when (style.alignment) {
            1 -> Gravity.BOTTOM or Gravity.START
            2 -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            3 -> Gravity.BOTTOM or Gravity.END
            4 -> Gravity.CENTER_VERTICAL or Gravity.START
            5 -> Gravity.CENTER
            6 -> Gravity.CENTER_VERTICAL or Gravity.END
            7 -> Gravity.TOP or Gravity.START
            8 -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            9 -> Gravity.TOP or Gravity.END
            else -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        val params = preview.layoutParams as FrameLayout.LayoutParams
        params.gravity = gravity
        params.bottomMargin = if (style.alignment in 1..3) (style.marginV / 3) else 0
        params.topMargin = if (style.alignment in 7..9) (style.marginV / 3) else 0
        preview.layoutParams = params
        preview.gravity = when {
            style.alignment % 3 == 1 -> Gravity.START
            style.alignment % 3 == 0 -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }
    }
}
