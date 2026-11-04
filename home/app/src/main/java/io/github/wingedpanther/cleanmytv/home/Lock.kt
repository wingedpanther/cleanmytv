package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App lock. The PIN is kept salted and hashed in Home's private storage; CleanMyTV can set or clear it over
 * ADB but never read it back. Unlocks live in memory only, so a restart locks everything again.
 */
object Lock {
    /** Locked with "Also lock Settings": the places to switch the lock off or uninstall around it. */
    val SETTINGS = setOf(
        "com.android.tv.settings", "com.google.android.tv.settings", "com.android.settings",
        "com.android.vending", "com.android.packageinstaller", "com.google.android.packageinstaller",
    )
    /** Not an app: the Quick settings panel inside Home, which can switch debugging off. */
    const val PANEL = "panel"
    private const val ROUNDS = 20_000

    private val unlocked = HashMap<String, Long>() // package -> elapsed time it locks again; MAX_VALUE until sleep
    /** With "every time": the app just unlocked, until something else comes to the front. */
    private var untilLeft: String? = null
    private var fails = 0
    private var waitUntil = 0L

    private fun file(ctx: Context) = File(ctx.filesDir, "lock.json")

    fun hasPin(ctx: Context): Boolean = file(ctx).exists()

    fun setPin(ctx: Context, pin: String) {
        require(pin.length in 4..6 && pin.all { it in '0'..'9' }) { "the PIN must be 4 to 6 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val tmp = File(ctx.filesDir, "lock.json.tmp")
        tmp.writeText(JSONObject().put("salt", hex(salt)).put("hash", hex(hash(pin, salt))).toString())
        tmp.renameTo(file(ctx))
        lockAll()
    }

    fun clearPin(ctx: Context) { file(ctx).delete(); lockAll() }

    /** Seconds to wait before the next try, after five wrong PINs in a row; 0 when a try is allowed. */
    @Synchronized
    fun waitSeconds(): Int = ((waitUntil - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)

    /** True and unlocks [what] when the PIN is right. Every fifth wrong one in a row adds 30 seconds more. */
    @Synchronized
    fun check(ctx: Context, pin: String, what: String): Boolean {
        if (waitSeconds() > 0) return false
        val saved = runCatching { JSONObject(file(ctx).readText()) }.getOrNull() ?: return false
        val ok = MessageDigest.isEqual(hash(pin, unhex(saved.getString("salt"))), unhex(saved.getString("hash")))
        if (ok) {
            fails = 0
            val cfg = ConfigStore.config.value
            unlocked[what] = if (cfg.lockFor == "15") SystemClock.elapsedRealtime() + 15 * 60_000L else Long.MAX_VALUE
            untilLeft = if (cfg.lockFor == "always") what else null
        } else if (++fails % 5 == 0) {
            waitUntil = SystemClock.elapsedRealtime() + 30_000L * (fails / 5)
        }
        return ok
    }

    /** Whether opening [what] asks for the PIN right now. */
    @Synchronized
    fun needsPin(ctx: Context, what: String): Boolean {
        val cfg = ConfigStore.config.value
        if (!cfg.lockOn || !hasPin(ctx)) return false
        val locked = what in cfg.lockApps || (cfg.lockSystem && (what in SETTINGS || what == PANEL))
        if (!locked) return false
        val until = unlocked[what] ?: return true
        if (SystemClock.elapsedRealtime() < until) return false
        unlocked.remove(what)
        return true
    }

    /**
     * Another app came to the front. An "every time" unlock ends here; the system's own windows (the volume
     * bar, a keyboard) don't count as leaving.
     */
    @Synchronized
    fun seen(pkg: String, lockScreen: Boolean) {
        val u = untilLeft ?: return
        if (pkg == u || lockScreen || pkg == "com.android.systemui" || pkg == "android" || pkg.contains("inputmethod")) return
        unlocked.remove(u)
        untilLeft = null
    }

    /** Back on the PIN pad: the app it covered shows for a moment on the way Home; don't cover it again. */
    private var backedOut: Pair<String, Long>? = null

    @Synchronized
    fun backOut(pkg: String) { backedOut = pkg to SystemClock.elapsedRealtime() + 2_000L }

    @Synchronized
    fun justBackedOut(pkg: String): Boolean = backedOut?.let { (p, until) -> p == pkg && SystemClock.elapsedRealtime() < until } == true

    /** Whether [pkg] shows a lock on its tile. */
    fun isLocked(cfg: HomeConfig, pkg: String): Boolean = cfg.lockOn && pkg in cfg.lockApps

    /** The TV went to sleep, or the PIN or the list changed: everything asks again. */
    @Synchronized
    fun lockAll() { unlocked.clear(); untilLeft = null }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, ROUNDS, 256)).encoded

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
