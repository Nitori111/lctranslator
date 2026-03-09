package com.example.lctranslator.ui

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lctranslator.LlmClient
import com.example.lctranslator.Prefs
import com.example.lctranslator.R
import com.example.lctranslator.databinding.ActivityMainBinding
import com.example.lctranslator.service.CaptionAccessibilityService
import com.example.lctranslator.service.OverlayService
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val messages = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter
    private var overlayRunning = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val captionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val caption = intent.getStringExtra(CaptionAccessibilityService.EXTRA_CAPTION) ?: return
            onNewCaption(caption)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        adapter = MessageAdapter(messages)
        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMessages.adapter = adapter
        binding.btnToggleOverlay.setOnClickListener { toggleOverlay() }
        binding.btnClear.setOnClickListener { clearHistory() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        registerReceiver(captionReceiver,
            IntentFilter(CaptionAccessibilityService.ACTION_NEW_CAPTION),
            RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        updateStatusBar()
        ensureOverlayPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        unregisterReceiver(captionReceiver)
    }

    private fun toggleOverlay() {
        if (!overlayRunning) {
            if (!isAccessibilityEnabled()) { promptAccessibility(); return }
            startService(Intent(this, OverlayService::class.java))
            overlayRunning = true
            binding.btnToggleOverlay.text = getString(R.string.stop_overlay)
        } else {
            startService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP })
            overlayRunning = false
            binding.btnToggleOverlay.text = getString(R.string.start_overlay)
        }
    }

    private fun clearHistory() {
        val size = messages.size; messages.clear()
        adapter.notifyItemRangeRemoved(0, size)
    }

    private fun updateStatusBar() {
        binding.tvStatus.text = if (isAccessibilityEnabled())
            "✅ Accessibility enabled  •  Provider: ${Prefs.provider(this).name}  •  → ${Prefs.targetLang(this)}"
        else getString(R.string.accessibility_prompt)
    }

    private fun onNewCaption(caption: String) {
        val msg = Message(original = caption, translated = getString(R.string.translating))
        messages.add(msg)
        adapter.notifyItemInserted(messages.size - 1)
        binding.rvMessages.smoothScrollToPosition(messages.size - 1)
        scope.launch {
            val result = runCatching {
                LlmClient.translate(
                    text = caption,
                    targetLang = Prefs.targetLang(this@MainActivity),
                    provider = Prefs.provider(this@MainActivity),
                    apiKey = Prefs.apiKey(this@MainActivity),
                    baseUrl = Prefs.baseUrl(this@MainActivity),
                    model = Prefs.model(this@MainActivity)
                )
            }.getOrElse { "⚠ ${it.message}" }
            adapter.updateLastTranslation(result)
        }
    }

    private fun ensureOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.overlay_permission_title)
                .setMessage(R.string.overlay_permission_msg)
                .setPositiveButton("Grant") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Later", null).show()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun promptAccessibility() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility Required")
            .setMessage("Enable \"LC Translator\" in Accessibility settings to capture Live Captions.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null).show()
    }
}
