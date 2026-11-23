package com.duyzz.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvMicStatus: TextView
    private lateinit var tvNotificationStatus: TextView
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvLastLog: TextView

    private lateinit var btnRequestOverlay: Button
    private lateinit var btnRequestMic: Button
    private lateinit var btnRequestNotification: Button
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissionUi()
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshPermissionUi()
        if (!granted) {
            Toast.makeText(
                this,
                "Izin mikrofon diperlukan untuk mendengarkan perintah suara.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshPermissionUi()
        if (!granted) {
            Toast.makeText(
                this,
                "Izin notifikasi disarankan agar Foreground Service tampil stabil.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VoiceBubbleService.ACTION_STATE_CHANGED) {
                val running = intent.getBooleanExtra(VoiceBubbleService.EXTRA_IS_RUNNING, false)
                val muted = intent.getBooleanExtra(VoiceBubbleService.EXTRA_IS_MUTED, false)
                val lastCmd = intent.getStringExtra(VoiceBubbleService.EXTRA_LAST_COMMAND)
                updateServiceStatusUi(running, muted, lastCmd)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupListeners()
        refreshPermissionUi()
        updateServiceStatusUi(
            VoiceBubbleService.isServiceRunning,
            VoiceBubbleService.isCurrentlyMuted,
            null
        )
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(VoiceBubbleService.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            this,
            serviceStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionUi()
        updateServiceStatusUi(
            VoiceBubbleService.isServiceRunning,
            VoiceBubbleService.isCurrentlyMuted,
            null
        )
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(serviceStateReceiver)
        } catch (_: Exception) {
        }
    }

    private fun bindViews() {
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvMicStatus = findViewById(R.id.tvMicStatus)
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        tvLastLog = findViewById(R.id.tvLastLog)

        btnRequestOverlay = findViewById(R.id.btnRequestOverlay)
        btnRequestMic = findViewById(R.id.btnRequestMic)
        btnRequestNotification = findViewById(R.id.btnRequestNotification)
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)
    }

    private fun setupListeners() {
        btnRequestOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        btnRequestMic.setOnClickListener {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        btnRequestNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(
                    this,
                    "Izin notifikasi otomatis aktif di Android versi ini.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnStartService.setOnClickListener {
            handleStartService()
        }

        btnStopService.setOnClickListener {
            val stopIntent = Intent(this, VoiceBubbleService::class.java).apply {
                action = VoiceBubbleService.ACTION_STOP
            }
            startService(stopIntent)
            stopService(Intent(this, VoiceBubbleService::class.java))
            updateServiceStatusUi(false, false, "Service dihentikan oleh pengguna.")
        }
    }

    private fun handleStartService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Aktifkan izin Overlay (Tampil di atas aplikasi lain) terlebih dahulu.",
                Toast.LENGTH_LONG
            ).show()
            requestOverlayPermission()
            return
        }

        if (!hasMicPermission()) {
            Toast.makeText(
                this,
                "Berikan izin Mikrofon (RECORD_AUDIO) terlebih dahulu.",
                Toast.LENGTH_LONG
            ).show()
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val startIntent = Intent(this, VoiceBubbleService::class.java).apply {
            action = VoiceBubbleService.ACTION_START
        }
        ContextCompat.startForegroundService(this, startIntent)
        updateServiceStatusUi(true, false, "Menjalankan Duyzz Voice Bubble...")
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun refreshPermissionUi() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val micGranted = hasMicPermission()
        val notifGranted = hasNotificationPermission()

        applyPermissionStatus(tvOverlayStatus, btnRequestOverlay, overlayGranted)
        applyPermissionStatus(tvMicStatus, btnRequestMic, micGranted)
        applyPermissionStatus(tvNotificationStatus, btnRequestNotification, notifGranted)
    }

    private fun applyPermissionStatus(statusView: TextView, actionButton: Button, granted: Boolean) {
        if (granted) {
            statusView.text = "AKTIF"
            statusView.setTextColor(ContextCompat.getColor(this, R.color.neon_cyan))
            actionButton.isEnabled = false
            actionButton.alpha = 0.45f
            actionButton.text = "DIIZINKAN"
        } else {
            statusView.text = "BELUM"
            statusView.setTextColor(ContextCompat.getColor(this, R.color.neon_red))
            actionButton.isEnabled = true
            actionButton.alpha = 1.0f
            actionButton.text = "IZINKAN"
        }
    }

    private fun updateServiceStatusUi(running: Boolean, muted: Boolean, lastCommand: String?) {
        if (running) {
            if (muted) {
                tvServiceStatus.text = "● SERVICE AKTIF — MUTED 🔇"
                tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_red))
            } else {
                tvServiceStatus.text = "● SERVICE AKTIF — LISTENING 🎙️"
                tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_cyan))
            }
        } else {
            tvServiceStatus.text = "○ SERVICE BERHENTI"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        if (!lastCommand.isNullOrBlank()) {
            tvLastLog.text = lastCommand
        }
    }
}
