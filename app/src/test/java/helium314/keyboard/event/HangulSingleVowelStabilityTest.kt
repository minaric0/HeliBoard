// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import kotlin.test.Test
import kotlin.test.assertEquals

class HangulSingleVowelStabilityTest {
    private var now = 1_000L
    private val combiner = HangulCombiner(singleVowelTimeout = { 450 }, clock = { now })

    private fun tap(codePoint: Int) {
        now += 50
        combiner.processEvent(null, Event.createSoftwareKeypressEvent(codePoint, 0, 0, 50, 100, false))
    }

    private fun type(keys: String) = keys.forEach { tap(it.code) }
    private fun text() = combiner.combiningStateFeedback.toString()
    private fun delete() = combiner.processEvent(null,
        Event.createSoftwareKeypressEvent(KeyCode.DELETE, 0, 50, 100, false))

    @Test fun everyModernSyllableComposesFromAvailableKeys() {
        // Unicode Hangul syllable order; input strings are the physical 단모음 taps.
        initials.forEachIndexed { initial, onset ->
            vowels.forEachIndexed { vowel, medial ->
                finals.forEachIndexed { final, coda ->
                    combiner.reset()
                    type(onset + medial + coda)
                    val expected = (0xac00 + (initial * 21 + vowel) * 28 + final).toChar().toString()
                    assertEquals(expected, text(), "keys=$onset$medial$coda")
                }
            }
        }
    }

    @Test fun everyFinalSplitsBeforeFollowingVowel() {
        // Remaining coda and next onset after re-syllabification, in Unicode final order.
        val splits = listOf(
            "" to "ㄱ", "" to "ㄲ", "ㄱ" to "ㅅ", "" to "ㄴ", "ㄴ" to "ㅈ", "ㄴ" to "ㅎ",
            "" to "ㄷ", "" to "ㄹ", "ㄹ" to "ㄱ", "ㄹ" to "ㅁ", "ㄹ" to "ㅂ", "ㄹ" to "ㅅ",
            "ㄹ" to "ㅌ", "ㄹ" to "ㅍ", "ㄹ" to "ㅎ", "" to "ㅁ", "" to "ㅂ", "ㅂ" to "ㅅ",
            "" to "ㅅ", "" to "ㅆ", "" to "ㅇ", "" to "ㅈ", "" to "ㅊ", "" to "ㅋ",
            "" to "ㅌ", "" to "ㅍ", "" to "ㅎ"
        )
        val finalOrder = "\u0000ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
        val initialOrder = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        finals.drop(1).forEachIndexed { index, coda ->
            combiner.reset()
            type("ㄱㅏ${coda}ㅏ")
            val (remaining, onset) = splits[index]
            val first = (0xac00 + if (remaining.isEmpty()) 0 else finalOrder.indexOf(remaining.single())).toChar()
            val second = (0xac00 + initialOrder.indexOf(onset.single()) * 21 * 28).toChar()
            assertEquals("$first$second", text(), "split=$coda")
        }
    }

    @Test fun everySyllableBackspaceRestoresPreviousPhysicalTap() {
        initials.forEach { onset ->
            vowels.forEach { medial ->
                finals.forEach { coda ->
                    val keys = onset + medial + coda
                    combiner.reset()
                    type(keys.dropLast(1))
                    val previous = text()
                    combiner.reset()
                    type(keys)
                    delete()
                    assertEquals(previous, text(), "backspace keys=$keys")
                }
            }
        }
    }

    @Test fun restoringTextWithKnownCoordinatesNeverCreatesDoubleTap() {
        repeat(2) {
            combiner.processEvent(null, Event.createEventForCodePointFromAlreadyTypedText('ㅏ'.code, 50, 100))
        }
        assertEquals("ㅏㅏ", text())
        tap('ㅏ'.code)
        assertEquals("ㅏㅏㅏ", text())
    }

    @Test fun restoringTextKeepsLiteralJamoUnchanged() {
        "ㄱㅏ".forEach {
            combiner.processEvent(null, Event.createEventForCodePointFromAlreadyTypedText(it.code, 50, 100))
        }
        assertEquals("ㄱㅏ", text())
    }

    @Test fun supplementaryCharactersSurviveTypingAndBackspace() {
        type("ㄱㅏ")
        tap(0x1d11e) // Musical G clef, not an emoji.
        assertEquals("가\uD834\uDD1E", text())
        delete()
        assertEquals("가", text())
        tap('ㄴ'.code)
        assertEquals("가ㄴ", text())
    }

    @Test fun longCompositionKeepsPrefixWhenPairIsReplacedAndUndone() {
        val prefix = "가".repeat(5_000)
        type("ㄱㅏ".repeat(5_000))
        assertEquals(prefix, text())
        now += 451 // Start a new pair after the final ㅏ in the prefix.
        type("ㅏㅏ")
        assertEquals(prefix + "ㅑ", text())
        delete()
        assertEquals(prefix + "ㅏ", text())
        delete()
        assertEquals(prefix, text())
    }

    companion object {
        private val initials = listOf("ㄱ", "ㄱㄱ", "ㄴ", "ㄷ", "ㄷㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅂㅂ", "ㅅ", "ㅅㅅ", "ㅇ", "ㅈ", "ㅈㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")
        private val vowels = listOf("ㅏ", "ㅐ", "ㅏㅏ", "ㅐㅐ", "ㅓ", "ㅔ", "ㅓㅓ", "ㅔㅔ", "ㅗ", "ㅗㅏ", "ㅗㅐ", "ㅗㅣ", "ㅗㅗ", "ㅜ", "ㅜㅓ", "ㅜㅔ", "ㅜㅣ", "ㅜㅜ", "ㅡ", "ㅡㅣ", "ㅣ")
        private val finals = listOf("", "ㄱ", "ㄱㄱ", "ㄱㅅ", "ㄴ", "ㄴㅈ", "ㄴㅎ", "ㄷ", "ㄹ", "ㄹㄱ", "ㄹㅁ", "ㄹㅂ", "ㄹㅅ", "ㄹㅌ", "ㄹㅍ", "ㄹㅎ", "ㅁ", "ㅂ", "ㅂㅅ", "ㅅ", "ㅅㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")
    }
}
