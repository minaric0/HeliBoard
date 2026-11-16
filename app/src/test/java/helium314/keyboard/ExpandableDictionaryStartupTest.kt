// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.dictionary.ExpandableDictionaryUtils
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.prefs
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExpandableDictionaryStartupTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var userManager: UserManager
    private lateinit var directory: File

    @Before fun setUp() {
        // The cached Context-owned preferences must not outlive Robolectric's storage reset.
        ReflectionHelpers.setStaticField(DeviceProtectedUtils::class.java, "prefs", null)
        context = ApplicationProvider.getApplicationContext()
        userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        shadowOf(userManager).setUserUnlocked(true)
        directory = temporary.newFolder("files")
        context.prefs().edit()
            .putBoolean(Settings.PREF_USE_CONTACTS, false)
            .putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, false)
            .putBoolean(Settings.PREF_USE_APPS, false)
            .commit()
    }

    @After fun resetCachedPreferences() {
        ReflectionHelpers.setStaticField(DeviceProtectedUtils::class.java, "prefs", null)
    }

    @Test fun lockedStartupDoesNotAccessCredentialFiles() {
        shadowOf(userManager).setUserUnlocked(false)
        val lockedContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File = error("Credential files requested before first unlock")
        }
        ExpandableDictionaryUtils.initUpToDateDicts(lockedContext)
    }

    @Test fun lockedStartupDoesNotDeleteDisabledDictionaries() {
        val contacts = dictionary("contacts.en_US.dict")
        val user = dictionary("userunigram.en_US.dict")
        val apps = dictionary("apps.en_US.dict")
        shadowOf(userManager).setUserUnlocked(false)

        ExpandableDictionaryUtils.initUpToDateDicts(withFiles(directory))

        for (file in listOf(contacts, user, apps))
            assertEquals("private dictionary", File(file, "contents").readText())
    }

    @Test fun unavailableDirectoryDoesNotCrashStartup() {
        val unavailable = File(directory, "not-mounted")

        ExpandableDictionaryUtils.initUpToDateDicts(withFiles(unavailable))

        assertFalse(unavailable.exists())
    }

    @Test fun failedListingDoesNotCrashOrOverwriteAFile() {
        val unavailable = File(directory, "not-a-directory").apply { writeText("keep") }

        ExpandableDictionaryUtils.initUpToDateDicts(withFiles(unavailable))

        assertEquals("keep", unavailable.readText())
    }

    @Test fun scanCanRunAfterFirstUnlock() {
        val contacts = dictionary("contacts.en_US.dict")
        val history = dictionary("history.en_US.dict")
        val filesContext = withFiles(directory)
        shadowOf(userManager).setUserUnlocked(false)
        ExpandableDictionaryUtils.initUpToDateDicts(filesContext)
        assertTrue(contacts.exists())

        shadowOf(userManager).setUserUnlocked(true)
        ExpandableDictionaryUtils.initUpToDateDicts(filesContext)

        assertFalse(contacts.exists())
        assertEquals("private dictionary", File(history, "contents").readText())
    }

    @Test fun unlockedScanPreservesEnabledDictionariesAndUnrelatedFiles() {
        context.prefs().edit()
            .putBoolean(Settings.PREF_USE_CONTACTS, true)
            .putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true)
            .putBoolean(Settings.PREF_USE_APPS, true)
            .commit()
        val keep = listOf(
            dictionary("contacts.en_US.dict"), dictionary("userunigram.en_US.dict"),
            dictionary("apps.en_US.dict"), dictionary("history.en_US.dict"),
            dictionary("layouts")
        )
        val file = File(directory, "regular.dict").apply { writeText("keep") }

        ExpandableDictionaryUtils.initUpToDateDicts(withFiles(directory))

        for (dir in keep) assertEquals("private dictionary", File(dir, "contents").readText())
        assertEquals("keep", file.readText())
    }

    private fun withFiles(file: File): Context = object : ContextWrapper(context) {
        override fun getFilesDir(): File = file
    }

    private fun dictionary(name: String) = File(directory, name).apply {
        mkdirs()
        File(this, "contents").writeText("private dictionary")
    }
}
