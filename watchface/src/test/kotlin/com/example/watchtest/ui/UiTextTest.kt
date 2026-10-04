package com.example.watchtest.ui

import com.example.watchtest.R
import com.example.watchtest.uii.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiTextTest {
    private val strings: Map<String, String> by lazy {
        val xml = File("src/main/res/values/strings.xml").readText()
        Regex("""<string name="([^"]+)">([^<]*)</string>""").findAll(xml).associate { it.groupValues[1] to it.groupValues[2] }
    }

    /** Labels the wearer reads on the home, SOS, countdown, confirmation, prompt and pairing screens. */
    private val mainScreenLabels = listOf(
        "sos", "bpm", "home_waiting_unit", "fix_permission", "home_unpaired_title", "home_unpaired_caption", "home_unpaired_start",
        "repair_title", "repair_button", "sos_header", "sos_pre", "sos_now", "sos_fall", "cancel", "sent", "waiting_network",
        "fall_question", "fall_ok", "pairing_busy", "pair_need_eight", "pair_err_wrong", "pair_err_rate", "pair_err_devices",
        "pair_err_network", "pair_err_other", "delivery_failed",
    )

    @Test fun noLabelOnTheMainScreensIsLongerThanThreeWords() {
        for (key in mainScreenLabels) {
            val text = strings[key]
            assertNotNull("missing string $key", text)
            val words = text!!.trim().split(' ').filter { w -> w.any(Char::isLetterOrDigit) }
            assertTrue("\"$text\" ($key) has ${words.size} words", words.size <= 3)
        }
    }

    @Test fun everySpokenDescriptionExistsAndIsNotJustASymbol() {
        val spoken = strings.filterKeys { it.startsWith("cd_") }
        assertTrue(spoken.size >= 15)
        for ((key, text) in spoken) assertTrue("$key is too short to describe anything", text.trim().length >= 6)
    }

    @Test fun everyPairingOutcomeHasAShortMessageExceptSuccess() {
        assertNull(pairMessage(Pairing.Result.PAIRED))
        for (r in Pairing.Result.entries.filter { it != Pairing.Result.PAIRED }) assertNotNull(r.name, pairMessage(r))
        assertEquals(R.string.pair_err_wrong, pairMessage(Pairing.Result.INVALID_CODE))
        assertEquals(R.string.pair_err_rate, pairMessage(Pairing.Result.RATE_LIMITED))
        assertEquals(R.string.pair_err_devices, pairMessage(Pairing.Result.TOO_MANY_DEVICES))
        assertEquals(R.string.pair_err_network, pairMessage(Pairing.Result.NETWORK))
        assertEquals(R.string.pair_err_other, pairMessage(Pairing.Result.ERROR))
    }

    @Test fun countdownNumberStaysAtOneUntilTimeIsReallyUp() {
        assertEquals(5, secondsLeft(5_000))
        assertEquals(5, secondsLeft(4_001))
        assertEquals(4, secondsLeft(4_000))
        assertEquals(1, secondsLeft(1))
        assertEquals(0, secondsLeft(0))
        assertEquals(0, secondsLeft(-250))
    }

    @Test fun sosOptionsMapToTheTypesTheServerKnows() {
        assertEquals(listOf("pre_seizure", "seizure", "fall"), SosKind.entries.map { it.payloadType })
    }
}
