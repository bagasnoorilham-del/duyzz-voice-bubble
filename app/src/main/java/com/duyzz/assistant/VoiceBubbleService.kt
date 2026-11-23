package com.duyzz.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.math.hypot

class VoiceBubbleService : Service() {

    companion object {
        const val ACTION_START = "com.duyzz.assistant.action.START"
        const val ACTION_STOP = "com.duyzz.assistant.action.STOP"
        const val ACTION_TOGGLE_MUTE = "com.duyzz.assistant.action.TOGGLE_MUTE"
        const val ACTION_STATE_CHANGED = "com.duyzz.assistant.action.STATE_CHANGED"

        const val EXTRA_IS_RUNNING = "extra_is_running"
        const val EXTRA_IS_MUTED = "extra_is_muted"
        const val EXTRA_LAST_COMMAND = "extra_last_command"

        private const val CHANNEL_ID = "duyzz_voice_bubble_channel"
        private const val NOTIFICATION_ID = 2401
        private const val LONG_PRESS_DURATION_MS = 500L

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        @Volatile
        var isCurrentlyMuted: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var commandExecutor: VoiceCommandExecutor
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubbleView: View? = null
    private var bubbleIconView: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var quickMenuView: View? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private var isMuted: Boolean = false
    private var isListening: Boolean = false
    private var lastCommandText: String = "Menunggu perintah suara..."

    private val restartListeningRunnable = Runnable {
        if (!isMuted && isServiceRunning) {
            startVoiceListening()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        commandExecutor = VoiceCommandExecutor(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MUTE -> {
                if (isServiceRunning) {
                    setMuted(!isMuted)
                }
                return START_STICKY
            }
        }

        if (!hasRequiredPermissions()) {
            Toast.makeText(
                this,
                "Izin Overlay dan Mikrofon diperlukan untuk menjalankan Duyzz Voice Bubble.",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        isServiceRunning = true
        isCurrentlyMuted = isMuted

        if (bubbleView == null) {
            showFloatingBubble()
        }

        if (!isMuted) {
            startVoiceListening()
        }

        broadcastState()
        return START_STICKY
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        return hasOverlay && hasMic
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleMuteIntent = Intent(this, VoiceBubbleService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val toggleMutePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleMuteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceBubbleService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusTitle = if (isMuted) {
            "Duyzz Voice Bubble • MUTED 🔇"
        } else {
            "Duyzz Voice Bubble • LISTENING 🎙️"
        }

        val toggleLabel = if (isMuted) "Unmute" else "Mute"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic_notification)
            .setContentTitle(statusTitle)
            .setContentText(lastCommandText)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, toggleLabel, toggleMutePendingIntent)
            .addAction(0, "Matikan", stopPendingIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingBubble() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.layout_floating_bubble, null)
        val iconView = view.findViewById<TextView>(R.id.tvBubbleIcon)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 280
        }

        bubbleView = view
        bubbleIconView = iconView
        bubbleParams = params

        updateBubbleAppearance()

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var isLongPressTriggered = false

        val longPressRunnable = Runnable {
            isLongPressTriggered = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            toggleQuickMenu()
        }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isLongPressTriggered = false
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_DURATION_MS)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (!isDragging && !isLongPressTriggered) {
                        setMuted(!isMuted)
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    true
                }

                else -> false
            }
        }

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal menampilkan bubble: ${e.message}", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun updateBubbleAppearance() {
        val icon = bubbleIconView ?: return
        if (isMuted) {
            icon.text = "🔇"
            icon.setBackgroundResource(R.drawable.bg_bubble_muted)
        } else {
            icon.text = "🎙️"
            icon.setBackgroundResource(R.drawable.bg_bubble_active)
        }
    }

    private fun setMuted(mute: Boolean) {
        isMuted = mute
        isCurrentlyMuted = mute
        updateBubbleAppearance()
        updateQuickMenuUi()

        if (mute) {
            stopAndDestroyRecognizer()
            lastCommandText = "Mikrofon di-mute (Tap bola untuk mengaktifkan)"
        } else {
            lastCommandText = "Mendengarkan perintah suara (id-ID)..."
            startVoiceListening()
        }

        updateNotification()
        broadcastState()
    }

    private fun toggleQuickMenu() {
        if (quickMenuView != null) {
            dismissQuickMenu()
        } else {
            showQuickMenu()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showQuickMenu() {
        if (quickMenuView != null) return

        val inflater = LayoutInflater.from(this)
        val menuView = inflater.inflate(R.layout.layout_quick_menu, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        menuView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                dismissQuickMenu()
                true
            } else {
                false
            }
        }

        menuView.findViewById<Button>(R.id.btnMenuToggleMute).setOnClickListener {
            setMuted(!isMuted)
        }

        menuView.findViewById<Button>(R.id.btnMenuStopService).setOnClickListener {
            dismissQuickMenu()
            stopSelf()
        }

        menuView.findViewById<Button>(R.id.btnMenuClose).setOnClickListener {
            dismissQuickMenu()
        }

        quickMenuView = menuView
        updateQuickMenuUi()

        try {
            windowManager.addView(menuView, params)
        } catch (_: Exception) {
            quickMenuView = null
        }
    }

    private fun updateQuickMenuUi() {
        val menu = quickMenuView ?: return
        val statusText = menu.findViewById<TextView>(R.id.tvMenuStatus)
        val lastLogText = menu.findViewById<TextView>(R.id.tvMenuLastLog)
        val toggleBtn = menu.findViewById<Button>(R.id.btnMenuToggleMute)

        if (isMuted) {
            statusText.text = "STATUS: MUTED 🔇"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.neon_red))
            toggleBtn.text = "🎙️ UNMUTE MIKROFON"
            toggleBtn.setBackgroundResource(R.drawable.bg_cyber_button_cyan)
            toggleBtn.setTextColor(ContextCompat.getColor(this, R.color.cyber_bg))
        } else {
            statusText.text = "STATUS: LISTENING 🎙️"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.neon_cyan))
            toggleBtn.text = "🔇 MUTE MIKROFON"
            toggleBtn.setBackgroundResource(R.drawable.bg_cyber_button_red)
            toggleBtn.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }

        lastLogText.text = "Log: $lastCommandText"
    }

    private fun dismissQuickMenu() {
        val menu = quickMenuView ?: return
        try {
            windowManager.removeView(menu)
        } catch (_: Exception) {
        }
        quickMenuView = null
    }

    private fun startVoiceListening() {
        mainHandler.post {
            if (isMuted || !isServiceRunning) return@post

            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Toast.makeText(
                    this,
                    "Layanan SpeechRecognizer tidak tersedia di perangkat ini.",
                    Toast.LENGTH_LONG
                ).show()
                setMuted(true)
                return@post
            }

            mainHandler.removeCallbacks(restartListeningRunnable)

            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(recognitionListener)
                }
            }

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }

            try {
                isListening = true
                speechRecognizer?.startListening(recognizerIntent)
            } catch (_: Exception) {
                isListening = false
                recreateAndScheduleRestart(800L)
            }
        }
    }

    private fun stopAndDestroyRecognizer() {
        mainHandler.removeCallbacks(restartListeningRunnable)
        isListening = false
        speechRecognizer?.let { recognizer ->
            try {
                recognizer.stopListening()
                recognizer.cancel()
                recognizer.destroy()
            } catch (_: Exception) {
            }
        }
        speechRecognizer = null
    }

    private fun scheduleRestart(delayMs: Long) {
        mainHandler.removeCallbacks(restartListeningRunnable)
        if (!isMuted && isServiceRunning) {
            mainHandler.postDelayed(restartListeningRunnable, delayMs)
        }
    }

    private fun recreateAndScheduleRestart(delayMs: Long) {
        stopAndDestroyRecognizer()
        scheduleRestart(delayMs)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false
            if (isMuted || !isServiceRunning) return

            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    Toast.makeText(
                        this@VoiceBubbleService,
                        "Izin mikrofon dicabut. Mengaktifkan mode Mute.",
                        Toast.LENGTH_SHORT
                    ).show()
                    setMuted(true)
                }

                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    scheduleRestart(350L)
                }

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_AUDIO -> {
                    recreateAndScheduleRestart(800L)
                }

                else -> {
                    scheduleRestart(700L)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            if (isMuted || !isServiceRunning) return

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches.isNullOrEmpty()) {
                scheduleRestart(350L)
                return
            }

            var handled = false
            for (candidate in matches) {
                when (val result = commandExecutor.execute(candidate)) {
                    is VoiceCommandExecutor.ExecutionResult.MuteRequested -> {
                        lastCommandText = "Perintah: \"$candidate\" → Mute"
                        Toast.makeText(
                            this@VoiceBubbleService,
                            "🔇 Mikrofon di-mute",
                            Toast.LENGTH_SHORT
                        ).show()
                        setMuted(true)
                        return
                    }

                    is VoiceCommandExecutor.ExecutionResult.Executed -> {
                        lastCommandText = result.feedback
                        Toast.makeText(
                            this@VoiceBubbleService,
                            "⚡ ${result.feedback}",
                            Toast.LENGTH_SHORT
                        ).show()
                        handled = true
                        break
                    }

                    is VoiceCommandExecutor.ExecutionResult.NotFound -> {
                        // Coba kandidat alternatif jika ada
                    }
                }
            }

            if (!handled) {
                lastCommandText = "Terdengar: \"${matches.first()}\""
            }

            updateQuickMenuUi()
            updateNotification()
            broadcastState()
            scheduleRestart(650L)
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun broadcastState() {
        val intent = Intent(ACTION_STATE_CHANGED).apply {
            setPackage(packageName)
            putExtra(EXTRA_IS_RUNNING, isServiceRunning)
            putExtra(EXTRA_IS_MUTED, isMuted)
            putExtra(EXTRA_LAST_COMMAND, lastCommandText)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        isServiceRunning = false
        isCurrentlyMuted = false
        stopAndDestroyRecognizer()
        dismissQuickMenu()
        bubbleView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }
        bubbleView = null
        bubbleIconView = null
        broadcastState()
        super.onDestroy()
    }
}
