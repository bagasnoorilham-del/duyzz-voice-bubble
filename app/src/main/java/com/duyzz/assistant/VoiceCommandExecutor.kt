package com.duyzz.assistant

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.net.URLEncoder
import java.util.Locale

class VoiceCommandExecutor(private val context: Context) {

    sealed class ExecutionResult {
        data class Executed(val feedback: String) : ExecutionResult()
        data object MuteRequested : ExecutionResult()
        data class NotFound(val spokenText: String) : ExecutionResult()
    }

    private val appLauncherHelper = AppLauncherHelper(context)

    fun execute(rawText: String): ExecutionResult {
        val cleaned = rawText
            .trim()
            .lowercase(Locale("id", "ID"))
            .replace(Regex("\\s+"), " ")

        if (cleaned.isBlank()) {
            return ExecutionResult.NotFound(rawText)
        }

        // 1. Perintah Mute / Diam
        if (cleaned == "mute" ||
            cleaned == "diam" ||
            cleaned == "matikan mic" ||
            cleaned == "matikan mik" ||
            cleaned == "senyap" ||
            cleaned.startsWith("mute ") ||
            cleaned.startsWith("diam ")
        ) {
            return ExecutionResult.MuteRequested
        }

        // 2. Perintah "cari di tiktok [kata kunci]"
        val tiktokPrefixes = listOf("cari di tiktok ", "cari di tik tok ")
        for (prefix in tiktokPrefixes) {
            if (cleaned.startsWith(prefix)) {
                val query = cleaned.removePrefix(prefix).trim()
                if (query.isNotEmpty()) {
                    openTikTokSearch(query)
                    return ExecutionResult.Executed("Mencari \"$query\" di TikTok")
                }
            }
        }

        // 3. Perintah "cari di youtube [kata kunci]"
        val youtubePrefixes = listOf("cari di youtube ", "cari di yutub ")
        for (prefix in youtubePrefixes) {
            if (cleaned.startsWith(prefix)) {
                val query = cleaned.removePrefix(prefix).trim()
                if (query.isNotEmpty()) {
                    openYouTubeSearch(query)
                    return ExecutionResult.Executed("Mencari \"$query\" di YouTube")
                }
            }
        }

        // 4. Perintah "cari [kata kunci]" -> Google Search
        if (cleaned.startsWith("cari ")) {
            val query = cleaned.removePrefix("cari ").trim()
            if (query.isNotEmpty()) {
                openGoogleSearch(query)
                return ExecutionResult.Executed("Mencari \"$query\" di Google")
            }
        }

        // 5. Perintah "buka [nama aplikasi]"
        if (cleaned.startsWith("buka ")) {
            val appQuery = cleaned.removePrefix("buka ").trim()
            val launchedLabel = appLauncherHelper.launchApp(appQuery)
            return if (launchedLabel != null) {
                ExecutionResult.Executed("Membuka $launchedLabel")
            } else {
                ExecutionResult.NotFound("Aplikasi \"$appQuery\" tidak ditemukan")
            }
        }

        // 6. Langsung sebut "[nama aplikasi]"
        val directLaunchedLabel = appLauncherHelper.launchApp(cleaned)
        if (directLaunchedLabel != null) {
            return ExecutionResult.Executed("Membuka $directLaunchedLabel")
        }

        return ExecutionResult.NotFound(rawText)
    }

    private fun openTikTokSearch(query: String) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUri = Uri.parse("https://www.tiktok.com/search?q=$encoded")
        val tiktokPackages = listOf(
            "com.ss.android.ugc.trill",      // TikTok Asia Tenggara / Indonesia
            "com.zhiliaoapp.musically",      // TikTok Global
            "com.zhiliaoapp.musically.go"    // TikTok Lite
        )

        val installedPkg = tiktokPackages.firstOrNull { isPackageInstalled(it) }
        if (installedPkg != null) {
            val appIntent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                setPackage(installedPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStartActivity(appIntent)) return
        }

        val fallbackIntent = Intent(Intent.ACTION_VIEW, searchUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        tryStartActivity(fallbackIntent)
    }

    private fun openYouTubeSearch(query: String) {
        val encoded = URLEncoder.encode(query, "UTF-8")

        if (isPackageInstalled("com.google.android.youtube")) {
            val appSearchIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStartActivity(appSearchIntent)) return
        }

        val webUri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
        val fallbackIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        tryStartActivity(fallbackIntent)
    }

    private fun openGoogleSearch(query: String) {
        val webSearchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStartActivity(webSearchIntent)) return

        val encoded = URLEncoder.encode(query, "UTF-8")
        val browserIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=$encoded")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        tryStartActivity(browserIntent)
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun tryStartActivity(intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
