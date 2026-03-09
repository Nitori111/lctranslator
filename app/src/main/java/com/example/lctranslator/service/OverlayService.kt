package com.example.lctranslator.service

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.IBinder
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.lctranslator.LlmClient
import com.example.lctranslator.Prefs
import com.example.lctranslator.R
import kotlinx.coroutines.*

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.example.lctranslator.STOP_OVERLAY"
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIF_ID = 101
    }

    private lateinit var wm: WindowManager
    private lateinit var overlayRoot: LinearLayout
    private lateinit var tvCaption: TextView
    private lateinit var tvTranslation: TextView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var translateJob: Job? = null
    private var initX = 0; private var initY = 0
    private var initTouchX = 0f; private var initTouchY = 0f
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val captionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val caption = intent.getStringExtra(CaptionAccessibilityService.EXTRA_CAPTION) ?: return
            onCaptionReceived(caption)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        buildOverlayView()
        wm.addView(overlayRoot, layoutParams)
        registerReceiver(captionReceiver,
            IntentFilter(CaptionAccessibilityService.ACTION_NEW_CAPTION),
            RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        unregisterReceiver(captionReceiver)
        if (::overlayRoot.isInitialized) wm.removeView(overlayRoot)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onCaptionReceived(caption: String) {
        tvCaption.text = caption
        tvTranslation.text = getString(R.string.translating)
        translateJob?.cancel()
        translateJob = scope.launch {
            val result = runCatching {
                LlmClient.translate(
                    text = caption,
                    targetLang = Prefs.targetLang(this@OverlayService),
                    provider = Prefs.provider(this@OverlayService),
                    apiKey = Prefs.apiKey(this@OverlayService),
                    baseUrl = Prefs.baseUrl(this@OverlayService),
                    model = Prefs.model(this@OverlayService)
                )
            }.getOrElse { "⚠ ${it.message}" }
            tvTranslation.text = result
        }
    }

    private fun buildOverlayView() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 240
        }
        overlayRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(200, 0, 0, 0))
            setPadding(24, 12, 24, 12)
            elevation = 8f
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 16f)
                }
            }
        }
        tvCaption = TextView(this).apply {
            textSize = 12f; setTextColor(Color.LTGRAY); maxLines = 2
        }
        tvTranslation = TextView(this).apply {
            textSize = 16f; setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD); maxLines = 3
        }
        val divider = View(this).apply { setBackgroundColor(Color.argb(60, 255, 255, 255)) }
        val dp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            .apply { setMargins(0, 6, 0, 6) }
        overlayRoot.addView(tvCaption)
        overlayRoot.addView(divider, dp)
        overlayRoot.addView(tvTranslation)
        overlayRoot.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = layoutParams.x; initY = layoutParams.y
                    initTouchX = ev.rawX; initTouchY = ev.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initX + (ev.rawX - initTouchX).toInt()
                    layoutParams.y = initY + (ev.rawY - initTouchY).toInt()
                    wm.updateViewLayout(overlayRoot, layoutParams); true
                }
                else -> false
            }
        }
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Translation Overlay",
            NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(this, 0,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LC Translator active")
            .setContentText("Tap to stop")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true).build()
    }
}
