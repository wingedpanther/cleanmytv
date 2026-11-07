package io.github.wingedpanther.cleanmytv.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyServiceRecoveryTest {
    private val selfFull = "io.github.wingedpanther.cleanmytv.home/io.github.wingedpanther.cleanmytv.home.KeyService"
    private val selfShort = "io.github.wingedpanther.cleanmytv.home/.KeyService"
    private val otherSvc1 = "com.google.android.marvin.talkback/.TalkBackService"
    private val otherSvc2 = "com.example.buttonmapper/.ButtonService"

    // 1. Hardware gate tests
    @Test
    fun `Fire TV hardware is recognized by amazon system feature`() {
        assertTrue(KeyServiceRecovery.isFireTvDevice({ it == "amazon.hardware.fire_tv" }, "GenericModel"))
    }

    @Test
    fun `Fire TV hardware is recognized by AFT model prefix`() {
        assertTrue(KeyServiceRecovery.isFireTvDevice({ false }, "AFTHA004"))
        assertTrue(KeyServiceRecovery.isFireTvDevice({ false }, "AFTMM"))
    }

    @Test
    fun `Non-Fire-TV devices are rejected`() {
        assertFalse(KeyServiceRecovery.isFireTvDevice({ false }, "Chromecast"))
        assertFalse(KeyServiceRecovery.isFireTvDevice({ false }, "MIBOX4"))
        assertFalse(KeyServiceRecovery.isFireTvDevice({ false }, "SHIELD Android TV"))
    }

    @Test
    fun `Amazon non-TV tablet and blank models are rejected`() {
        // Amazon Kindle Fire tablets have model starting with KF, not AFT, and lack fire_tv feature
        assertFalse(KeyServiceRecovery.isFireTvDevice({ false }, "KFTRWI"))
        assertFalse(KeyServiceRecovery.isFireTvDevice({ false }, "KFKAWI"))
        assertFalse(KeyServiceRecovery.isFireTvDevice({ false }, ""))
        assertFalse(KeyServiceRecovery.isFireTvDevice({ false }, null))
    }

    // 2. Component matching tests
    @Test
    fun `isSelfComponent identifies fully qualified and shorthand components`() {
        assertTrue(KeyServiceRecovery.isSelfComponent(selfFull))
        assertTrue(KeyServiceRecovery.isSelfComponent(selfShort))
        // pkg/KeyService is not valid Android shorthand (requires leading dot)
        assertFalse(KeyServiceRecovery.isSelfComponent("io.github.wingedpanther.cleanmytv.home/KeyService"))
        assertTrue(KeyServiceRecovery.isSelfComponent("  $selfFull  "))
        assertTrue(KeyServiceRecovery.isSelfComponent("  $selfShort  "))

        assertFalse(KeyServiceRecovery.isSelfComponent(otherSvc1))
        assertFalse(KeyServiceRecovery.isSelfComponent("com.other.home/.KeyService"))
        assertFalse(KeyServiceRecovery.isSelfComponent("io.github.wingedpanther.cleanmytv.home/.OtherService"))
    }

    @Test
    fun `hasSelfService checks colon-separated lists correctly`() {
        assertFalse(KeyServiceRecovery.hasSelfService(null))
        assertFalse(KeyServiceRecovery.hasSelfService(""))
        assertFalse(KeyServiceRecovery.hasSelfService("   "))
        assertFalse(KeyServiceRecovery.hasSelfService("null"))
        assertFalse(KeyServiceRecovery.hasSelfService(otherSvc1))
        assertFalse(KeyServiceRecovery.hasSelfService("$otherSvc1:$otherSvc2"))

        assertTrue(KeyServiceRecovery.hasSelfService(selfFull))
        assertTrue(KeyServiceRecovery.hasSelfService(selfShort))
        assertTrue(KeyServiceRecovery.hasSelfService("$otherSvc1:$selfShort:$otherSvc2"))
        assertTrue(KeyServiceRecovery.hasSelfService("$otherSvc1:$selfFull"))
    }

    // 3. String building and preservation tests
    @Test
    fun `buildUpdatedServicesString handles empty, null, and literal null safely`() {
        assertEquals(selfFull, KeyServiceRecovery.buildUpdatedServicesString(null))
        assertEquals(selfFull, KeyServiceRecovery.buildUpdatedServicesString(""))
        assertEquals(selfFull, KeyServiceRecovery.buildUpdatedServicesString("   "))
        assertEquals(selfFull, KeyServiceRecovery.buildUpdatedServicesString("null"))
    }

    @Test
    fun `buildUpdatedServicesString preserves unrelated services`() {
        val input = "$otherSvc1:$otherSvc2"
        val expected = "$otherSvc1:$otherSvc2:$selfFull"
        assertEquals(expected, KeyServiceRecovery.buildUpdatedServicesString(input))
    }

    @Test
    fun `buildUpdatedServicesString cleans up literal null and empty tokens while preserving others`() {
        val input = "null:$otherSvc1::null:$otherSvc2:"
        val expected = "$otherSvc1:$otherSvc2:$selfFull"
        assertEquals(expected, KeyServiceRecovery.buildUpdatedServicesString(input))
    }

    @Test
    fun `buildUpdatedServicesString does not duplicate shorthand or fully qualified component`() {
        // If shorthand already present, preserve it without duplicating
        val shortInput = "$otherSvc1:$selfShort:$otherSvc2"
        assertEquals(shortInput, KeyServiceRecovery.buildUpdatedServicesString(shortInput))

        // If fully qualified already present, preserve it without duplicating
        val fullInput = "$otherSvc1:$selfFull"
        assertEquals(fullInput, KeyServiceRecovery.buildUpdatedServicesString(fullInput))
    }

    @Test
    fun `invalid dotless component is preserved and triggers valid self append`() {
        // Invalid dotless registration is preserved as unrelated token, and valid selfFull is appended
        val dotlessInput = "io.github.wingedpanther.cleanmytv.home/KeyService"
        val expected = "$dotlessInput:$selfFull"
        assertEquals(expected, KeyServiceRecovery.buildUpdatedServicesString(dotlessInput))
        assertFalse(KeyServiceRecovery.hasSelfService(dotlessInput))
    }

    // 4. Prompt decision tests
    @Test
    fun `shouldPrompt returns false on non-Fire-TV devices`() {
        assertFalse(
            KeyServiceRecovery.shouldPrompt(
                isFireTv = false,
                buttonsOn = true,
                isServiceRunning = false,
                enabledServices = null,
                accessibilityEnabled = 0
            )
        )
    }

    @Test
    fun `shouldPrompt returns false when buttonsOn is false regardless of lockOn`() {
        assertFalse(
            KeyServiceRecovery.shouldPrompt(
                isFireTv = true,
                buttonsOn = false,
                isServiceRunning = false,
                enabledServices = null,
                accessibilityEnabled = 0
            )
        )
    }

    @Test
    fun `shouldPrompt returns false when service is already running`() {
        assertFalse(
            KeyServiceRecovery.shouldPrompt(
                isFireTv = true,
                buttonsOn = true,
                isServiceRunning = true,
                enabledServices = null,
                accessibilityEnabled = 0
            )
        )
    }

    @Test
    fun `shouldPrompt returns false when already healthy in settings`() {
        // Shorthand present and global accessibility on
        assertFalse(
            KeyServiceRecovery.shouldPrompt(
                isFireTv = true,
                buttonsOn = true,
                isServiceRunning = false,
                enabledServices = selfShort,
                accessibilityEnabled = 1
            )
        )

        // Fully qualified present and global accessibility on
        assertFalse(
            KeyServiceRecovery.shouldPrompt(
                isFireTv = true,
                buttonsOn = true,
                isServiceRunning = false,
                enabledServices = "$otherSvc1:$selfFull",
                accessibilityEnabled = 1
            )
        )
    }

    @Test
    fun `shouldPrompt returns true when self service is missing from settings`() {
        assertTrue(
            KeyServiceRecovery.shouldPrompt(
                isFireTv = true,
                buttonsOn = true,
                isServiceRunning = false,
                enabledServices = otherSvc1,
                accessibilityEnabled = 1
            )
        )
        assertTrue(
            KeyServiceRecovery.shouldPrompt(
                isFireTv = true,
                buttonsOn = true,
                isServiceRunning = false,
                enabledServices = null,
                accessibilityEnabled = 1
            )
        )
    }

    @Test
    fun `shouldPrompt returns true when global accessibility is off`() {
        assertTrue(
            KeyServiceRecovery.shouldPrompt(
                isFireTv = true,
                buttonsOn = true,
                isServiceRunning = false,
                enabledServices = selfShort,
                accessibilityEnabled = 0
            )
        )
    }

    // 5. Recovery execution and orchestration tests
    @Test
    fun `performRecovery is no-op on non-Fire-TV devices`() {
        var writtenServices: String? = null
        var writtenGlobal: Int? = null

        val result = KeyServiceRecovery.performRecovery(
            isFireTv = false,
            buttonsOn = true,
            isServiceRunning = false,
            getCurrentServices = { null },
            getCurrentAccessibilityEnabled = { 0 },
            canWriteSecure = { true },
            putServices = { writtenServices = it; true },
            putAccessibilityEnabled = { writtenGlobal = it; true }
        )

        assertEquals(KeyServiceRecovery.RecoveryResult.NO_OP, result)
        assertEquals(null, writtenServices)
        assertEquals(null, writtenGlobal)
    }

    @Test
    fun `performRecovery is no-op when remote buttons are off`() {
        var writtenServices: String? = null
        var writtenGlobal: Int? = null

        val result = KeyServiceRecovery.performRecovery(
            isFireTv = true,
            buttonsOn = false,
            isServiceRunning = false,
            getCurrentServices = { null },
            getCurrentAccessibilityEnabled = { 0 },
            canWriteSecure = { true },
            putServices = { writtenServices = it; true },
            putAccessibilityEnabled = { writtenGlobal = it; true }
        )

        assertEquals(KeyServiceRecovery.RecoveryResult.NO_OP, result)
        assertEquals(null, writtenServices)
        assertEquals(null, writtenGlobal)
    }

    @Test
    fun `performRecovery is no-op when service is already running`() {
        var writes = 0
        val result = KeyServiceRecovery.performRecovery(
            isFireTv = true,
            buttonsOn = true,
            isServiceRunning = true,
            getCurrentServices = { null },
            getCurrentAccessibilityEnabled = { 0 },
            canWriteSecure = { true },
            putServices = { writes++; true },
            putAccessibilityEnabled = { writes++; true }
        )

        assertEquals(KeyServiceRecovery.RecoveryResult.NO_OP, result)
        assertEquals(0, writes)
    }

    @Test
    fun `performRecovery handles stale snapshot where buttonsOn turned off while dialog open`() {
        var writes = 0
        // User saw prompt when buttons were on, but in settings buttons got turned off before clicking Restore
        val result = KeyServiceRecovery.performRecovery(
            isFireTv = true,
            buttonsOn = false, // re-read value
            isServiceRunning = false,
            getCurrentServices = { null },
            getCurrentAccessibilityEnabled = { 0 },
            canWriteSecure = { true },
            putServices = { writes++; true },
            putAccessibilityEnabled = { writes++; true }
        )

        assertEquals(KeyServiceRecovery.RecoveryResult.NO_OP, result)
        assertEquals(0, writes)
    }

    @Test
    fun `performRecovery handles stale snapshot where service became healthy before click`() {
        var writes = 0
        val result = KeyServiceRecovery.performRecovery(
            isFireTv = true,
            buttonsOn = true,
            isServiceRunning = false,
            getCurrentServices = { "$otherSvc1:$selfShort" }, // re-read shows service is now present
            getCurrentAccessibilityEnabled = { 1 }, // re-read shows global is now 1
            canWriteSecure = { true },
            putServices = { writes++; true },
            putAccessibilityEnabled = { writes++; true }
        )

        assertEquals(KeyServiceRecovery.RecoveryResult.NO_OP, result)
        assertEquals(0, writes)
    }

    @Test
    fun `performRecovery preserves unrelated services added while dialog was open`() {
        var writtenServices: String? = null
        var writtenGlobal: Int? = null

        // While dialog was open, an unrelated service was registered in settings
        val latestServices = "$otherSvc1:$otherSvc2"

        val result = KeyServiceRecovery.performRecovery(
            isFireTv = true,
            buttonsOn = true,
            isServiceRunning = false,
            getCurrentServices = { latestServices },
            getCurrentAccessibilityEnabled = { 0 },
            canWriteSecure = { true },
            putServices = { writtenServices = it; true },
            putAccessibilityEnabled = { writtenGlobal = it; true }
        )

        assertEquals(KeyServiceRecovery.RecoveryResult.SUCCESS, result)
        assertEquals("$otherSvc1:$otherSvc2:$selfFull", writtenServices)
        assertEquals(1, writtenGlobal)
    }

    @Test
    fun `performRecovery returns PERMISSION_DENIED without writes when permission missing`() {
        var writes = 0
        val result = KeyServiceRecovery.performRecovery(
            isFireTv = true,
            buttonsOn = true,
            isServiceRunning = false,
            getCurrentServices = { null },
            getCurrentAccessibilityEnabled = { 0 },
            canWriteSecure = { false }, // permission denied
            putServices = { writes++; true },
            putAccessibilityEnabled = { writes++; true }
        )

        assertEquals(KeyServiceRecovery.RecoveryResult.PERMISSION_DENIED, result)
        assertEquals(0, writes)
    }

    @Test
    fun `performRecovery returns WRITE_FAILED and does zero writes when services getter throws`() {
        var writes = 0
        val result = KeyServiceRecovery.performRecovery(
            isFireTv = true,
            buttonsOn = true,
            isServiceRunning = false,
            getCurrentServices = { throw SecurityException("cannot read secure table") },
            getCurrentAccessibilityEnabled = { 0 },
            canWriteSecure = { true },
            putServices = { writes++; true },
            putAccessibilityEnabled = { writes++; true }
        )

        assertEquals(KeyServiceRecovery.RecoveryResult.WRITE_FAILED, result)
        assertEquals(0, writes)
    }

    @Test
    fun `performRecovery returns WRITE_FAILED and does zero writes when global getter throws`() {
        var writes = 0
        val result = KeyServiceRecovery.performRecovery(
            isFireTv = true,
            buttonsOn = true,
            isServiceRunning = false,
            getCurrentServices = { null },
            getCurrentAccessibilityEnabled = { throw RuntimeException("dead object") },
            canWriteSecure = { true },
            putServices = { writes++; true },
            putAccessibilityEnabled = { writes++; true }
        )

        assertEquals(KeyServiceRecovery.RecoveryResult.WRITE_FAILED, result)
        assertEquals(0, writes)
    }

    @Test
    fun `performRecovery returns PERMISSION_DENIED and does zero writes when canWriteSecure throws`() {
        var writes = 0
        val result = KeyServiceRecovery.performRecovery(
            isFireTv = true,
            buttonsOn = true,
            isServiceRunning = false,
            getCurrentServices = { null },
            getCurrentAccessibilityEnabled = { 0 },
            canWriteSecure = { throw SecurityException("package manager dead") },
            putServices = { writes++; true },
            putAccessibilityEnabled = { writes++; true }
        )

        assertEquals(KeyServiceRecovery.RecoveryResult.PERMISSION_DENIED, result)
        assertEquals(0, writes)
    }

    @Test
    fun `performRecovery returns WRITE_FAILED without crashing when write returns false`() {
        val result = KeyServiceRecovery.performRecovery(
            isFireTv = true,
            buttonsOn = true,
            isServiceRunning = false,
            getCurrentServices = { null },
            getCurrentAccessibilityEnabled = { 0 },
            canWriteSecure = { true },
            putServices = { false }, // write failed
            putAccessibilityEnabled = { true }
        )

        assertEquals(KeyServiceRecovery.RecoveryResult.WRITE_FAILED, result)
    }

    @Test
    fun `performRecovery returns WRITE_FAILED without crashing when write throws exception`() {
        val result = KeyServiceRecovery.performRecovery(
            isFireTv = true,
            buttonsOn = true,
            isServiceRunning = false,
            getCurrentServices = { null },
            getCurrentAccessibilityEnabled = { 0 },
            canWriteSecure = { true },
            putServices = { throw SecurityException("permission revoked") },
            putAccessibilityEnabled = { true }
        )

        assertEquals(KeyServiceRecovery.RecoveryResult.WRITE_FAILED, result)
    }

    @Test
    fun `performRecovery returns WRITE_FAILED when global switch write throws exception`() {
        var servicesWritten: String? = null
        val result = KeyServiceRecovery.performRecovery(
            isFireTv = true,
            buttonsOn = true,
            isServiceRunning = false,
            getCurrentServices = { null },
            getCurrentAccessibilityEnabled = { 0 },
            canWriteSecure = { true },
            putServices = { servicesWritten = it; true },
            putAccessibilityEnabled = { throw RuntimeException("settings table locked") }
        )

        assertEquals(KeyServiceRecovery.RecoveryResult.WRITE_FAILED, result)
        assertEquals(selfFull, servicesWritten)
    }

    @Test
    fun `performRecovery succeeds and only writes what is needed`() {
        // Case 1: self missing, but global accessibility is already 1
        var writtenServices1: String? = null
        var globalWritten1 = false
        val result1 = KeyServiceRecovery.performRecovery(
            isFireTv = true,
            buttonsOn = true,
            isServiceRunning = false,
            getCurrentServices = { otherSvc1 },
            getCurrentAccessibilityEnabled = { 1 },
            canWriteSecure = { true },
            putServices = { writtenServices1 = it; true },
            putAccessibilityEnabled = { globalWritten1 = true; true }
        )
        assertEquals(KeyServiceRecovery.RecoveryResult.SUCCESS, result1)
        assertEquals("$otherSvc1:$selfFull", writtenServices1)
        assertFalse(globalWritten1)

        // Case 2: self already present in shorthand, but global accessibility is 0
        var servicesWritten2 = false
        var writtenGlobal2: Int? = null
        val result2 = KeyServiceRecovery.performRecovery(
            isFireTv = true,
            buttonsOn = true,
            isServiceRunning = false,
            getCurrentServices = { "$otherSvc1:$selfShort" },
            getCurrentAccessibilityEnabled = { 0 },
            canWriteSecure = { true },
            putServices = { servicesWritten2 = true; true },
            putAccessibilityEnabled = { writtenGlobal2 = it; true }
        )
        assertEquals(KeyServiceRecovery.RecoveryResult.SUCCESS, result2)
        assertFalse(servicesWritten2)
        assertEquals(1, writtenGlobal2)
    }

    @Test
    fun `performRecovery is idempotent on repeated calls`() {
        var currentServices = otherSvc1
        var currentGlobal = 0
        var serviceWrites = 0
        var globalWrites = 0

        val callRecovery = {
            KeyServiceRecovery.performRecovery(
                isFireTv = true,
                buttonsOn = true,
                isServiceRunning = false,
                getCurrentServices = { currentServices },
                getCurrentAccessibilityEnabled = { currentGlobal },
                canWriteSecure = { true },
                putServices = {
                    serviceWrites++
                    currentServices = it
                    true
                },
                putAccessibilityEnabled = {
                    globalWrites++
                    currentGlobal = it
                    true
                }
            )
        }

        // First call recovers
        val firstResult = callRecovery()
        assertEquals(KeyServiceRecovery.RecoveryResult.SUCCESS, firstResult)
        assertEquals(1, serviceWrites)
        assertEquals(1, globalWrites)
        assertEquals("$otherSvc1:$selfFull", currentServices)
        assertEquals(1, currentGlobal)

        // Second call is an idempotent no-op with zero writes
        val secondResult = callRecovery()
        assertEquals(KeyServiceRecovery.RecoveryResult.NO_OP, secondResult)
        assertEquals(1, serviceWrites)
        assertEquals(1, globalWrites)
    }
}
