# 📱 Media Downloader (Android APK)

A native Android application designed to download **Videos** (selectable resolutions), **MP3 Audio**, and **Subtitles** from **YouTube, Facebook, and Instagram**, powered by `yt-dlp` and `FFmpeg`.

---

## ⚡ Zero Local Data / Bandwidth Requirement
You do **not** need to install Android Studio, Gradle, SDKs, or download GBs of dependencies on your local machine.
The included **GitHub Actions workflow** compiles and builds the APK in the cloud for free, and provides you with the ready-to-install `.apk` file.

---

## ✨ Features

- 🎥 **Video Download**:
  - Download high-definition videos from YouTube, Facebook (Watch & Reels), Instagram (Reels & Posts).
  - Selectable video resolutions:
    - **Best Available** (combines highest video + audio tracks)
    - **1080p (FHD)**
    - **720p (HD)**
    - **480p (SD)**
    - **360p (Low)**
  - Merges video and audio streams seamlessly into `.mp4` using embedded FFmpeg.
- 🎵 **Audio (MP3) Extraction**:
  - Automatically extracts audio from videos and converts to high-bitrate `.mp3` with embedded metadata (title, artist).
- 📝 **Subtitles Download**:
  - Download subtitles in `.srt` or `.vtt` format.
  - Multi-language support (English, Arabic, Spanish, French, or All).
- 📲 **Instant Android Share Integration**:
  - In YouTube, Facebook, or Instagram: tap **Share** ➔ choose **Media Downloader** ➔ link is automatically captured and ready to download.
- 🔄 **In-App Engine Updater**:
  - Platforms frequently change their video player formats. You can update the core `yt-dlp` engine directly inside the app with 1 tap (Toolbar ➔ **Update yt-dlp Engine**) without needing to reinstall the app.
- 📁 **Public Gallery & Music Scanner**:
  - Saved files go directly to your device's `Download/MediaDownloader/` folder and are registered with Android's MediaStore so they show up instantly in your Gallery and Music player.

---

## 🚀 How to Build and Get Your APK (Using Free Cloud GitHub Actions)

### Step 1: Initialize Git and Commit
In your terminal, navigate to this project folder:
```bash
git init
git add .
git commit -m "Initial commit of Media Downloader"
git branch -M main
```

### Step 2: Push to Your GitHub Repository
1. Go to [GitHub.com](https://github.com) and click **New Repository** (can be Public or Private).
2. Run the commands shown by GitHub to link and push your repository:
```bash
git remote add origin https://github.com/<your-username>/<your-repo-name>.git
git push -u origin main
```

### Step 3: Download Your APK from GitHub Actions
1. Open your repository on GitHub in your browser.
2. Click on the **Actions** tab at the top.
3. You will see the workflow run named **"Build Android APK"**.
4. Click on the workflow run. Once it finishes (~2–3 minutes):
   - Scroll down to the **Artifacts** section at the bottom.
   - Click on **`MediaDownloader-Lite-arm64-v8a`** to download the **~40 MB lightweight APK** (Recommended! Works on 99% of modern Android phones like Samsung, Xiaomi, Pixel, Oppo, OnePlus).
   - If you have an older 32-bit Android phone, download **`MediaDownloader-All-Variants`** which includes `armeabi-v7a` (~35 MB) as well.
5. Extract the zip file, transfer the `.apk` to your phone, and install it!

> [!TIP]
> Why was the original APK 195 MB? The universal APK bundles 4 different device architectures (Python + FFmpeg for ARM64, ARMv7, x86, and x86_64). By downloading the `arm64-v8a` APK, you only download what your phone needs, cutting download size by **~75% to 80%**!

---

## 📲 Installing on Your Android Phone

1. Transfer or download `MediaDownloader-v1.0.0.apk` onto your phone.
2. Open your phone's File Manager and tap the APK file.
3. If prompted with *"For your security, your phone is not allowed to install unknown apps from this source"*, tap **Settings** and enable **"Allow from this source"**.
4. Tap **Install**.

---

## 🛠️ Project Structure

```
YOUTBUBEDoownloader/
├── .github/
│   └── workflows/
│       └── build-apk.yml               # GitHub Actions CI workflow to build APK
├── app/
│   ├── build.gradle.kts                # App module build configuration
│   ├── proguard-rules.pro              # Proguard optimization rules
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml     # Manifest with permissions & share intent
│           ├── java/com/media/downloader/
│           │   ├── DownloaderApp.kt    # Application class (initializes yt-dlp & FFmpeg)
│           │   ├── MainActivity.kt     # Main UI, clipboard, mode toggles, updater
│           │   ├── model/
│           │   │   ├── DownloadItem.kt # Downloaded file model
│           │   │   └── DownloadType.kt # Enums for Video, MP3, Subtitles, Platforms
│           │   ├── service/
│           │   │   └── DownloadService.kt # Foreground service for background downloads
│           │   ├── ui/
│           │   │   └── DownloadAdapter.kt # RecyclerView adapter with Open & Share
│           │   └── util/
│           │       └── MediaUtils.kt   # Platform detection, yt-dlp options, MediaStore
│           └── res/                    # UI layouts, icons, strings, colors, themes
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties   # Gradle 8.7 distribution
├── build.gradle.kts                    # Root build configuration
├── settings.gradle.kts                 # Repositories & module inclusion
├── gradle.properties                   # JVM & AndroidX optimization flags
└── README.md
```
