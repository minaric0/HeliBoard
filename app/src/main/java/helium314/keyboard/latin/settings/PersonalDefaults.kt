// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.latin.common.Constants.Separators

/** Initial language preset for this personal fork. Existing and restored settings take precedence. */
object PersonalDefaults {
    private const val PROFILE_VERSION = "personal_defaults_version"
    private const val KOREAN = "ko${Separators.SET}CombiningRules=hangul_single_vowel,KeyboardLayoutSet=MAIN:korean_single_vowel,SupportTouchPositionCorrection"
    private const val ENGLISH = "en-US${Separators.SET}SupportTouchPositionCorrection,TrySuppressingImeSwitcher"

    fun applyToNewInstall(prefs: SharedPreferences) {
        if (prefs.contains(PROFILE_VERSION)) return
        val isNewInstall = prefs.all.isEmpty()
        prefs.edit {
            if (isNewInstall) {
                putString(Settings.PREF_ENABLED_SUBTYPES, "$ENGLISH${Separators.SETS}$KOREAN")
                putString(Settings.PREF_SELECTED_SUBTYPE, KOREAN)
            }
            putInt(PROFILE_VERSION, 1)
        }
    }
}
