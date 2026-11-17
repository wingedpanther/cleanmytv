package io.github.wingedpanther.cleanmytv.home

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    /** Where the log is now, so the on-TV button finder only looks at presses after it. */
    @Synchronized
    fun mark(): Int = seq

    /** Key codes pressed after [mark], oldest first. */
    @Synchronized
    fun since(mark: Int): List<Int> = keys.filter { it.first > mark }.map { it.second }
}

/**
 * Remote buttons and the app lock's guard.
 *
 * Remote buttons, mapped from CleanMyTV. Only buttons in the map are handled (and swallowed, so the
 * TV doesn't also do its own thing); every other button returns false and reaches apps untouched.
 * That is the line upstream's key helper crossed in #26.
 *
 * Seeing buttons at all (key filtering) is only on while a button is mapped, or the desktop app's button
 * finder is listening. On some TVs filtering stops held buttons repeating, so volume went up one step per
 * press (#87); owners who map nothing keep the TV's own behaviour. A volume change stops the filtering for
 * a few seconds, so holding the button repeats again after the first step. Most TVs never show this
 * service the volume buttons, so it goes by the volume changing, as upstream's volume_pauses_filter does.
 *
 * It also sends a launcher that keeps taking Home back to CleanMyTV Home ([HomeConfig.stockRedirect]), and
 * goes back to the TV's input after the TV wakes ([PowerOn]).
 */
