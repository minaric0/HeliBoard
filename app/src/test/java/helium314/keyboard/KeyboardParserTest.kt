// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
import android.view.View
import com.android.inputmethod.keyboard.ProximityInfo
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Key.KeyParams
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeySpecParser.KeySpecParserError
import helium314.keyboard.keyboard.internal.KeyboardBuilder
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.PopupKeySpec
import helium314.keyboard.keyboard.internal.TouchPositionCorrection
import helium314.keyboard.keyboard.internal.UniqueKeysCache
import helium314.keyboard.keyboard.internal.keyboard_parser.LayoutParser
import helium314.keyboard.keyboard.internal.keyboard_parser.LocaleKeyboardInfos
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.POPUP_KEYS_LAYOUT
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowInputMethodManager2::class,
    ShadowProximityInfo::class,
])
class ParserTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val params = KeyboardParams()

    init {
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
        params.mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardElement.ALPHABET)
        params.mPopupKeyOrder.add(POPUP_KEYS_LAYOUT)
        params.mPopupKeyHintOrder.add(POPUP_KEYS_LAYOUT)
        LocaleKeyboardInfos.addLocaleKeyTextsToParams(latinIME, params, LocaleKeyboardInfos.POPUP_KEYS_NORMAL)
    }

    @Test fun simpleParser() {
        val layoutStrings = listOf(
"""
a
b
c

d
e
f
""", // normal
"""
a
b
c

d
e
f
""", // spaces in the empty line
"""
a
b
c

d
e
f
""".replace("\n", "\r\n"), // windows file endings
"""
a
b
c


d
e
f

""", // too many newlines
"""
a
b x
c v

d
e
f
""", // spaces in the end
"""
a
b
c

d
e
f""", // no newline at the end
        )
        val wantedKeyLabels = listOf(listOf("a", "b", "c"), listOf("d", "e", "f"))
        layoutStrings.forEach { layout ->
            val keyLabels = LayoutParser.parseSimpleString(layout)
                .map { row -> row.map { it.toKeyParams(params).mLabel } }
            assertEquals(wantedKeyLabels, keyLabels)
        }
    }

    @Test fun simpleKey() {
        assertIsExpected("""[[{ "$": "auto_text_key" "label": "a" }]]""", Expected('a'.code, "a"))
        assertIsExpected("""[[{ "$": "text_key" "label": "a" }]]""", Expected('a'.code, "a"))
        assertIsExpected("""[[{ "label": "a" }]]""", Expected('a'.code, "a"))
    }

    @Test fun labelAndExplicitCode() {
        assertIsExpected("""[[{ "$": "text_key" "label": "a", "code": 98 }]]""", Expected('b'.code, "a"))
    }

    @Test fun labelAndImplicitCode() {
        assertIsExpected("""[[{ "$": "text_key" "label": "a|b" }]]""", Expected('b'.code, "a"))
    }

    @Test fun labelAndImplicitText() {
        assertIsExpected("""[[{ "$": "text_key" "label": "a|bb" }]]""", Expected(KeyCode.MULTIPLE_CODE_POINTS, "a", text = "bb"))
        assertIsExpected("""[[{ "$": "text_key" "label": "a|" }]]""", Expected(KeyCode.MULTIPLE_CODE_POINTS, "a", text = ""))
    }

    @Test fun labelAndImplicitAndExplicitCode() { // explicit code overrides implicit code
        assertIsExpected("""[[{ "code": 32, "label": "a|b" }]]""", Expected(' '.code, "a"))
        assertIsExpected("""[[{ "code": 32, "label": "a|!code/key_delete" }]]""", Expected(' '.code, "a"))
        assertIsExpected("""[[{ "code": 32, "label": "a|!code/-1" }]]""", Expected(' '.code, "a"))
        assertIsExpected("""[[{ "code": -1, "label": "a|!code/key_delete" }]]""", Expected(KeyCode.CTRL, "a"))
        assertIsExpected("""[[{ "code": 32, "label": "a|bb" }]]""", Expected(' '.code, "a"))
        assertIsExpected("""[[{ "code": 32, "label": "a|bb", "popup": { "main": { "code": 32, "label": "!icon/undo|!code/key_delete" } } }]]""", Expected(' '.code, "a", popups = listOf(null to ' '.code)))
        assertIsExpected("""[[{ "code": 32, "label": "a|bb", "popup": { "main": { "code": -1, "label": "!icon/undo|!code/key_delete" } } }]]""", Expected(' '.code, "a", popups = listOf(null to KeyCode.CTRL)))
        assertIsExpected("""[[{ "code": 32, "label": "a|bb", "popup": { "main": { "code": 32, "label": "a|!code/key_delete" } } }]]""", Expected(' '.code, "a", popups = listOf("a" to ' '.code)))
        assertIsExpected("""[[{ "code": 32, "label": "a|bb", "popup": { "main": { "code": -1, "label": "a|!code/key_delete" } } }]]""", Expected(' '.code, "a", popups = listOf("a" to KeyCode.CTRL)))
    }

    @Test fun keyWithIconAndExplicitCode() {
        assertIsExpected("""[[{ "label": "!icon/clipboard", "code": 55 }]]""", Expected(55, icon = "clipboard"))
        assertIsExpected("""[[{ "label": "!icon/clipboard", "code": -1 }]]""", Expected(KeyCode.CTRL, icon = "clipboard"))
    }

    @Test fun keyWithIconAndImplicitCode() {
        assertIsExpected("""[[{ "label": "!icon/clipboard_action_key|!code/key_clipboard" }]]""", Expected(KeyCode.CLIPBOARD, icon = "clipboard_action_key"))
        assertIsExpected("""[[{ "label": "!icon/clipboard_action_key|!code/key_clipboard", "popup": { "main": { "label": "!icon/undo|!code/key_delete" } } }]]""", Expected(KeyCode.CLIPBOARD, icon = "clipboard_action_key", popups = listOf(null to KeyCode.DELETE)))
    }

    @Test fun popupKeyWithIconAndExplicitCode() {
        assertIsExpected("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key", "code": 32 }
      ]
    } }]]""", Expected('a'.code, "a", popups = listOf(null to ' '.code)))
    }

    @Test fun popupKeyWithIconAndExplicitAndImplicitCode() {
        assertIsExpected("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|", "code": 32 }
      ]
    } }]]""", Expected('a'.code, "a", popups = listOf(null to ' '.code)))
        assertIsExpected("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|abc", "code": 32 }
      ]
    } }]]""", Expected('a'.code, "a", popups = listOf(null to ' '.code)))
    }

    @Test fun labelAndImplicitCodeForPopup() {
        assertIsExpected("""[[{ "$": "text_key" "label": "a|b", "popup": { "main": { "label": "b|a" } } }]]""", Expected('b'.code, "a", popups = listOf("b" to 'a'.code)))
        assertIsExpected("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|" }
      ]
    } }]]""", Expected('a'.code, "a",
            popups = listOf(null to KeyCode.MULTIPLE_CODE_POINTS))
        )
    }

    @Test fun `| works`() {
        assertIsExpected("""[[{ "label": "|", "popup": { "main": { "label": "|" } } }]]""", Expected('|'.code, "|", popups = listOf("|" to '|'.code)))
    }

    @Test fun currencyKey() {
        assertIsExpected("""[[{ "label": "$$$" }]]""", Expected('$'.code, "$", popups = listOf("£", "¢", "€", "¥", "₱").map { it to it.first().code }))
    }

    @Test fun currencyKeyWithOtherCurrencyCode() {
        assertIsExpected("""[[{ "label": "$$$", code: -805 }]]""", Expected('¥'.code, "$", popups = listOf("£", "¢", "€", "¥", "₱").map { it to it.first().code }))
    }

    @Test fun currencyPopup() {
        assertIsExpected("""[[{ "label": "p", "popup": { "main": { "label": "$$$" } } }]]""", Expected('p'.code, "p", null, null, listOf("$" to '$'.code)))
        assertIsExpected("""[[{ "label": "p", "popup": { "main": { "label": "a", "code": -804 } } }]]""", Expected('p'.code, "p", null, null, listOf("a" to '€'.code)))
        assertIsExpected("""[[{ "label": "p", "popup": { "main": { "label": "!icon/clipboard_action_key", "code": -804 } } }]]""", Expected('p'.code, "p", null, null, listOf(null to '€'.code)))
    }

    @Test fun weirdCurrencyKey() {
        assertIsExpected("""[[{ "code": -801, "label": "currency_slot_1", "popup": {
      "main": { "code": -802, "label": "currency_slot_2" },
      "relevant": [
        { "code": -806, "label": "currency_slot_6" },
        { "code": -803, "label": "currency_slot_3" },
        { "code": -804, "label": "currency_slot_4" },
        { "code": -805, "label": "currency_slot_5" },
        { "code": -804, "label": "$$$4" }
      ]
    } }]]""", Expected('$'.code, "$", popups = listOf("£" to '£'.code, "₱" to '₱'.code, "¢" to '¢'.code, "€" to '€'.code, "¥" to '¥'.code, "¥" to '€'.code)))
    }

    @Test fun caseSelector() {
        assertIsExpected("""[[{ "$": "case_selector",
      "lower": { "code":  105, "label": "i" },
      "upper": { "code":  304, "label": "İ" }
    }]]""", Expected(105, "i"))
    }

    @Test fun caseSelectorWithPopup() {
        assertIsExpected("""[[{ "$": "case_selector",
      "lower": { "code":   59, "label": ";", "popup": {
        "relevant": [
          { "code":   58, "label": ":" }
        ]
      } },
      "upper": { "code":   58, "label": ":", "popup": {
        "relevant": [
          { "code":   59, "label": ";" }
        ]
      } }
    }]]""", Expected(';'.code, ";", popups = listOf(":").map { it to it.first().code }))
    }

    @Test fun shiftSelector() {
        assertIsExpected("""[[{ "$": "shift_state_selector",
      "shiftedManual": { "code":   62, "label": ">", "popup": {
        "relevant": [
          { "code":   46, "label": "." }
        ]
      } },
      "default": { "code":   46, "label": ".", "popup": {
        "relevant": [
          { "code":   62, "label": ">" }
        ]
      } }
    }]]""", Expected('.'.code, ".", popups = listOf(">").map { it to it.first().code }))
    }

    @Test fun nestedSelectors() {
        assertIsExpected("""[[{ "$": "shift_state_selector",
      "shiftedManual": { "code":   34, "label": "\"", "popup": {
        "relevant": [
          { "code":   33, "label": "!" },
          { "code":   39, "label": "'"}
        ]
      } },
      "default": { "$": "variation_selector",
        "email":   { "code":   64, "label": "@" },
        "uri":     { "code":   47, "label": "/" },
        "default": { "code":   39, "label": "'", "popup": {
          "relevant": [
            { "code":   33, "label": "!" },
            { "code":   34, "label": "\"" }
          ]
        } }
      }
    }]]""", Expected('\''.code, "'", popups = listOf("!", "\"").map { it to it.first().code }))
    }

    @Test fun layoutDirectionSelector() {
        assertIsExpected("""[[{ "$": "layout_direction_selector",
      "ltr": { "code":   40, "label": "(", "popup": {
        "main": { "code":   60, "label": "<" },
        "relevant": [
          { "code":   91, "label": "[" },
          { "code":  123, "label": "{" }
        ]
      } },
      "rtl": { "code":   41, "label": "(", "popup": {
        "main": { "code":   62, "label": "<" },
        "relevant": [
          { "code":   93, "label": "[" },
          { "code":  125, "label": "{" }
        ]
      } }
    }]]""", Expected('('.code, "(", popups = listOf("<", "[", "{").map { it to it.first().code }))
    }

    @Test fun autoMultiTextKey() {
        assertIsExpected("""[[{ "label": "্র" }]]""", Expected(KeyCode.MULTIPLE_CODE_POINTS, "্র", text = "্র"))
    }

    @Test fun multiTextKey() { // pointless without codepoints!
        assertIsExpected("""[[{ "$": "multi_text_key", "codePoints": [2509, 2480], "label": "্র" }]]""", Expected(KeyCode.MULTIPLE_CODE_POINTS, "্র", text = "্র"))
        assertIsExpected("""[[{ "$": "multi_text_key", "codePoints": [2509, 2480], "label": "x" }]]""", Expected(KeyCode.MULTIPLE_CODE_POINTS, "x", text = "্র"))
    }

    @Test fun negativeCode() {
        assertIsExpected("""[[{ "code":   -7, "label": "delete" }]]""", Expected(-7, icon = "delete_key", background = Key.BACKGROUND_TYPE_FUNCTIONAL))
    }

    @Test fun keyWithType() {
        assertIsExpected("""[[{ "code":   57, "label": "9", "type": "numeric" }]]""", Expected(57, "9"))
        assertIsExpected("""[[{ "code":   -7, "label": "delete", "type": "enter_editing" }]]""", Expected(-7, icon = "delete_key", background = Key.BACKGROUND_TYPE_ACTION))
        // -207 gets translated to -202 in Int.toKeyEventCode and view_phone2 to symbol
        assertIsExpected("""[[{ "code": -207, "label": "view_phone2", "type": "system_gui" }]]""", Expected(-202, "!?#", background = Key.BACKGROUND_TYPE_FUNCTIONAL))
    }

    @Test fun spaceKey() {
        assertIsExpected("""[[{ "code":   32, "label": "space" }]]""", Expected(32, icon = "space_key", background = Key.BACKGROUND_TYPE_SPACEBAR))
    }

    @Test fun emojiKey() {
        assertIsExpected("""[[{ "label": "emoji" }]]""", Expected(KeyCode.EMOJI, icon = "emoji", background = Key.BACKGROUND_TYPE_FUNCTIONAL))
    }

    @Test fun invalidKeys() {
        assertFailsWith<KeySpecParserError> {
            LayoutParser.parseJsonString("""[[{ "label": "!icon/clipboard_action_key" }]]""")
                .map { row -> row.mapNotNull { it.compute(params)?.toKeyParams(params) } }
        }
    }

    @Test fun removeRedundantPopupKeys() {
        val keyParams = LayoutParser.parseJsonString("""[[{"label": "k"}, { "label": "w", "popup": {
          "relevant": [{ "label": "!hasLabels!" }, { "label": "k" }, { "label": "m" }]
    } }]]""").flatMap { row -> row.mapNotNull { it.compute(params)?.toKeyParams(params) } }
        keyParams[1].mAbsoluteWidth = 1f
        keyParams[1].mAbsoluteHeight = 1f
        val key = keyParams[1].createKey()
        assertEquals(null, key.hintLabel)
        assertEquals(2, key.popupKeys?.size)
        val lettersOnBaseLayout = PopupKeySpec.LettersOnBaseLayout()
        lettersOnBaseLayout.addLetter(keyParams[0])
        val keyWithoutRedundantPopups = Key.removeRedundantPopupKeys(key, lettersOnBaseLayout)
        assertEquals(1, keyWithoutRedundantPopups.popupKeys?.size)
        assertEquals(null, keyWithoutRedundantPopups.hintLabel)
    }

    @Test fun popupWithCodeAndLabel() {
        val key = LayoutParser.parseJsonString("""[[{ "label": "w", "popup": {
          "main": { "code":   55, "label": "!" }
    } }]]""").flatMap { row -> row.mapNotNull { it.compute(params) } }.single()
        assertEquals("!", key.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals('7'.code, key.toKeyParams(params).mPopupKeys?.first()?.mCode)
        assertEquals("!", key.toKeyParams(params).mHintLabel)
    }

    @Test fun popupWithCodeAndIcon() {
        val key = LayoutParser.parseJsonString("""[[{ "label": "w", "popup": {
          "main": { "code":   55, "label": "!icon/clipboard_action_key" }
    } }]]""").flatMap { row -> row.mapNotNull { it.compute(params) } }.single()
        assertEquals(null, key.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals("clipboard_action_key", key.toKeyParams(params).mPopupKeys?.first()?.mIconName)
        assertEquals('7'.code, key.toKeyParams(params).mPopupKeys?.first()?.mCode)
    }

    @Test fun popupToolbarKey() {
        val key = LayoutParser.parseJsonString("""[[{ "label": "x", "popup": {
          "main": { "label": "undo" }
    } }]]""").flatMap { row -> row.mapNotNull { it.compute(params) } }.single()
        assertEquals(null, key.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals("undo", key.toKeyParams(params).mPopupKeys?.first()?.mIconName)
        assertEquals(KeyCode.UNDO, key.toKeyParams(params).mPopupKeys?.first()?.mCode)
        assertEquals("undo", key.toKeyParams(params).mHintIconName)
    }

    @Test fun popupKeyWithIconAndImplicitText() {
        val key = LayoutParser.parseJsonString("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|aa" }
      ]
    } }]]""").flatMap { row -> row.mapNotNull { it.compute(params) } }.single()
        assertEquals(null, key.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals("go_key", key.toKeyParams(params).mPopupKeys?.first()?.mIconName)
        assertEquals(KeyCode.MULTIPLE_CODE_POINTS, key.toKeyParams(params).mPopupKeys?.first()?.mCode)
        assertEquals("aa", key.toKeyParams(params).mPopupKeys?.first()?.mOutputText)
        assertEquals("go_key", key.toKeyParams(params).mHintIconName)

        val key2 = LayoutParser.parseJsonString("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|" }
      ]
    } }]]""").flatMap { row -> row.mapNotNull { it.compute(params) } }.single()
        assertEquals(null, key2.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals("go_key", key2.toKeyParams(params).mPopupKeys?.first()?.mIconName)
        assertEquals(KeyCode.MULTIPLE_CODE_POINTS, key2.toKeyParams(params).mPopupKeys?.first()?.mCode)
        assertEquals("", key2.toKeyParams(params).mPopupKeys?.first()?.mOutputText)
    }

    // output text is null here, maybe should be changed?
    @Test fun popupKeyWithIconAndCodeAndImplicitText() {
        val key = LayoutParser.parseJsonString("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|", "code": 55 }
      ]
    } }]]""").flatMap { row -> row.mapNotNull { it.compute(params) } }.single()
        assertEquals(null, key.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals("go_key", key.toKeyParams(params).mPopupKeys?.first()?.mIconName)
        assertEquals(55, key.toKeyParams(params).mPopupKeys?.first()?.mCode)
        assertEquals(null, key.toKeyParams(params).mPopupKeys?.first()?.mOutputText)

        val key2 = LayoutParser.parseJsonString("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|a", "code": 55 }
      ]
    } }]]""").flatMap { row -> row.mapNotNull { it.compute(params) } }.single()
        assertEquals(null, key2.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals("go_key", key2.toKeyParams(params).mPopupKeys?.first()?.mIconName)
        assertEquals(55, key2.toKeyParams(params).mPopupKeys?.first()?.mCode)
        assertEquals(null, key2.toKeyParams(params).mPopupKeys?.first()?.mOutputText)

        val key3 = LayoutParser.parseJsonString("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|aa", "code": 55 }
      ]
    } }]]""").flatMap { row -> row.mapNotNull { it.compute(params) } }.single()
        assertEquals(null, key3.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals("go_key", key3.toKeyParams(params).mPopupKeys?.first()?.mIconName)
        assertEquals(55, key3.toKeyParams(params).mPopupKeys?.first()?.mCode)
        assertEquals(null, key3.toKeyParams(params).mPopupKeys?.first()?.mOutputText)
    }

    @Test fun invalidPopupKeys() {
        assertFailsWith<KeySpecParserError> {
            LayoutParser.parseJsonString("""[[{ "label": "a", "popup": {
          "main": { "label": "!icon/clipboard_action_key" }
    } }]]""").map { row -> row.mapNotNull { it.compute(params)?.toKeyParams(params) } }
        }
    }

    @Test fun popupSymbolAlpha() {
        val key = LayoutParser.parseJsonString("""[[{ "label": "c", "popup": {
          "main": { "code":   -10001, "label": "x" }
    } }]]""").flatMap { row -> row.mapNotNull { it.compute(params) } }.single()
        assertEquals("x", key.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals(-10001, key.toKeyParams(params).mPopupKeys?.first()?.mCode)
    }

    @Test fun builtInSingleVowelLayoutHasAllKeysAndPopups() {
        val subtype = SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale())
            .first { it.getExtraValueOf("KeyboardLayoutSet") == "MAIN:korean_single_vowel" }
        val (_, rows) = buildKeyboard(EditorInfo(), subtype, KeyboardElement.ALPHABET)
        val jamoRows = rows.take(3).map { row -> row.filter { it.mCode in 0x3131..0x3163 }.map { it.mCode.toChar() }.joinToString("") }
        assertEquals(listOf("ㅂㅈㄷㄱㅅㅗㅐㅔ", "ㅁㄴㅇㄹㅎㅓㅏㅣ", "ㅋㅌㅊㅍㅜㅡ"), jamoRows)
        rows.take(3).forEachIndexed { index, row ->
            assertTrue(row.none { it.isSpacer }, "Korean row ${index + 1} must fill its available width")
            assertEquals(1f, row.sumOf { it.mWidth.toDouble() }.toFloat(), 0.0001f)
            val letters = row.filter { it.mCode in 0x3131..0x3163 }
            letters.forEach { assertEquals(letters.first().mWidth, it.mWidth, 0.0001f) }
            // Check the positioned keys too, including rounding at the screen edges.
            row.zipWithNext().forEach { (left, right) ->
                assertEquals(left.xPos + left.mAbsoluteWidth, right.xPos, 0.01f)
            }
        }
        val allKeys = rows.flatten()
        listOf('ㅂ' to 'ㅃ', 'ㅈ' to 'ㅉ', 'ㄷ' to 'ㄸ', 'ㄱ' to 'ㄲ', 'ㅅ' to 'ㅆ',
            'ㅗ' to 'ㅛ', 'ㅓ' to 'ㅕ', 'ㅏ' to 'ㅑ', 'ㅜ' to 'ㅠ', 'ㅐ' to 'ㅒ', 'ㅔ' to 'ㅖ').forEach { (base, popup) ->
            assertTrue(allKeys.single { it.mCode == base.code }.mPopupKeys!!.any { it.mCode == popup.code })
        }
        assertEquals(KeyCode.LANGUAGE_SWITCH, rows.last()[1].mCode)
        assertEquals('@'.code, rows.last()[2].mCode)
        assertEquals(1, allKeys.count { it.mCode == KeyCode.LANGUAGE_SWITCH })
    }

    @Test fun englishBottomRowKeepsGlobeAndAtInTextEmailAndUriFields() {
        val subtype = SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(Locale.ENGLISH, "qwerty", true)
        listOf(android.text.InputType.TYPE_CLASS_TEXT,
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI).forEach { type ->
            val editor = EditorInfo().apply { inputType = type }
            val (_, rows) = buildKeyboard(editor, subtype, KeyboardElement.ALPHABET)
            assertEquals(KeyCode.LANGUAGE_SWITCH, rows.last()[1].mCode)
            assertEquals('@'.code, rows.last()[2].mCode)
        }
    }

    @Test fun singleVowelRowsFillWidthAcrossScreenSizesAndShiftStates() {
        val subtype = SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale())
            .first { it.getExtraValueOf("KeyboardLayoutSet") == "MAIN:korean_single_vowel" }
        listOf(320, 500, 720, 1080, 1600).forEach { width ->
            listOf(false, true).forEach { numberRow ->
                KeyboardElement.entries.filter { it.isAlphabet }.forEach { element ->
                    val (_, rows) = buildKeyboard(EditorInfo(), subtype, element, width, numberRow)
                    val letterRows = rows.filter { row -> row.any { it.mCode in 0x3131..0x3163 } }
                    assertEquals(3, letterRows.size)
                    val leftEdge = rows.last().first().xPos
                    val rightEdge = rows.last().last().let { it.xPos + it.mAbsoluteWidth }
                    val letterWidth = letterRows.first().first().mAbsoluteWidth
                    letterRows.forEach { row ->
                        assertTrue(row.none { it.isSpacer }, "width=$width, element=$element, numberRow=$numberRow")
                        assertEquals(1f, row.sumOf { it.mWidth.toDouble() }.toFloat(), 0.0001f)
                        assertEquals(leftEdge, row.first().xPos, 0.01f)
                        assertEquals(rightEdge, row.last().let { it.xPos + it.mAbsoluteWidth }, 0.01f)
                        val letters = row.filter { it.mCode in 0x3131..0x3163 }
                        letters.forEach { assertEquals(letterWidth, it.mAbsoluteWidth, 0.01f) }
                    }
                }
            }
        }
    }

    @Test fun toolbarStartsExpandedAndCanBeCollapsed() {
        val strip = SuggestionStripView(latinIME, null)
        val toolbar = strip.findViewById<View>(R.id.toolbar_container)
        assertEquals(View.VISIBLE, toolbar.visibility)
        strip.setToolbarVisibility(false)
        assertEquals(View.GONE, toolbar.visibility)
    }

    @Test fun canLoadKeyboard() {
        val editorInfo = EditorInfo()
        val subtype = SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(Locale.ENGLISH, "qwerty", true)
        val (kb, keys) = buildKeyboard(editorInfo, subtype, KeyboardElement.ALPHABET)
        assertEquals(kb.sortedKeys.size, keys.sumOf { it.size })
    }

    @Test fun `dvorak has 4 rows`() {
        val editorInfo = EditorInfo()
        val subtype = SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(Locale.ENGLISH, "dvorak", true)
        val (_, keys) = buildKeyboard(editorInfo, subtype, KeyboardElement.ALPHABET)
        assertEquals(keys.size, 4)
    }

    @Test fun `de_DE has extra keys`() {
        val editorInfo = EditorInfo()
        val subtype = SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(Locale.GERMANY, "qwertz+", true)
        val (_, keys) = buildKeyboard(editorInfo, subtype, KeyboardElement.ALPHABET)
        assertEquals(11, keys[0].size)
        assertEquals(11, keys[1].size)
        assertEquals(10, keys[2].size)
        val (_, keys2) = buildKeyboard(editorInfo, subtype, KeyboardElement.ALPHABET_AUTOMATIC_SHIFTED)
        assertEquals(11, keys2[0].size)
        assertEquals(11, keys2[1].size)
        assertEquals(10, keys2[2].size)
    }

    @Test fun `popup key count does not depend on shift for (for simple layout)`() {
        val editorInfo = EditorInfo()
        val subtype = SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(Locale.ENGLISH, "qwerty", true)
        val (kb, keys) = buildKeyboard(editorInfo, subtype, KeyboardElement.ALPHABET)
        val (kb2, keys2) = buildKeyboard(editorInfo, subtype, KeyboardElement.ALPHABET_AUTOMATIC_SHIFTED)
        assertEquals(kb.sortedKeys.size, kb2.sortedKeys.size)
        keys.forEachIndexed { i, kpList -> kpList.forEachIndexed { j, kp ->
            assertEquals(kp.mPopupKeys?.size, keys2[i][j].mPopupKeys?.size)
        } }
        kb.sortedKeys.forEachIndexed { index, key ->
            assertEquals(key.popupKeys?.size, kb2.sortedKeys[index].popupKeys?.size)
        }
    }

    @Test fun parseExistingLayouts() {
        val dir = File("src/main/assets/layouts")
        dir.walk().forEach {
            if (it.isDirectory) return@forEach
            val content = it.readText()
            val data = if (it.name.endsWith(".json"))
                LayoutParser.parseJsonString(content)
            else LayoutParser.parseSimpleString(content)
            data.flatten().forEach { key -> key.compute(params)?.toKeyParams(params) }
        }
    }

    @Test fun simpleWithLabelPopupHasCode() {
        val keys = LayoutParser.parseSimpleString("""
            a symbol_alpha
            b esc
            c undo

            d $$$
            e $$$1
            f blah
            tab timestamp
    """).flatMap { row -> row.mapNotNull { it.compute(params)?.toKeyParams(params) } }
        assertEquals("?123", keys[0].mPopupKeys?.first()?.mLabel)
        assertEquals(KeyCode.SYMBOL_ALPHA, keys[0].mPopupKeys?.first()?.mCode)
        assertEquals("ESC", keys[1].mPopupKeys?.first()?.mLabel)
        assertEquals(KeyCode.ESCAPE, keys[1].mPopupKeys?.first()?.mCode)
        assertEquals(null, keys[2].mPopupKeys?.first()?.mLabel)
        assertEquals("undo", keys[2].mPopupKeys?.first()?.mIconName)
        assertEquals(KeyCode.UNDO, keys[2].mPopupKeys?.first()?.mCode)
        assertEquals("$", keys[3].mPopupKeys?.first()?.mLabel)
        assertEquals('$'.code, keys[3].mPopupKeys?.first()?.mCode)
        assertEquals("£", keys[4].mPopupKeys?.first()?.mLabel)
        assertEquals('£'.code, keys[4].mPopupKeys?.first()?.mCode)
        assertEquals("blah", keys[5].mPopupKeys?.first()?.mLabel)
        assertEquals(KeyCode.MULTIPLE_CODE_POINTS, keys[5].mPopupKeys?.first()?.mCode)
        assertEquals("tab_key", keys[6].mIconName)
        assertEquals(KeyCode.TAB, keys[6].mCode)
        assertEquals("⌚", keys[6].mPopupKeys?.first()?.mLabel)
        assertEquals(KeyCode.TIMESTAMP, keys[6].mPopupKeys?.first()?.mCode)
    }

    @Test fun keyRepeatPopup() {
        val key = LayoutParser.parseJsonString("""[[{ "label": "c", "popup": {
          "main": { "code":   -11000, "label": "x" }
    } }]]""").flatMap { row -> row.mapNotNull { it.compute(params) } }.single()
        assertEquals(null, key.toKeyParams(params).mPopupKeys)
        assertEquals(1, key.toKeyParams(params).mActionFlags and 0x01 )
        val key2 = LayoutParser.parseSimpleString("a b|!code/-11000").flatMap { row -> row.mapNotNull { it.compute(params) } }.single()
        assertEquals(null, key2.toKeyParams(params).mPopupKeys)
        assertEquals(1, key2.toKeyParams(params).mActionFlags and 0x01 )
    }

    @Test fun keyRepeatKeyDoesNotWork() {
        assertFails {
            LayoutParser.parseJsonString("""[[{ "label": "c", "code":   -11000 }} }]]""")
                .flatMap { row -> row.mapNotNull { it.compute(params) } }.single()
        }
        // but unfortunately the following does work:
        LayoutParser.parseSimpleString("a|!code/-11000").flatMap { row -> row.mapNotNull { it.compute(params) } }.single()
    }

    private data class Expected(
        val code: Int,
        val label: String? = null,
        val icon: String? = null,
        val text: String? = null,
        val popups: List<Pair<String?, Int>>? = null,
        val background: Int = Key.BACKGROUND_TYPE_NORMAL
    )

    private fun assertIsExpected(json: String, expected: Expected) {
        assertAreExpected(json, listOf(expected))
    }

    private fun assertAreExpected(json: String, expected: List<Expected>) {
        val keys = LayoutParser.parseJsonString(json)
            .map { row -> row.mapNotNull { it.compute(params) } }.flatten()
        keys.forEachIndexed { index, keyData ->
            println("data: key ${keyData.label}: code ${keyData.code}, popups: ${keyData.popup.getPopupKeyLabels(params)}")
            val keyParams = keyData.toKeyParams(params)
            println("params: key ${keyParams.mLabel}: code ${keyParams.mCode}, popups: ${keyParams.mPopupKeys?.toList()}")
            assertEquals(expected[index].label, keyParams.mLabel)
            assertEquals(expected[index].icon, keyParams.mIconName)
            assertEquals(expected[index].code, keyParams.mCode)
            assertEquals(expected[index].popups, keyParams.mPopupKeys?.mapNotNull { it.mLabel to it.mCode })
            assertEquals(expected[index].text, keyParams.outputText)
            assertEquals(expected[index].background, keyParams.mBackgroundType)
            assertTrue(LayoutUtilsCustom.checkKeys(listOf(listOf(keyParams))))
        }
    }

    private fun buildKeyboard(editorInfo: EditorInfo, subtype: InputMethodSubtype, element: KeyboardElement,
        keyboardWidth: Int = 500, numberRow: Boolean = false): Pair<Keyboard, List<List<KeyParams>>> {
        val layoutParams = KeyboardLayoutSet.Params()
        val editorInfoField = KeyboardLayoutSet.Params::class.java.getDeclaredField("editorInfo").apply { isAccessible = true }
        editorInfoField.set(layoutParams, editorInfo)
        val subtypeField = KeyboardLayoutSet.Params::class.java.getDeclaredField("subtype").apply { isAccessible = true }
        subtypeField.set(layoutParams, RichInputMethodSubtype.get(subtype))
        val widthField = KeyboardLayoutSet.Params::class.java.getDeclaredField("keyboardWidth").apply { isAccessible = true }
        widthField.setInt(layoutParams, keyboardWidth)
        val heightField = KeyboardLayoutSet.Params::class.java.getDeclaredField("keyboardHeight").apply { isAccessible = true }
        heightField.setInt(layoutParams, 300)

        val keysInRowsField = KeyboardBuilder::class.java.getDeclaredField("keysInRows").apply { isAccessible = true }

        val id = KeyboardId(element, layoutParams).let { id ->
            if (numberRow) id.copy(numberRowEnabled = true).apply { this.editorInfo = editorInfo } else id
        }
        val builder = KeyboardBuilder(latinIME, KeyboardParams(UniqueKeysCache.NO_CACHE))
        builder.load(id)
        @Suppress("UNCHECKED_CAST")
        return builder.build() to keysInRowsField.get(builder) as ArrayList<ArrayList<KeyParams>>
    }

    @Test fun placeholderAsOnlyPopup() { // https://github.com/HeliBorg/HeliBoard/issues/1324
        val keyParams = LayoutParser.parseJsonString("""[[{ "label": "ক", "popup": { "relevant": [
       { "type": "placeholder" }
      ]
    } }]]""").flatMap { row -> row.mapNotNull { it.compute(params)?.toKeyParams(params) } }.single()
        assertEquals(null, keyParams.mPopupKeys)
        keyParams.mAbsoluteWidth = 1f
        keyParams.mAbsoluteHeight = 1f
        val key = keyParams.createKey()
        assertEquals(false, key.isLongPressEnabled)
    }

    @Test fun placeholderNextToOtherPopup() {
        val key = LayoutParser.parseJsonString("""[[{ "label": "ক", "popup": { "relevant": [
       { "type": "placeholder" }, { "label": "k" }
      ]
    } }]]""").flatMap { row -> row.mapNotNull { it.compute(params) } }.single()
        assertEquals(1, key.toKeyParams(params).mPopupKeys?.size)
        assertEquals("k", key.toKeyParams(params).mPopupKeys?.first()?.mLabel)
    }

}

@Implements(ProximityInfo::class)
class ShadowProximityInfo {
    @Implementation
    fun createNativeProximityInfo(tpc: TouchPositionCorrection): Long = 0
}
