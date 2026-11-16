// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.app.KeyguardManager
import android.content.Context
import android.os.UserManager
import android.text.InputType
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowContextImpl
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowUserManager
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Install the locked state before Robolectric starts the actual manifest Application.
@Implements(UserManager::class)
class LockedBootUserManager : ShadowUserManager() {
    @Implementation override fun isUserUnlocked() = false
}

@Implements(className = "android.app.ContextImpl", isInAndroidSdk = false)
class LockedBootContext : ShadowContextImpl() {
    @RealObject private lateinit var context: Context

    @Implementation fun getFilesDir(): File {
        val directory = Shadow.directlyOn<File>(context, "android.app.ContextImpl", "getFilesDir")
        return if (context.isDeviceProtectedStorage) directory
        else File(directory, "credential-storage-not-mounted")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [LockedBootUserManager::class, LockedBootContext::class, ShadowInputMethodService::class])
class AppDirectBootTest {
    @Test fun lockedStartupCompletesAndPasswordKeyboardAcceptsTouchInput() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(!(context.getSystemService(Context.USER_SERVICE) as UserManager).isUserUnlocked)
        // The startup log is written only after the background dictionary scan finishes.
        runBlocking {
            withTimeout(10_000) {
                while (Log.getLog(200).none { it.tag == "startup" }) delay(10)
            }
        }
        shadowOf(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceLocked(true)
        val ime = Robolectric.setupService(LatinIME::class.java)
        val switcher = KeyboardSwitcher.getInstance()
        switcher.onCreateInputView(ime, true)
        val english = SubtypeSettings.getResourceSubtypesForLocale("en-US".constructLocale()).first()
        ime.switchToSubtype(english)
        ShadowInputMethodService.reset()
        ShadowInputMethodService.currentInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val editor = EditorInfo().apply {
            inputType = ShadowInputMethodService.currentInputType
            packageName = "com.android.systemui"
        }
        ime.mHandler.onStartInput(editor, false)
        ime.mHandler.onStartInputView(editor, false)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        switcher.reloadMainKeyboard()
        val keyboard = assertNotNull(switcher.keyboard)
        val view = assertNotNull(switcher.mainKeyboardView)
        "abc".forEachIndexed { index, char ->
            val key = assertNotNull(keyboard.getKey(char.code))
            val time = 1_000L + index * 100L
            for ((action, eventTime) in listOf(MotionEvent.ACTION_DOWN to time, MotionEvent.ACTION_UP to time + 40)) {
                val event = MotionEvent.obtain(time, eventTime, action,
                    (key.x + key.width / 2).toFloat(), (key.y + key.height / 2).toFloat(), 0)
                view.onTouchEvent(event)
                event.recycle()
            }
        }
        assertEquals("abc", ShadowInputMethodService.text)
        assertEquals("", ShadowInputMethodService.composingText)
    }
}
