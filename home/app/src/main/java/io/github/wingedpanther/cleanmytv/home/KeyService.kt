package io.github.wingedpanther.cleanmytv.home

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
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
    fun add(code: Int) {
        keys.addLast(Triple(++seq, code, KeyEvent.keyCodeToString(code)))
        while (keys.size > 30) keys.removeFirst()
    }

    @Synchronized
    fun json(): JSONArray = JSONArray(keys.map { (s, c, n) -> JSONObject().put("seq", s).put("code", c).put("name", n) })
}

/**
 * Remote buttons and the app lock's guard.
 *
 * Remote buttons, mapped from CleanMyTV. Only buttons in the map are handled (and swallowed, so the
 * TV doesn't also do its own thing); every other button returns false and reaches apps untouched.
 * That is the line upstream's key helper crossed in #26.
 */
class KeyService : AccessibilityService() {
    // An unlock lasts until the TV sleeps, unless the owner picked 15 minutes or every time.
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { Lock.lockAll() }
    }

    override fun onServiceConnected() {
        running = this
        ConfigStore.load(this)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        // Some builds ignore the flag in the XML; asking again here turns key filtering on.
        serviceInfo = serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS }
        Log.i(TAG, "key service on, flags ${serviceInfo.flags}")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) KeyLog.add(event.keyCode)
        val cfg = ConfigStore.config.value
        if (!cfg.buttonsOn) return false
        val map = cfg.buttons.firstOrNull { it.code == event.keyCode } ?: return false
        if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) runCatching { perform(map) }.onFailure { Log.w(TAG, "button ${map.code}: $it") }
        return true
    }

    private fun perform(b: ButtonMap) {
        when (b.action) {
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "none" -> Unit
            "inputs" -> startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.OPEN, "inputs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "music" -> ConfigStore.update(this, JSONObject().put("visualiser", !ConfigStore.config.value.visualiser))
            "panel", "themes" -> startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.OPEN, b.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "next_wallpaper" -> ThemePack.next(this)
            "fireplace" -> startActivity(Intent(this, FireplaceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "app" -> b.app?.let { pkg ->
                (packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg))
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { startActivity(it) }
            }
            else -> quickActions()[b.action]?.let { a -> resolve(this, a)?.let { startActivity(it) } }
        }
    }

    /** A locked app came to the front, however it was opened: cover it with the PIN pad. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Fire OS won't let the Amazon launcher be disabled or replaced as Home, so it opens at boot and
        // on every Home press. With remote buttons on, bring CleanMyTV Home back in front of it. Android
        // blocks app starts for about 5 s after a Home press, hence the retry after that window.
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

        /** The service while it's on, so the fireplace timer can put the TV to sleep. */
        @Volatile var running: KeyService? = null

        /** Screen off, as the power button would. Needs the key service on; false when it isn't. */
        fun sleep(): Boolean = running?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false

        /**
         * Some TVs take this service out of the accessibility list when they restart, so mapped buttons and
         * the app lock stopped until the desktop app saved something (#78). Home puts it back itself, with
         * the secure-settings permission CleanMyTV granted. Without that permission nothing changes.
         */
        fun ensureOn(ctx: Context) {
            val cfg = ConfigStore.config.value
            if (!cfg.buttonsOn && !cfg.lockOn) return
            val me = ComponentName(ctx, KeyService::class.java)
            val r = ctx.contentResolver
            val list = Settings.Secure.getString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .split(':').filter { it.isNotBlank() && it != "null" }
            val listed = list.any { ComponentName.unflattenFromString(it) == me }
            val on = Settings.Secure.getInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
            if (listed && on) return
            runCatching {
                if (!listed) Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (list + me.flattenToString()).joinToString(":"))
                Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                Log.i(TAG, "key service switched back on")
            }.onFailure { Log.w(TAG, "key service: $it") }
        }
    }
}

/** At boot, before anyone presses Home: Fire TV opens Amazon's launcher first and needs the service to leave it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        ConfigStore.load(c)
        KeyService.ensureOn(c)
    }
}
