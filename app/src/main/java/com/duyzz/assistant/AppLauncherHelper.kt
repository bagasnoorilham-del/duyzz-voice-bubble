package com.duyzz.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import java.util.Locale

class AppLauncherHelper(private val context: Context) {

    data class InstalledApp(
        val label: String,
        val normalizedLabel: String,
        val packageName: String
    )

    private val indonesianAliases: Map<String, List<String>> = mapOf(
        "wa" to listOf("whatsapp"),
        "watsap" to listOf("whatsapp"),
        "ig" to listOf("instagram"),
        "yt" to listOf("youtube"),
        "yutub" to listOf("youtube"),
        "fb" to listOf("facebook"),
        "tik tok" to listOf("tiktok"),
        "tele" to listOf("telegram"),
        "peta" to listOf("maps", "google maps"),
        "kamera" to listOf("camera", "kamera"),
        "galeri" to listOf("gallery", "galeri", "photos", "foto"),
        "kalkulator" to listOf("calculator", "kalkulator"),
        "pengaturan" to listOf("settings", "pengaturan", "setelan"),
        "setelan" to listOf("settings", "pengaturan", "setelan"),
        "playstore" to listOf("google play store", "play store"),
        "krom" to listOf("chrome"),
        "tokped" to listOf("tokopedia"),
        "sopi" to listOf("shopee"),
        "shopi" to listOf("shopee")
    )

    fun launchApp(rawQuery: String): String? {
        val cleaned = normalize(rawQuery)
        if (cleaned.isBlank()) return null

        val installedApps = getLaunchableApps()
        if (installedApps.isEmpty()) return null

        val candidates = buildList {
            add(cleaned)
            indonesianAliases[cleaned]?.let { addAll(it) }
        }

        val matchedApp = findBestMatch(candidates, installedApps) ?: return null
        val launchIntent = context.packageManager.getLaunchIntentForPackage(matchedApp.packageName)
            ?: return null

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return try {
            context.startActivity(launchIntent)
            matchedApp.label
        } catch (_: Exception) {
            null
        }
    }

    private fun findBestMatch(
        queries: List<String>,
        apps: List<InstalledApp>
    ): InstalledApp? {
        // 1. Exact label match
        for (q in queries) {
            apps.firstOrNull { it.normalizedLabel == q }?.let { return it }
        }

        // 2. Prefix match (prefer shortest label)
        for (q in queries) {
            apps.filter { it.normalizedLabel.startsWith(q) }
                .minByOrNull { it.normalizedLabel.length }
                ?.let { return it }
        }

        // 3. Contains match (only for queries >= 3 chars to avoid false positives)
        for (q in queries) {
            if (q.length >= 3) {
                apps.filter { it.normalizedLabel.contains(q) }
                    .minByOrNull { it.normalizedLabel.length }
                    ?.let { return it }
            }
        }

        // 4. Package name match (compact query without spaces)
        for (q in queries) {
            val compact = q.replace(" ", "")
            if (compact.length >= 3) {
                apps.filter { it.packageName.lowercase(Locale.ROOT).contains(compact) }
                    .minByOrNull { it.normalizedLabel.length }
                    ?.let { return it }
            }
        }

        return null
    }

    private fun getLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                InstalledApp(
                    label = label,
                    normalizedLabel = normalize(label),
                    packageName = pkg
                )
            }
            .distinctBy { it.packageName }
    }

    private fun normalize(input: String): String {
        return input
            .trim()
            .lowercase(Locale("id", "ID"))
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
