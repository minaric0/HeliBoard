// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.settings.PersonalDefaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.LayoutType
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PersonalDefaultsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("personal_defaults_test", Context.MODE_PRIVATE)

    @Test fun newInstallStartsWithKoreanAndEnglish() {
        prefs.edit().clear().commit()
        PersonalDefaults.applyToNewInstall(prefs)
        val selected = prefs.getString(Settings.PREF_SELECTED_SUBTYPE, "")!!.toSettingsSubtype()
        assertEquals("ko", selected.locale.language)
        assertEquals("korean_single_vowel", selected.layoutName(LayoutType.MAIN))
        val enabled = prefs.getString(Settings.PREF_ENABLED_SUBTYPES, "")!!.split(';').map { it.toSettingsSubtype() }
        assertEquals(setOf("en", "ko"), enabled.map { it.locale.language }.toSet())
    }

    @Test fun updatesAndRestoresKeepUserSettings() {
        prefs.edit().clear().putString(Settings.PREF_SELECTED_SUBTYPE, "en-US§SupportTouchPositionCorrection,TrySuppressingImeSwitcher").commit()
        PersonalDefaults.applyToNewInstall(prefs)
        assertTrue(prefs.getString(Settings.PREF_SELECTED_SUBTYPE, "")!!.startsWith("en-US"))
        assertTrue(!prefs.contains(Settings.PREF_ENABLED_SUBTYPES))
    }

    @Test fun reloadingDoesNotReenableRemovedLanguages() {
        prefs.edit().clear().commit()
        PersonalDefaults.applyToNewInstall(prefs)
        prefs.edit().putString(Settings.PREF_ENABLED_SUBTYPES, "").commit()
        PersonalDefaults.applyToNewInstall(prefs)
        assertEquals("", prefs.getString(Settings.PREF_ENABLED_SUBTYPES, null))
    }
}
