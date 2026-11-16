// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
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
}
