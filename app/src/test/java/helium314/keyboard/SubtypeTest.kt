package helium314.keyboard

import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.LocaleKeyboardInfos
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.event.HangulEventDecoder
import helium314.keyboard.event.Event
import android.view.KeyEvent
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.PersonalDefaults
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.POPUP_KEYS_LAYOUT
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowInputMethodManager2::class
])
class SubtypeTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val params = KeyboardParams()

    @BeforeTest fun resetEnabledSubtypes() {
        latinIME.prefs().edit().clear().commit()
        SubtypeSettings.reloadEnabledSubtypes(latinIME)
    }

    init {
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
        params.mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardElement.ALPHABET)
        params.mPopupKeyOrder.add(POPUP_KEYS_LAYOUT)
        LocaleKeyboardInfos.addLocaleKeyTextsToParams(latinIME, params, LocaleKeyboardInfos.POPUP_KEYS_NORMAL)
    }

    @Test fun emptyAdditionalSubtypesResultsInEmptyList() {
        // avoid issues where empty string results in additional subtype for undefined locale
        val prefs = latinIME.prefs()
        prefs.edit().putString(Settings.PREF_ADDITIONAL_SUBTYPES, "").apply()
        assertTrue(SubtypeSettings.getAdditionalSubtypes().isEmpty())
        val from = SubtypeSettings.getResourceSubtypesForLocale("es".constructLocale()).first()

        // no change, and "changed" subtype actually is resource subtype -> still expect empty list
        SubtypeUtilsAdditional.changeAdditionalSubtype(from.toSettingsSubtype(), from.toSettingsSubtype(), latinIME)
        assertEquals(emptyList(), SubtypeSettings.getAdditionalSubtypes().map { it.toSettingsSubtype() })
    }

    @Test fun personalPresetResolvesBuiltInKoreanAndEnglish() {
        val prefs = latinIME.prefs()
        prefs.edit().clear().commit()
        PersonalDefaults.applyToNewInstall(prefs)
        SubtypeSettings.reloadEnabledSubtypes(latinIME)
        assertEquals(setOf("ko", "en"), SubtypeSettings.getEnabledSubtypes().map { it.toSettingsSubtype().locale.language }.toSet())
        val selected = SubtypeSettings.getSelectedSubtype(prefs)
        assertEquals("korean_single_vowel", selected.toSettingsSubtype().mainLayoutName())
        assertEquals("hangul_single_vowel", SubtypeLocaleUtils.getCombiningRulesExtraValue(selected))
    }

    @Test fun changingMainLayoutChangesEffectiveCombiningRules() {
        val single = SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale())
            .first { it.toSettingsSubtype().mainLayoutName() == "korean_single_vowel" }.toSettingsSubtype()
        assertEquals("hangul_single_vowel", SubtypeLocaleUtils.getCombiningRulesExtraValue(single.toAdditionalSubtype()))
        val stock = single.withLayout(LayoutType.MAIN, "korean")
        assertEquals("hangul", SubtypeLocaleUtils.getCombiningRulesExtraValue(stock.toAdditionalSubtype()))
        val again = stock.withLayout(LayoutType.MAIN, "korean_single_vowel")
        assertEquals("hangul_single_vowel", SubtypeLocaleUtils.getCombiningRulesExtraValue(again.toAdditionalSubtype()))
        val custom = single.withLayout(LayoutType.MAIN, "custom_test")
        assertEquals("hangul", SubtypeLocaleUtils.getCombiningRulesExtraValue(custom.toAdditionalSubtype()))
    }

    @Test fun physicalKeyboardUsesStandardKoreanWithoutTouchTiming() {
        val single = SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale())
            .first { it.toSettingsSubtype().mainLayoutName() == "korean_single_vowel" }
        val decoded = HangulEventDecoder.decodeHardwareKeyEvent(RichInputMethodSubtype.get(single),
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R)) { Event.notHandledEvent }
        assertEquals('ㄱ'.code, decoded.codePoint)
        assertTrue(decoded.x < 0)
    }

    @Test fun subtypeStaysEnabledOnEdits() {
        val prefs = latinIME.prefs()
        prefs.edit().putString(Settings.PREF_ADDITIONAL_SUBTYPES, "").apply() // clear it for convenience

        // edit enabled resource subtype
        val from = SubtypeSettings.getResourceSubtypesForLocale("es".constructLocale()).first()
        SubtypeSettings.addEnabledSubtype(prefs, from)
        val to = from.toSettingsSubtype().withLayout(LayoutType.SYMBOLS, "symbols_arabic")
        SubtypeUtilsAdditional.changeAdditionalSubtype(from.toSettingsSubtype(), to, latinIME)
        assertEquals(to, SubtypeSettings.getEnabledSubtypes(false).single().toSettingsSubtype())

        // change the new subtype to effectively be the same as original resource subtype
        val toNew = to.withoutLayout(LayoutType.SYMBOLS)
        assertEquals(from.toSettingsSubtype(), toNew)
        SubtypeUtilsAdditional.changeAdditionalSubtype(to, toNew, latinIME)
        assertEquals(emptyList(), SubtypeSettings.getAdditionalSubtypes().map { it.toSettingsSubtype() })
        assertEquals(from.toSettingsSubtype(), SubtypeSettings.getEnabledSubtypes(false).single().toSettingsSubtype())
    }
}
