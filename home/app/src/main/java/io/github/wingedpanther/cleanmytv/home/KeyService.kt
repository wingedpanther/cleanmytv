package io.github.wingedpanther.cleanmytv.home

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import org.json.JSONArray
import org.json.JSONObject

/** The last buttons the TV received, for the desktop app's button finder. */
object KeyLog {
    private val keys = ArrayDeque<Triple<Int, Int, String>>() // seq, code, name
    private var seq = 0

    @Synchronized
    fun add(code: Int, name: String? = null) {
        val label = name?.takeIf { it.isNotEmpty() } ?: KeyEvent.keyCodeToString(code)
        keys.addLast(Triple(++seq, code, label))
        while (keys.size > 30) keys.removeFirst()
    }

    @Synchronized
    fun json(): JSONArray = JSONArray(keys.map { (s, c, n) -> JSONObject().put("seq", s).put("code", c).put("name", n) })
}

class KeyService : AccessibilityService() {
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { Lock.lockAll() }
    }

    override fun onServiceConnected() {
        running = this
        ConfigStore.load(this)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        // Do NOT set FLAG_REQUEST_FILTER_KEY_EVENTS so native BLE volume repeat stays intact.
        // Instead, stream raw key events via LocalAdb PTY.
        LocalAdb.ensureDaemon(this)
        Log.i(TAG, "key service on (non-filtering mode), LocalAdb started")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == FIRE_LAUNCHER && ConfigStore.config.value.buttonsOn) {
            redirect.removeCallbacksAndMessages(null)
            for (d in longArrayOf(300, 1200, 5300)) redirect.postDelayed({ runCatching { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }.onFailure { Log.w(TAG, "fire home: $it") } }, d)
            return
        }
        if (pkg != FIRE_LAUNCHER) redirect.removeCallbacksAndMessages(null)
        Lock.seen(pkg, lockScreen = event.className?.toString()?.endsWith("LockActivity") == true)
        if (pkg == packageName || pkg == LockActivity.showing || Lock.justBackedOut(pkg)) return
        if (Lock.needsPin(this, pkg)) runCatching { startActivity(LockActivity.intent(this, pkg)) }.onFailure { Log.w(TAG, "lock $pkg: $it") }
    }

    private val redirect = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onDestroy() {
        redirect.removeCallbacksAndMessages(null)
        if (running === this) running = null
        runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    companion object {
        private const val FIRE_LAUNCHER = "com.amazon.tv.launcher"
        @Volatile var running: KeyService? = null
        fun sleep(): Boolean = running?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
    }
}
