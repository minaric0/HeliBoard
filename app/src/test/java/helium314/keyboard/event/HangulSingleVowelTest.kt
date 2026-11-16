// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HangulSingleVowelTest {
    private var now = 1_000L
    private var timeout = 450
    private val combiner = HangulCombiner(singleVowelTimeout = { timeout }, clock = { now })
    private val mappings = listOf(
        'ㅏ' to "ㅑ", 'ㅓ' to "ㅕ", 'ㅗ' to "ㅛ", 'ㅜ' to "ㅠ", 'ㅐ' to "ㅒ", 'ㅔ' to "ㅖ",
        'ㄱ' to "ㄲ", 'ㄷ' to "ㄸ", 'ㅂ' to "ㅃ", 'ㅅ' to "ㅆ", 'ㅈ' to "ㅉ"
    )

    private fun tap(c: Char, delay: Long = 50) {
        now += delay
        combiner.processEvent(null, Event.createSoftwareKeypressEvent(c.code, 0, 0, 50, 100, false))
    }

    private fun type(text: String) = text.forEach { tap(it) }
    private fun text() = combiner.combiningStateFeedback.toString()
    private fun delete() = combiner.processEvent(null, Event.createSoftwareKeypressEvent(KeyCode.DELETE, 0, 50, 100, false))

    @Test fun allDoubleTapMappings() {
        mappings.forEach { (base, doubled) ->
            combiner.reset()
            tap(base)
            tap(base)
            assertEquals(doubled, text(), "$base double tap")
        }
    }

    @Test fun stockLayoutDoesNotUseTimedTaps() {
        mappings.forEach { (base, _) ->
            val stock = HangulCombiner()
            repeat(2) { stock.processEvent(null, Event.createSoftwareKeypressEvent(base.code, 0, 0, 50, 100, false)) }
            assertEquals("$base$base", stock.combiningStateFeedback.toString())
        }
    }

    @Test fun exactDeadlineIsInclusive() {
        tap('ㅏ')
        tap('ㅏ', 450)
        assertEquals("ㅑ", text())
    }

    @Test fun afterDeadlineStartsAnotherVowel() {
        tap('ㅏ')
        tap('ㅏ', 451)
        assertEquals("ㅏㅏ", text())
    }

    @Test fun slowConsonantsAreSeparate() {
        tap('ㄱ')
        tap('ㄱ', 451)
        assertEquals("ㄱㄱ", text())
    }

    @Test fun intervalCanBeChanged() {
        timeout = 200
        tap('ㅏ')
        tap('ㅏ', 201)
        assertEquals("ㅏㅏ", text())
        combiner.reset()
        timeout = 800
        tap('ㅏ')
        tap('ㅏ', 800)
        assertEquals("ㅑ", text())
    }

    @Test fun disabledDoubleTapKeepsLongPressLayoutUsable() {
        timeout = 0
        type("ㅏㅏㄲㅑ")
        assertEquals("ㅏㅏ꺄", text())
    }

    @Test fun tripleAndQuadrupleTapsMakeNonOverlappingPairs() {
        type("ㅏㅏㅏ")
        assertEquals("ㅑㅏ", text())
        tap('ㅏ')
        assertEquals("ㅑㅑ", text())
        combiner.reset()
        type("ㄱㄱㄱㄱ")
        assertEquals("ㄲㄲ", text())
    }

    @Test fun combinesInitialAndVowelInSyllable() {
        type("ㄱㄱㅏㅏ")
        assertEquals("꺄", text())
    }

    @Test fun tenseFinalMovesToNextInitial() {
        type("ㄱㅏㄱㄱ")
        assertEquals("갂", text())
        tap('ㅏ')
        assertEquals("가까", text())
    }

    @Test fun ssangSiotFinalMovesToNextInitial() {
        type("ㄱㅏㅅㅅ")
        assertEquals("갔", text())
        tap('ㅏ')
        assertEquals("가싸", text())
    }

    @Test fun tenseConsonantWithoutFinalFormStartsNextSyllable() {
        type("ㄱㅏㄷㄷㅏ")
        assertEquals("가따", text())
    }

    @Test fun compoundFinalStillSplits() {
        type("ㄷㅏㄹㄱㅏ")
        assertEquals("달가", text())
    }

    @Test fun compoundVowelsStillCombine() {
        listOf("ㄱㅗㅏ" to "과", "ㄱㅗㅐ" to "괘", "ㄱㅗㅣ" to "괴",
            "ㄱㅜㅓ" to "궈", "ㄱㅜㅔ" to "궤", "ㄱㅜㅣ" to "귀", "ㅇㅡㅣ" to "의")
            .forEach { (input, output) ->
                combiner.reset()
                type(input)
                assertEquals(output, text())
            }
    }

    @Test fun backspaceUndoesSecondTapBeforeFirstTap() {
        type("ㄱㅏㅏ")
        assertEquals("갸", text())
        delete()
        assertEquals("가", text())
        delete()
        assertEquals("ㄱ", text())
        delete()
        assertEquals("", text())
    }

    @Test fun backspaceUndoesTenseFinalSafely() {
        type("ㄱㅏㄱㄱ")
        delete()
        assertEquals("각", text())
        delete()
        assertEquals("가", text())
    }

    @Test fun deletingClearsTapCandidate() {
        type("ㅏㅏ")
        delete()
        tap('ㅏ')
        assertEquals("ㅏㅏ", text())
    }

    @Test fun resetCancelsTapCandidate() {
        tap('ㅏ')
        combiner.reset()
        tap('ㅏ')
        assertEquals("ㅏ", text())
    }

    @Test fun cursorMovementCancelsTapCandidate() {
        tap('ㅏ')
        combiner.processEvent(null, Event.createCursorMovedEvent(0))
        tap('ㅏ')
        assertEquals("ㅏ", text()) // earlier text is flushed into the returned event chain
    }

    @Test fun shiftCancelsTapCandidate() {
        tap('ㅏ')
        combiner.processEvent(null, Event.createSoftwareKeypressEvent(KeyCode.SHIFT, 0, 50, 100, false))
        tap('ㅏ')
        assertEquals("ㅏㅏ", text())
    }

    @Test fun spacesFlushComposition() {
        type("ㄱㅏㅏ")
        val event = combiner.processEvent(null, Event.createSoftwareKeypressEvent(' '.code, 0, 0, 50, 100, false))
        assertEquals("갸", event.textToCommit.toString())
        assertEquals("", text())
        tap('ㅏ')
        assertEquals("ㅏ", text())
    }

    @Test fun repeatsDoNotMakeDoubleTaps() {
        tap('ㅏ')
        combiner.processEvent(null, Event.createSoftwareKeypressEvent('ㅏ'.code, 0, 0, 50, 100, true))
        assertEquals("ㅏㅏ", text())
    }

    @Test fun hardwareAndRestoredTextDoNotMakeDoubleTaps() {
        repeat(2) { combiner.processEvent(null, Event.createHardwareKeypressEvent('ㅏ'.code, 0, 0, null, false)) }
        assertEquals("ㅏㅏ", text())
        combiner.reset()
        repeat(2) { combiner.processEvent(null, Event.createEventForCodePointFromUnknownSource('ㅏ'.code)) }
        assertEquals("ㅏㅏ", text())
    }

    @Test fun popupsAndNonHangulInterruptPairs() {
        type("ㅏㅑㅏ")
        assertEquals("ㅏㅑㅏ", text())
        combiner.reset()
        type("ㅏ@ㅏ")
        assertEquals("ㅏ@ㅏ", text())
    }

    @Test fun ordinaryKoreanTextStillWorks() {
        type("ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ")
        assertEquals("안녕하세요", text())
        assertTrue(combiner.history.isNotEmpty())
    }

    @Test fun everyModernSyllableMatchesUnicodeNormalization() {
        val initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        val vowels = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
        val finals = "ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
        for (expected in '가'..'힣') {
            combiner.reset()
            Normalizer.normalize(expected.toString(), Normalizer.Form.NFD).forEach { jamo ->
                tap(when (jamo.code) {
                    in 0x1100..0x1112 -> initials[jamo.code - 0x1100]
                    in 0x1161..0x1175 -> vowels[jamo.code - 0x1161]
                    else -> finals[jamo.code - 0x11a8]
                })
            }
            assertEquals(expected.toString(), text(), "Unicode syllable $expected")
        }
    }

    @Test fun everyCompoundFinalSplitsAndDeletes() {
        listOf("ㄱㅅ" to "각사", "ㄴㅈ" to "간자", "ㄴㅎ" to "간하", "ㄹㄱ" to "갈가",
            "ㄹㅁ" to "갈마", "ㄹㅂ" to "갈바", "ㄹㅅ" to "갈사", "ㄹㅌ" to "갈타",
            "ㄹㅍ" to "갈파", "ㄹㅎ" to "갈하", "ㅂㅅ" to "갑사").forEach { (final, expected) ->
            combiner.reset()
            type("ㄱㅏ${final}ㅏ")
            assertEquals(expected, text())
            delete()
            assertEquals(expected.take(1) + final.last(), text())
            delete()
            assertEquals(expected.take(1), text())
        }
    }

    @Test fun doubleTapUndoRestoresEveryCompositionContext() {
        listOf("", "ㄱㅏ", "ㄱㅏㄱ", "ㄱㅗ", "ㄱㅜ", "ㄱㅏㄹ", "ㅁㅏㄴㅏㄷㅏㄱㅗ").forEach { prefix ->
            mappings.forEach { (base, _) ->
                combiner.reset()
                type(prefix)
                tap(base, 1_000)
                val firstTap = text()
                tap(base)
                delete()
                assertEquals(firstTap, text(), "Undo $prefix + $base$base")
            }
        }
    }

    @Test fun supplementaryTextSurvivesCompositionAndDeletion() {
        type("ㄱㅏ")
        combiner.processEvent(null, Event.createSoftwareKeypressEvent(0x20BB7, 0, 0, 50, 100, false))
        assertEquals("가\uD842\uDFB7", text())
        delete()
        assertEquals("가", text())
    }
}
