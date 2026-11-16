package io.github.wingedpanther.cleanmytv.home

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A newer CleanMyTV Home on the project's release page. */
data class HomeRelease(val version: String, val tag: String, val apk: String, val sums: String, val notes: String)

/**
 * Home updates itself from the project's GitHub releases, which carry CleanMyTV-Home-<version>.apk since 0.9.0.
 * Once a day it asks for the latest release; a newer Home shows an icon in the top bar. Updating downloads the
 * APK, checks it against the release's SHA256SUMS.txt, as the desktop app does, and hands it to Android's
 * installer, which asks the owner to confirm. It's signed with the same key, so every setting stays.
 */
object Updates {
    private const val LATEST = "https://api.github.com/repos/wingedpanther/cleanmytv/releases/latest"
    private val ASSET = Regex("^CleanMyTV-Home-(\\d+\\.\\d+\\.\\d+)\\.apk$")
    // GitHub's API and the hosts its release downloads redirect to; nothing else is followed.
    private val HOSTS = setOf("api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")
    private const val MAX_APK = 40 * 1024 * 1024
    private const val DAY_MS = 24 * 3600 * 1000L

    private val _available = MutableStateFlow<HomeRelease?>(null)
    /** A newer Home than this one, not skipped, or null. */
    val available: StateFlow<HomeRelease?> = _available
    private val _progress = MutableStateFlow("")
    /** What the update is doing, for its dialog. */
    val progress: StateFlow<String> = _progress

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE)

    fun installed(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    /** "1.10.0-test.2" -> [1, 10, 0]: a test build counts as the version it leads to. */
    fun parts(v: String): List<Int> = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    fun newer(candidate: String, current: String): Boolean {
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Release notes for a TV screen: up to the downloads, no tables, links or Markdown marks, bullets kept. */
    fun plain(md: String): String = md.lines()
        // Everything from the downloads table on is for the desktop app's page, not the TV.
        .takeWhile { !Regex("^\\**Downloads\\**\\s*$").matches(it.trim()) }
        .filterNot { it.trimStart().startsWith("|") || it.startsWith("**Full Changelog") }
        .joinToString("\n") { l ->
            l.replace(Regex("\\*\\*|`"), "").replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1").replace(Regex("^\\s*- "), "• ")
        }
        .replace(Regex("\n{3,}"), "\n\n").trim()

    /** Once a day, while "Look for updates" is on. [force] asks now, for the editor's button. */
    suspend fun check(ctx: Context, force: Boolean = false): HomeRelease? = withContext(Dispatchers.IO) {
        if (!force && !ConfigStore.config.value.updateCheck) return@withContext _available.value
        val p = prefs(ctx)
        if (!force && System.currentTimeMillis() - p.getLong("checked", 0) < DAY_MS) return@withContext _available.value
        runCatching {
            val r = JSONObject(String(get(LATEST, 512 * 1024), Charsets.UTF_8))
            p.edit().putLong("checked", System.currentTimeMillis()).apply()
            val assets = r.optJSONArray("assets") ?: return@runCatching null
            var apk: Pair<String, String>? = null
            var sums: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                val url = a.optString("browser_download_url")
                ASSET.matchEntire(name)?.let { apk = it.groupValues[1] to url }
                if (name == "SHA256SUMS.txt") sums = url
            }
            val (version, url) = apk ?: return@runCatching null
            if (sums == null || !newer(version, installed(ctx)) || p.getString("skipped", null) == version) return@runCatching null
            HomeRelease(version, r.optString("tag_name"), url, sums!!, plain(r.optString("body")))
        }.onFailure { Log.w(TAG, "update check: $it") }.getOrNull().also { _available.value = it }
    }

    fun skip(ctx: Context, r: HomeRelease) { prefs(ctx).edit().putString("skipped", r.version).apply(); _available.value = null }

    /** Whether this release's icon has been announced already, so the TV says it once. */
    fun announce(ctx: Context, r: HomeRelease): Boolean {
        val p = prefs(ctx)
        if (p.getString("announced", null) == r.version) return false
        p.edit().putString("announced", r.version).apply()
        return true
    }

    /** Downloads, checks and hands the APK to Android's installer. Null when the installer took it, or why not. */
    suspend fun install(ctx: Context, r: HomeRelease): String? = withContext(Dispatchers.IO) {
        runCatching {
            val name = "CleanMyTV-Home-${r.version}.apk"
            _progress.value = "Checking the release…"
            val sums = String(get(r.sums, 64 * 1024), Charsets.UTF_8)
            val want = sums.lines().firstNotNullOfOrNull { l -> Regex("^([0-9a-f]{64})\\s+\\*?(\\S+)$").matchEntire(l.trim())?.takeIf { it.groupValues[2] == name }?.groupValues?.get(1) }
                ?: error("the release has no checksum for $name")
            _progress.value = "Downloading ${r.version}…"
            val bytes = get(r.apk, MAX_APK)
            val got = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            check(got == want) { "the download is damaged; try again" }
            _progress.value = "Installing…"
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(ctx.packageName) }
            val id = installer.createSession(params)
            installer.openSession(id).use { s ->
                s.openWrite("home.apk", 0, bytes.size.toLong()).use { it.write(bytes); s.fsync(it) }
                val status = PendingIntent.getBroadcast(
                    ctx, id, Intent(ctx, UpdateReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                )
                s.commit(status.intentSender)
            }
            null
        }.getOrElse { e -> "That didn't work: ${e.message}" }.also { _progress.value = it ?: "Confirm on the screen that opens. Home starts again on its own." }
    }

    internal fun said(text: String) { _progress.value = text }

    /** Android lets an app install updates only once the owner allows it, a switch per app in Settings. */
    fun allowed(ctx: Context): Boolean = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Opens that switch for Home, or the list it sits in where a TV doesn't open it straight away. */
    fun askToAllow(ctx: Context) {
        val one = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))
        val all = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        val target = listOf(one, all).firstOrNull { it.resolveActivity(ctx.packageManager) != null }
        if (target != null) runCatching { ctx.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { Log.w(TAG, "allow installs: $it") }
        else _progress.value = "This TV hides the setting. Find Install unknown apps under Settings → Apps → Special app access and allow CleanMyTV Home."
    }

    private fun get(url: String, max: Int): ByteArray {
        var u = URL(url)
        repeat(5) {
            require(u.protocol == "https" && u.host in HOSTS) { "the download went somewhere unexpected (${u.host})" }
            val c = u.openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 60_000
                c.setRequestProperty("User-Agent", "CleanMyTV-Home github.com/wingedpanther/cleanmytv")
                c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
                val code = c.responseCode
                if (code in 300..399) { u = URL(u, c.getHeaderField("Location")); return@repeat }
                require(code == 200) { "GitHub answered $code" }
                val out = java.io.ByteArrayOutputStream()
                c.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n)
                        require(out.size() <= max) { "the file is bigger than expected" }
                    }
                }
                return out.toByteArray()
            } finally { c.disconnect() }
        }
        error("too many redirects")
    }
}

/** Android's installer reports back: it needs the owner's OK, or it's done, or it failed. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                runCatching { c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { Log.w(TAG, "update confirm: $it") }
            }
            PackageInstaller.STATUS_SUCCESS -> Updates.said("Updated.")
            else -> {
                val why = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Updates.said(
                    if ("DOWNGRADE" in why) "The TV has a newer test build than this release, so Android won't go back to it."
                    else if (i.getIntExtra(PackageInstaller.EXTRA_STATUS, 0) == PackageInstaller.STATUS_FAILURE_ABORTED) "Not installed. Press Update to try again."
                    else "The TV didn't install it: ${why.ifEmpty { "no reason given" }}"
                )
            }
        }
    }
}
