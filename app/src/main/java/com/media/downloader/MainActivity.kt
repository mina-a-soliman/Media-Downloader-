package com.media.downloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.media.downloader.databinding.ActivityMainBinding
import com.media.downloader.model.DownloadItem
import com.media.downloader.model.DownloadType
import com.media.downloader.model.Platform
import com.media.downloader.service.DownloadService
import com.media.downloader.ui.DownloadAdapter
import com.media.downloader.util.MediaUtils
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var downloadAdapter: DownloadAdapter

    private var currentMode: DownloadType = DownloadType.VIDEO

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            triggerDownload()
        } else {
            Toast.makeText(this, "Storage and notification permissions are required to download files", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupUI()
        setupDropdowns()
        setupRecyclerView()
        observeProgress()
        handleIntent(intent)
        loadHistory()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val extractedUrl = extractUrl(sharedText)
                binding.etUrl.setText(extractedUrl)
                Snackbar.make(binding.root, "Link received from share", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun extractUrl(text: String): String {
        val parts = text.split("\\s+".toRegex())
        for (part in parts) {
            if (part.startsWith("http://") || part.startsWith("https://")) {
                return part
            }
        }
        return text.trim()
    }

    private fun setupUI() {
        // Paste button
        binding.btnPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString().orEmpty()
                val url = extractUrl(text)
                binding.etUrl.setText(url)
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        // URL text watcher for platform detection
        binding.etUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val url = s?.toString()?.trim().orEmpty()
                if (url.isNotBlank()) {
                    val platform = MediaUtils.detectPlatform(url)
                    binding.chipPlatform.text = platform.displayName
                    binding.chipPlatform.setChipBackgroundColorResource(android.R.color.transparent)
                    try {
                        binding.chipPlatform.chipBackgroundColor =
                            android.content.res.ColorStateList.valueOf(Color.parseColor(platform.badgeColorHex))
                    } catch (ignored: Exception) { }
                    binding.chipPlatform.visibility = View.VISIBLE
                } else {
                    binding.chipPlatform.visibility = View.GONE
                }
            }
        })

        // Download Mode selector
        binding.toggleGroupMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnModeVideo -> {
                        currentMode = DownloadType.VIDEO
                        binding.tilQuality.visibility = View.VISIBLE
                        binding.tilSubtitleLang.visibility = View.GONE
                    }
                    R.id.btnModeMp3 -> {
                        currentMode = DownloadType.AUDIO_MP3
                        binding.tilQuality.visibility = View.GONE
                        binding.tilSubtitleLang.visibility = View.GONE
                    }
                    R.id.btnModeSubtitles -> {
                        currentMode = DownloadType.SUBTITLES
                        binding.tilQuality.visibility = View.GONE
                        binding.tilSubtitleLang.visibility = View.VISIBLE
                    }
                }
            }
        }

        // Download button click
        binding.btnDownload.setOnClickListener {
            checkPermissionsAndDownload()
        }

        // Refresh downloads list
        binding.btnRefreshList.setOnClickListener {
            loadHistory()
        }
    }

    private fun setupDropdowns() {
        val qualities = resources.getStringArray(R.array.video_qualities_array)
        val qualityAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, qualities)
        binding.actvQuality.setAdapter(qualityAdapter)

        val subtitles = resources.getStringArray(R.array.subtitles_array)
        val subAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, subtitles)
        binding.actvSubtitleLang.setAdapter(subAdapter)
    }

    private fun setupRecyclerView() {
        downloadAdapter = DownloadAdapter(
            onOpenClicked = { item -> DownloadAdapter.openMediaFile(this, item.file) },
            onShareClicked = { item -> DownloadAdapter.shareMediaFile(this, item.file) }
        )
        binding.rvDownloads.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = downloadAdapter
        }
    }

    private fun checkPermissionsAndDownload() {
        val url = binding.etUrl.text?.toString()?.trim().orEmpty()
        if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            binding.tilUrl.error = "Please enter a valid video link"
            return
        }
        binding.tilUrl.error = null

        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            triggerDownload()
        }
    }

    private fun triggerDownload() {
        val url = binding.etUrl.text?.toString()?.trim().orEmpty()
        val quality = binding.actvQuality.text?.toString()
        val subLang = binding.actvSubtitleLang.text?.toString()

        DownloadService.startDownload(
            context = this,
            url = url,
            type = currentMode,
            quality = quality,
            subtitleLang = subLang
        )
        binding.cardProgress.visibility = View.VISIBLE
    }

    private fun observeProgress() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DownloadService.progressState.collect { state ->
                    if (state.isDownloading) {
                        binding.cardProgress.visibility = View.VISIBLE
                        binding.btnDownload.isEnabled = false

                        if (state.progress > 0) {
                            binding.progressBar.isIndeterminate = false
                            binding.progressBar.progress = state.progress.toInt()
                            binding.tvProgressPercent.text = "${state.progress.toInt()}% (ETA: ${formatEta(state.etaSeconds)})"
                        } else {
                            binding.progressBar.isIndeterminate = true
                            binding.tvProgressPercent.text = "Initializing…"
                        }

                        binding.tvProgressTitle.text = getString(R.string.status_downloading)
                        binding.tvProgressLog.text = state.logLine
                    } else {
                        binding.btnDownload.isEnabled = true
                        if (state.isCompleted) {
                            binding.cardProgress.visibility = View.GONE
                            Toast.makeText(this@MainActivity, R.string.status_completed, Toast.LENGTH_SHORT).show()
                            loadHistory()
                        } else if (state.error != null) {
                            binding.cardProgress.visibility = View.GONE
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Download Error")
                                .setMessage(state.error)
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
            }
        }
    }

    private fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "--:--"
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    private fun loadHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val downloadDir = MediaUtils.getDownloadDir(this@MainActivity)
            val files = downloadDir.listFiles()?.filter {
                it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            val items = files.map { file ->
                val ext = file.extension.lowercase()
                val type = when (ext) {
                    "mp3", "m4a", "aac", "wav" -> DownloadType.AUDIO_MP3
                    "srt", "vtt" -> DownloadType.SUBTITLES
                    else -> DownloadType.VIDEO
                }
                DownloadItem(
                    file = file,
                    title = file.name,
                    sizeBytes = file.length(),
                    formattedSize = MediaUtils.formatFileSize(file.length()),
                    type = type,
                    lastModified = file.lastModified()
                )
            }

            withContext(Dispatchers.Main) {
                downloadAdapter.submitList(items)
                if (items.isEmpty()) {
                    binding.tvEmptyHistory.visibility = View.VISIBLE
                    binding.rvDownloads.visibility = View.GONE
                } else {
                    binding.tvEmptyHistory.visibility = View.GONE
                    binding.rvDownloads.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_update_engine -> {
                updateEngine()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateEngine() {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle(R.string.status_updating)
            .setMessage("Fetching the latest yt-dlp engine from GitHub...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(applicationContext)
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Snackbar.make(binding.root, R.string.status_updated, Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Update Failed")
                        .setMessage(e.message ?: "Failed to update yt-dlp")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Media Downloader")
            .setMessage("Fast & Universal Downloader for YouTube, Facebook, and Instagram.\n\n" +
                    "Features:\n" +
                    "• Video downloading with selectable resolution\n" +
                    "• Pure MP3 audio extraction with FFmpeg\n" +
                    "• Subtitles extraction (.srt / .vtt)\n" +
                    "• Direct Android share integration\n" +
                    "• Built-in yt-dlp update mechanism")
            .setPositiveButton("OK", null)
            .show()
    }
}