class KeyService : AccessibilityService() {
    // An unlock lasts until the TV sleeps, unless the owner picked 15 minutes or every time.
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                // A TV switched off again inside the wait opens nothing.
                Intent.ACTION_SCREEN_OFF -> { timer.removeCallbacks(wake); Lock.lockAll(); PowerOn.asleep(c, front); Limits.front(c, null) }
                Intent.ACTION_SCREEN_ON -> { timer.removeCallbacks(wake); timer.postDelayed(wake, PowerOn.delayMs(ConfigStore.config.value)) }
            }
        }
    }

    private val volume = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (System.currentTimeMillis() >= listenUntil && needsKeys(ConfigStore.config.value, false)) pauseForVolume()
        }
    }

    /** Power on's look at the screen, after the wait; it reads [front] when it runs. */
    private val wake = Runnable { PowerOn.awake(this, front) }

    /** The app whose screen is in front, from window changes; the volume bar and other overlays don't count. */
    private var front: String? = null

    override fun onServiceConnected() {
        running = this
        ConfigStore.load(this)
        registerReceiver(screen, IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) })
        registerReceiver(volume, IntentFilter(VOLUME_CHANGED))
        Keyboard.watch(this)
        // A cold start: the service comes up with the TV, and the input it was on may be due back.
        if (SystemClock.elapsedRealtime() < 5 * 60_000L) timer.postDelayed(wake, PowerOn.delayMs(ConfigStore.config.value) + PowerOn.SETTLE_MS)
        scope.launch { ConfigStore.config.map { needsKeys(it, false) }.distinctUntilChanged().collect { filterKeys() } }
        Log.i(TAG, "key service on")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Asks Android for buttons only while something here wants them. */
    private fun filterKeys() {
        val now = System.currentTimeMillis()
        val on = needsKeys(ConfigStore.config.value, now < listenUntil, now < pausedUntil)
        runCatching {
            serviceInfo = serviceInfo.apply {
                flags = if (on) flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS else flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
            }
        }.onFailure { Log.w(TAG, "key filter: $it") }
        Log.i(TAG, "key filter ${if (on) "on" else "off"}")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) KeyLog.add(event.keyCode)
        val cfg = ConfigStore.config.value
        val map = if (cfg.buttonsOn) cfg.buttons.firstOrNull { it.code == event.keyCode } else null
        if (map == null) {
            if (event.keyCode in VOLUME_KEYS && System.currentTimeMillis() >= listenUntil) pauseForVolume()
            return false
        }
        if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) runCatching { perform(map) }.onFailure { Log.w(TAG, "button ${map.code}: $it") }
        return true
    }

    /** This press went through as usual; the ones after it, for a few seconds, never pass the filter. */
    private fun pauseForVolume() {
        val first = System.currentTimeMillis() >= pausedUntil
        pausedUntil = System.currentTimeMillis() + VOLUME_PAUSE_MS
        if (first) filterKeys()
        timer.removeCallbacks(endPause)
        timer.postDelayed(endPause, VOLUME_PAUSE_MS + 100)
    }

    private fun perform(b: ButtonMap) {
        when (b.action) {
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "none" -> Unit
            "inputs" -> startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.OPEN, "inputs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "music" -> ConfigStore.update(this, JSONObject().put("visualiser", !ConfigStore.config.value.visualiser))
            "timer" -> SleepTimer.next(this)
            "panel", "themes", "customize" -> startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.OPEN, b.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "next_wallpaper" -> ThemePack.next(this)
            "fireplace" -> startActivity(Intent(this, FireplaceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "memory" -> if (Memory.canClean) scope.launch { Memory.cleanAndSay(this@KeyService) }
            "brightness" -> android.widget.Toast.makeText(this, TvSettings.nextBrightness(this)?.let { tr("Brightness {n}%", "n" to it) } ?: tr("Brightness needs CleanMyTV's permission"), android.widget.Toast.LENGTH_SHORT).show()
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
        val cls = event.className?.toString()
        val activity = cls != null && isActivity(pkg, cls)
        if (activity) { front = pkg; Limits.front(this, pkg) }
        // Fire OS won't let the Amazon launcher be disabled or replaced as Home, and Google TV's launcher may
        // be left on for apps that need it (#99), so it opens at boot and on every Home press. Bring CleanMyTV
        // Home back in front of its home screen, but not of its other screens: Fire TV's Settings live in
        // Amazon's launcher. Android blocks app starts for about 5 s after a Home press, hence the retry.
        if (pkg == ConfigStore.config.value.stockRedirect) {
            if (sendsBack(cls, homeActivities(pkg), activity)) {
                redirect.removeCallbacksAndMessages(null)
                for (d in longArrayOf(300, 1200, 5300)) redirect.postDelayed({ runCatching { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }.onFailure { Log.w(TAG, "home back: $it") } }, d)
                return
            }
            if (activity) redirect.removeCallbacksAndMessages(null)
        } else redirect.removeCallbacksAndMessages(null)
        Lock.seen(pkg, lockScreen = event.className?.toString()?.endsWith("LockActivity") == true)
        if (pkg == packageName || pkg == LockActivity.showing || Lock.justBackedOut(pkg)) return
        if (Lock.needsPin(this, pkg)) runCatching { startActivity(LockActivity.intent(this, pkg)) }.onFailure { Log.w(TAG, "lock $pkg: $it") }
        else if (activity) checkLimit(pkg)
    }

    /** Daily limits: out of time now, the PIN pad covers the app; otherwise look again when its time runs out. */
    private fun checkLimit(pkg: String) {
        timer.removeCallbacks(limitCheck)
        val left = Limits.left(this, pkg) ?: return
        if (left > 0) { limitCheck = Runnable { if (front == pkg) checkLimit(pkg) }; timer.postDelayed(limitCheck, left + 500); return }
        runCatching { startActivity(LockActivity.intent(this, pkg, limit = true)) }.onFailure { Log.w(TAG, "limit $pkg: $it") }
    }
    private var limitCheck = Runnable {}

    private val activities = HashMap<String, Boolean>()
    private val homes = HashMap<String, Set<String>>()

    /** A window class that is one of the app's activities, as opposed to a dialog, toast or the volume bar. */
    private fun isActivity(pkg: String, cls: String): Boolean = activities.getOrPut("$pkg/$cls") {
        runCatching { packageManager.getActivityInfo(ComponentName(pkg, cls), PackageManager.MATCH_DISABLED_COMPONENTS) }.isSuccess
    }

    private fun homeActivities(pkg: String): Set<String> = homes.getOrPut(pkg) {
        runCatching {
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg), PackageManager.MATCH_DISABLED_COMPONENTS)
                .flatMap { listOfNotNull(it.activityInfo.name, it.activityInfo.targetActivity) }.toSet()
        }.getOrDefault(emptySet())
    }

    private val redirect = android.os.Handler(android.os.Looper.getMainLooper())
    // Its own handler: window changes clear `redirect`'s queue.
    private val timer = android.os.Handler(android.os.Looper.getMainLooper())
    private val stopListening = Runnable { filterKeys() }
    private val endPause = Runnable { filterKeys() }
    @Volatile private var pausedUntil = 0L

    override fun onDestroy() {
        redirect.removeCallbacksAndMessages(null)
        timer.removeCallbacksAndMessages(null)
        scope.cancel()
        if (running === this) running = null
        runCatching { unregisterReceiver(screen) }
        runCatching { unregisterReceiver(volume) }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    companion object {
        /** The service while it's on, so the fireplace timer can put the TV to sleep. */
        @Volatile var running: KeyService? = null

        /** Until when the button finder wants every button, whether or not one is mapped. */
        @Volatile private var listenUntil = 0L

        /** AudioManager's own broadcast when a stream's volume changes; not in the public API, sent on every TV. */
        private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        private val VOLUME_KEYS = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)
        /** How long an unmapped volume press stops the filtering: long enough to hold it, short enough not to miss a mapped button. */
        const val VOLUME_PAUSE_MS = 4000L

        /** Mapped buttons need the TV's buttons, except just after a volume press; the finder needs them while it listens. */
        fun needsKeys(cfg: HomeConfig, listening: Boolean, volumePaused: Boolean = false) =
            listening || (!volumePaused && cfg.buttonsOn && cfg.buttons.isNotEmpty())

        /** The service has work while any of these is on. */
        fun needsService(cfg: HomeConfig) = cfg.buttonsOn || cfg.lockOn || cfg.stockRedirect.isNotEmpty() || cfg.powerOnInput || cfg.limits.isNotEmpty()

        /**
         * Whether a window of such a launcher is its home screen. Only another of its own activities says no;
         * a class Android doesn't name, or a launcher whose home activity can't be read, keeps the old redirect.
         */
        fun sendsBack(cls: String?, homes: Set<String>, activity: Boolean): Boolean =
            cls == null || homes.isEmpty() || cls in homes || !activity

        /** The desktop app's button finder asked for the last buttons: listen for the next two minutes. */
        fun listen() {
            val first = System.currentTimeMillis() >= listenUntil
            listenUntil = System.currentTimeMillis() + 2 * 60_000L
            val s = running ?: return
            // Called on a binder thread; the service's info changes on the main one.
            if (first) s.timer.post { s.filterKeys() }
            s.timer.removeCallbacks(s.stopListening)
            s.timer.postDelayed(s.stopListening, 2 * 60_000L + 500)
        }

        /** Whether the service is running, so the on-TV editor can say when mapped buttons won't work yet. */
        val isOn: Boolean get() = running != null

        /** Screen off, as the power button would. Needs the key service on; false when it isn't. */
        fun sleep(): Boolean = running?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false

        /**
         * Some TVs take this service out of the accessibility list when they restart, so mapped buttons, the
         * app lock, the Home redirect and the power-on input stopped until the desktop app saved something
         * (#78). Home puts it back itself, with the secure-settings permission CleanMyTV granted. Without that
         * permission nothing changes.
         */
        fun ensureOn(ctx: Context, force: Boolean = false) {
            if (!force && !needsService(ConfigStore.config.value)) return
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
        Keyboard.restore(c)
        Keyboard.watch(c)
    }
}
