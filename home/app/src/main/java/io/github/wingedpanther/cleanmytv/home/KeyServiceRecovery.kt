package io.github.wingedpanther.cleanmytv.home

/**
 * Recovers KeyService accessibility settings after a force-stop on Amazon Fire TV.
 *
 * On Amazon Fire TV firmwares, when an app is force-stopped in Settings, Fire OS has been
 * observed to disable its accessibility service, stopping remote button mappings and the Home
 * redirect. When the user reopens Home, this helper detects the missing service or disabled
 * global switch and restores them once confirmed by the user.
 */
object KeyServiceRecovery {
    const val DEFAULT_PKG = "io.github.wingedpanther.cleanmytv.home"
    const val DEFAULT_CLS = "io.github.wingedpanther.cleanmytv.home.KeyService"

    enum class RecoveryResult {
        NO_OP,
        SUCCESS,
        PERMISSION_DENIED,
        WRITE_FAILED
    }

    /**
     * Determines whether hardware is Amazon Fire TV.
     * Uses the official system feature "amazon.hardware.fire_tv" or model prefix "AFT".
     */
    fun isFireTvDevice(
        hasSystemFeature: (String) -> Boolean,
        model: String?
    ): Boolean {
        return hasSystemFeature("amazon.hardware.fire_tv") ||
            (model != null && model.startsWith("AFT"))
    }

    /**
     * Checks if a component string matches self (either fully qualified or shorthand).
     */
    fun isSelfComponent(
        component: String,
        pkg: String = DEFAULT_PKG,
        cls: String = DEFAULT_CLS
    ): Boolean {
        val trimmed = component.trim()
        val simpleName = cls.substringAfterLast('.')
        val full = "$pkg/$cls"
        val shortWithDot = "$pkg/.$simpleName"
        return trimmed == full || trimmed == shortWithDot
    }

    /**
     * Checks if the enabled services string contains self.
     */
    fun hasSelfService(
        enabledServices: String?,
        pkg: String = DEFAULT_PKG,
        cls: String = DEFAULT_CLS
    ): Boolean {
        if (enabledServices.isNullOrBlank() || enabledServices.trim() == "null") return false
        return enabledServices.split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "null" }
            .any { isSelfComponent(it, pkg, cls) }
    }

    /**
     * Builds an updated enabled_accessibility_services string containing self,
     * preserving all unrelated entries, handling null/empty/literal null,
     * and avoiding duplicate self components.
     */
    fun buildUpdatedServicesString(
        currentServices: String?,
        pkg: String = DEFAULT_PKG,
        cls: String = DEFAULT_CLS
    ): String {
        val selfComponent = "$pkg/$cls"
        if (currentServices.isNullOrBlank() || currentServices.trim() == "null") {
            return selfComponent
        }
        val tokens = currentServices.split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "null" }

        val alreadyPresent = tokens.any { isSelfComponent(it, pkg, cls) }
        return if (alreadyPresent) {
            tokens.joinToString(":")
        } else {
            (tokens + selfComponent).joinToString(":")
        }
    }

    /**
     * Decides whether the user should be prompted to restore remote buttons.
     * Strictly gated to Amazon Fire TV hardware and cfg.buttonsOn = true.
     * Never prompts for lockOn alone or on non-Fire-TV hardware.
     */
    fun shouldPrompt(
        isFireTv: Boolean,
        buttonsOn: Boolean,
        isServiceRunning: Boolean,
        enabledServices: String?,
        accessibilityEnabled: Int,
        pkg: String = DEFAULT_PKG,
        cls: String = DEFAULT_CLS
    ): Boolean {
        if (!isFireTv) return false
        if (!buttonsOn) return false
        if (isServiceRunning) return false

        val selfPresent = hasSelfService(enabledServices, pkg, cls)
        val globalOn = (accessibilityEnabled == 1)

        return !selfPresent || !globalOn
    }

    /**
     * Performs recovery only after explicit user confirmation.
     * Re-reads current state to handle stale prompt snapshots (e.g. buttonsOn turned off,
     * unrelated services changed, or service became healthy while dialog was open).
     */
    fun performRecovery(
        isFireTv: Boolean,
        buttonsOn: Boolean,
        isServiceRunning: Boolean,
        getCurrentServices: () -> String?,
        getCurrentAccessibilityEnabled: () -> Int,
        canWriteSecure: () -> Boolean,
        putServices: (String) -> Boolean,
        putAccessibilityEnabled: (Int) -> Boolean,
        pkg: String = DEFAULT_PKG,
        cls: String = DEFAULT_CLS
    ): RecoveryResult {
        if (!isFireTv) return RecoveryResult.NO_OP
        if (!buttonsOn) return RecoveryResult.NO_OP
        if (isServiceRunning) return RecoveryResult.NO_OP

        val currentServices = runCatching { getCurrentServices() }.getOrElse { return RecoveryResult.WRITE_FAILED }
        val currentAccessibilityEnabled = runCatching { getCurrentAccessibilityEnabled() }.getOrElse { return RecoveryResult.WRITE_FAILED }

        val selfPresent = hasSelfService(currentServices, pkg, cls)
        val globalOn = (currentAccessibilityEnabled == 1)

        if (selfPresent && globalOn) {
            return RecoveryResult.NO_OP
        }

        val canWrite = runCatching { canWriteSecure() }.getOrElse { return RecoveryResult.PERMISSION_DENIED }
        if (!canWrite) {
            return RecoveryResult.PERMISSION_DENIED
        }

        if (!selfPresent) {
            val updated = buildUpdatedServicesString(currentServices, pkg, cls)
            val ok = runCatching { putServices(updated) }.getOrDefault(false)
            if (!ok) return RecoveryResult.WRITE_FAILED
        }

        if (!globalOn) {
            val ok = runCatching { putAccessibilityEnabled(1) }.getOrDefault(false)
            if (!ok) return RecoveryResult.WRITE_FAILED
        }

        return RecoveryResult.SUCCESS
    }
}
