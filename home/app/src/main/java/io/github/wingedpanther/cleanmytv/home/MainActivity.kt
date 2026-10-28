package io.github.wingedpanther.cleanmytv.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

const val TAG = "CMTVHome"

data class TvApp(val pkg: String, val label: String, val art: ImageBitmap, val banner: Boolean, val launch: Intent)

class MainActivity : ComponentActivity() {
    private val visualiser = Visualiser()
    /** Bumped when apps are installed or removed, or Home comes back into view, so the list reloads. */
    private val appsVersion = mutableIntStateOf(0)
    /** Something a remote button asked Home to open; HomeScreen clears it. */
    private val open = androidx.compose.runtime.mutableStateOf<String?>(null)

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { appsVersion.intValue++ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val created = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        ConfigStore.load(this)
        open.value = intent.getStringExtra(OPEN)
        setContent { HomeScreen(visualiser, appsVersion.intValue, open) }
        window.decorView.post { Log.i(TAG, "first frame ${SystemClock.uptimeMillis() - created} ms after onCreate") }
        registerReceiver(packages, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    override fun onDestroy() {
        unregisterReceiver(packages)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        if (ConfigStore.config.value.visualiser) visualiser.start(this)
    }

    override fun onResume() {
        super.onResume()
        appsVersion.intValue++
    }

    override fun onStop() {
        visualiser.stop()
        super.onStop()
    }

    // Buttons Home sees go to the button finder too, for TVs where the key service is off. Nothing is swallowed.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) KeyLog.add(event.keyCode)
        return super.dispatchKeyEvent(event)
    }

    // A mapped button can ask Home to open something, such as the Inputs picker.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(OPEN)?.let { open.value = it }
    }

    companion object { const val OPEN = "open" }
}

/** TV apps (with their banners), then phone apps that only have an icon. */
fun loadApps(pm: PackageManager, self: String): List<TvApp> {
    val seen = HashSet<String>()
    val out = ArrayList<TvApp>()
    for ((category, isTv) in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER to true, Intent.CATEGORY_LAUNCHER to false)) {
        for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
            val ai = ri.activityInfo
            if (ai.packageName == self || !seen.add(ai.packageName)) continue
            val banner: Drawable? = if (isTv) ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) else null
            val art = runCatching {
                (banner ?: ai.loadIcon(pm)).toBitmap(if (banner != null) 320 else 128, if (banner != null) 180 else 128)
            }.getOrNull() ?: continue
            val launch = Intent(Intent.ACTION_MAIN).addCategory(category)
                .setClassName(ai.packageName, ai.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            out += TvApp(ai.packageName, ri.loadLabel(pm).toString(), art.asImageBitmap(), banner != null, launch)
        }
    }
    return out
}

/** The same apps as loadApps, names only, for the desktop app's editor. */
fun listApps(pm: PackageManager, self: String): org.json.JSONArray {
    val seen = HashSet<String>()
    val out = org.json.JSONArray()
    for ((category, isTv) in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER to true, Intent.CATEGORY_LAUNCHER to false)) {
        for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == self || !seen.add(pkg)) continue
            out.put(org.json.JSONObject().put("pkg", pkg).put("label", ri.loadLabel(pm).toString()).put("tv", isTv))
        }
    }
    return out
}

/** The order from CleanMyTV first, then everything else alphabetically; hidden apps left out. */
fun arrange(apps: List<TvApp>, cfg: HomeConfig): List<TvApp> {
    val byPkg = apps.associateBy { it.pkg }
    val first = cfg.order.mapNotNull { byPkg[it] }
    val rest = apps.filter { it.pkg !in cfg.order }.sortedBy { it.label.lowercase() }
    return (first + rest).filter { it.pkg !in cfg.hidden }
}
