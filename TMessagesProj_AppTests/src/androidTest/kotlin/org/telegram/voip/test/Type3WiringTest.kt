package org.telegram.voip.test

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.Type3ShimController
import org.telegram.messenger.voip.Instance

/**
 * ATDD red-phase scaffold — Story 9.5 (Android calls over Type3).
 *
 * Instrumented (androidTest) layer. Two kinds of test live here:
 *
 *  - GREEN ANCHOR  — `isType3Secret_*` characterise the EXISTING gating predicate
 *    (AC #2). They pass TODAY and must keep passing once the predicate is wired
 *    into VoIPService; they lock the truth-table so the wiring cannot silently
 *    widen / narrow which secrets count as Type3.
 *
 *  - RED (@Ignore) — `instanceConfig_*` assert plumbing that does NOT exist yet
 *    (AC #4, `allowTCP`). They are @Ignore'd so the committed tree / build stays
 *    green; ACTIVATION = remove @Ignore, run on a device/emulator, watch it fail
 *    RED, then implement until GREEN. (junit4 @Ignore is the framework-native red
 *    marker — the analogue of story 9.2's CMake DISABLED target and 9.4's env
 *    skip-gate. We avoid System.getenv here because it is unreliable on Android.)
 *
 * The BULK of 9.5's red surface is NOT here, because it lives in source that an
 * on-device unit test cannot exercise:
 *   - VoIPService lifecycle wiring (AC #2 / #3) and the JNI `allowTCP` marshalling
 *     (AC #4) — asserted by ci/atdd/atdd_9_5_wiring_assert.sh (source assertion).
 *   - the RU+EN group-call toast (AC #6) — same script.
 *   - the real ≥60 s two-device call + pcap (AC #5) — the manual smoke runbook
 *     at _bmad-output/implementation-artifacts/9-5-tests/smoke-runbook.md.
 * See the ATDD checklist:
 *   _bmad-output/test-artifacts/atdd-checklist-9-5-android-calls-over-type3.md
 *
 * Convention matches org.telegram.tgnet.test.* : JUnit4, no custom @RunWith
 * (these assertions are pure JVM logic / reflection — no Android context needed).
 */
class Type3WiringTest {

    // ---- GREEN ANCHOR — AC #2 gating predicate (passes today; must-not-regress) ----

    @Test
    fun isType3Secret_acceptsFfPrefixAtLeast36Hex() {
        // Type3 secret = 0xff marker (1B) + 16-byte key (34 hex) + >=1 domain byte
        // => at least 36 hex chars, lower- or upper-case "ff" marker.
        val secret = "ff" + "00".repeat(16) + "61" // ff + 16-byte key + 'a' = 36 hex
        assertTrue("36-hex ff-secret must be recognised", secret.length >= 36)
        assertTrue(Type3ShimController.isType3Secret(secret))
        assertTrue("FF marker tolerated", Type3ShimController.isType3Secret(secret.uppercase()))
    }

    @Test
    fun isType3Secret_rejectsNonType3() {
        assertFalse("null secret", Type3ShimController.isType3Secret(null))
        assertFalse("empty secret", Type3ShimController.isType3Secret(""))
        // plain MTProto secret: 32 hex, no ff marker => not Type3.
        assertFalse("dd-prefixed 32 hex", Type3ShimController.isType3Secret("dd" + "00".repeat(15)))
        // ff prefix but shorter than 36 hex => not Type3.
        assertFalse("ff but too short", Type3ShimController.isType3Secret("ff" + "00".repeat(10)))
    }

    // ---- RED (@Ignore) — AC #4 `allowTCP` plumbed onto Java Instance.Config ----

    @Test
    fun instanceConfig_exposesAllowTcpBooleanField() {
        // Today Instance.Config has no `allowTCP` field => getDeclaredField throws
        // NoSuchFieldException => RED. Goes GREEN once the field is added.
        val field = Instance.Config::class.java.getDeclaredField("allowTCP")
        val type = field.type
        assertTrue(
            "Instance.Config.allowTCP must be a boolean, was ${type.name}",
            type == java.lang.Boolean.TYPE || type == java.lang.Boolean::class.java
        )
    }
}
