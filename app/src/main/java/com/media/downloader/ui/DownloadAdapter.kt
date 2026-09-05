package com.media.downloader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.media.downloader.R
import com.media.downloader.databinding.ItemDownloadBinding
import com.media.downloader.model.DownloadItem
import java.io.File

class DownloadAdapter(
    private val onShareClicked: (DownloadItem) -> Unit,
    private val onOpenClicked: (DownloadItem) -> Unit
) : ListAdapter<DownloadItem, DownloadAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDownloadBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadItem) {
            binding.tvFileName.text = item.title

            val ext = item.file.extension.uppercase()
            binding.tvFileDetails.text = "${item.formattedSize} • $ext"

            // Set appropriate icon
            when (ext.lowercase()) {
                "mp3", "m4a", "aac", "wav", "opus" -> {
                    binding.ivTypeIcon.setImageResource(R.drawable.ic_music)
                }
                "srt", "vtt" -> {
                    binding.ivTypeIcon.setImageResource(R.drawable.ic_subtitles)
                }
                else -> {
                    binding.ivTypeIcon.setImageResource(R.drawable.ic_video)
                }
            }

            binding.btnOpen.setOnClickListener { onOpenClicked(item) }
            binding.btnShare.setOnClickListener { onShareClicked(item) }
            binding.root.setOnClickListener { onOpenClicked(item) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
                return oldItem.file.absolutePath == newItem.file.absolutePath
            }

            override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
                return oldItem.sizeBytes == newItem.sizeBytes &&
                        oldItem.lastModified == newItem.lastModified
            }
        }

        fun openMediaFile(context: Context, file: File) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val ext = file.extension.lowercase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Open with"))
            } catch (e: Exception) {
                Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
            }
        }

        fun shareMediaFile(context: Context, file: File) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val ext = file.extension.lowercase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share media file"))
            } catch (e: Exception) {
                Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
