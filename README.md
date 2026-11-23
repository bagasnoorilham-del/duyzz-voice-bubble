# Duyzz Voice Bubble (`com.duyzz.assistant`)

Aplikasi asisten suara Android Native (Kotlin + Gradle) **100% lokal di perangkat** tanpa server atau backend, dirancang dengan antarmuka **Dark Mode Cyber-Console** (`#090D16`, aksen Cyan `#00F5D4` dan Merah `#FF2A5F`).

## Fitur Utama
- **Foreground Service 24 Jam**: Menggunakan `foregroundServiceType="microphone"` dengan `START_STICKY` dan notifikasi persisten.
- **Floating Bubble Interaktif (`SYSTEM_ALERT_WINDOW`)**:
  - Bebas digeser (*drag & drop*) di atas aplikasi lain.
  - **Tap 1x**: Toggle **Mute / Unmute** instan (Cyan `#00F5D4` + 🎙️ saat aktif mendengar, Merah `#FF2A5F` + 🔇 saat di-mute).
  - **Tekan Lama (Long Press)**: Membuka **Quick Menu** melayang berisi daftar perintah suara, tombol Mute/Unmute, dan tombol Matikan Service.
- **Continuous SpeechRecognizer (`id-ID`)**: Otomatis *looping* mendengarkan perintah Bahasa Indonesia saat Unmute dan langsung melepaskan mikrofon total saat Mute.
- **Eksekutor Perintah Suara Lokal**:
  - `"buka [nama aplikasi]"` atau langsung `"[nama aplikasi]"` → mencari & membuka aplikasi terinstal via `PackageManager`.
  - `"cari di tiktok [kata kunci]"` → langsung membuka pencarian di TikTok.
  - `"cari di youtube [kata kunci]"` → langsung membuka pencarian di YouTube.
  - `"cari [kata kunci]"` → langsung membuka pencarian Google.
  - `"mute"` atau `"diam"` → langsung mengubah bola ke mode Mute.

## Cara Build APK Otomatis di GitHub Actions
1. Buat repository baru di GitHub.
2. Push seluruh isi folder proyek ini ke branch `main` atau `master`:
   ```bash
   git init
   git add .
   git commit -m "Initial commit: Duyzz Voice Bubble"
   git branch -M main
   git remote add origin https://github.com/USERNAME/duyzz-voice-bubble.git
   git push -u origin main
   ```
3. Buka tab **Actions** di repository GitHub Anda, tunggu workflow **Build Duyzz Voice Bubble APK** selesai, lalu unduh `duyzz-voice-bubble-debug-apk` (berisi `app-debug.apk`) dari bagian **Artifacts**.
