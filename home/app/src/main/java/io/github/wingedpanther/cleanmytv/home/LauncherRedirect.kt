package io.github.wingedpanther.cleanmytv.home

/** Fire TV can display its Settings grid inside HomeActivity_vNext, with the same window title. */
internal object LauncherRedirect {
    const val AMAZON = "com.amazon.tv.launcher"
    val SETTINGS_IDS = listOf("settings_card_view", "settings_nc_card_view").map { "$AMAZON:id/$it" }

    // Settings can open or close between retries. Check the current window when each callback runs.
    fun scheduleRetries(postDelayed: (Runnable, Long) -> Unit, mayRedirect: () -> Boolean, bringHome: () -> Unit) {
        for (delay in longArrayOf(300, 1200, 5300)) {
            postDelayed(Runnable { if (mayRedirect()) bringHome() }, delay)
        }
    }

    // A missing or different active window is not evidence that Amazon Home is still in front.
    fun mayReturn(target: String, activePackage: String?, settingsVisible: Boolean): Boolean =
        activePackage == target && !(target == AMAZON && settingsVisible)
}
