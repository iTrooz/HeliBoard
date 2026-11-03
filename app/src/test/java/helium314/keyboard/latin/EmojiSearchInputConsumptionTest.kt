// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.keyboard.emoji.EmojiSearchInputConsumption
import helium314.keyboard.keyboard.emoji.EmojiSearchPanel
import helium314.keyboard.keyboard.emoji.EmojiSearchSession
import helium314.keyboard.keyboard.emoji.EmojiSearchTarget
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests editor notification and restoration across search lifetimes. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowLocaleManagerCompat::class, ShadowInputMethodManager2::class,
    ShadowInputMethodService::class, ShadowKeyboardSwitcher::class, ShadowHandler::class, ShadowFacilitator2::class])
class EmojiSearchInputConsumptionTest {
    @Test fun closeRestoresTheCapturedConnectionOnlyOnce() {
        val connection = Mockito.mock(InputConnection::class.java)
        val consumption = EmojiSearchInputConsumption(connection) { true }
        consumption.open()
        consumption.open()
        consumption.close()
        consumption.close()
        val order = Mockito.inOrder(connection)
        order.verify(connection).setImeConsumesInput(true)
        order.verify(connection).setImeConsumesInput(false)
        Mockito.verifyNoMoreInteractions(connection)
    }

    @Test fun changedEditorDoesNotReceiveRestorationEvenWithReusedConnection() {
        val connection = Mockito.mock(InputConnection::class.java)
        val first = EditorInfo().apply { packageName = "app"; fieldId = 1 }
        var current = first
        val target = EmojiSearchTarget(connection, first, first.packageName, first.fieldId)
        val consumption = EmojiSearchInputConsumption(connection) {
            target.matches(connection, current, current.packageName, current.fieldId)
        }
        consumption.open()
        current = EditorInfo().apply { packageName = "app"; fieldId = 2 }
        consumption.close()
        Mockito.verify(connection).setImeConsumesInput(true)
        Mockito.verifyNoMoreInteractions(connection)
    }

    @Test fun failedNotificationStillAttemptsRestoration() {
        val connection = Mockito.mock(InputConnection::class.java)
        Mockito.`when`(connection.setImeConsumesInput(true)).thenThrow(IllegalStateException())
        val consumption = EmojiSearchInputConsumption(connection) { true }
        consumption.open()
        consumption.close()
        Mockito.verify(connection).setImeConsumesInput(false)
    }

    @Test fun editorSwitchLifecycleRestoresBeforeTheTargetChanges() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        val connection = Mockito.mock(InputConnection::class.java)
        val consumption = EmojiSearchInputConsumption(connection) { true }
        consumption.open()
        setField(ime, "mEmojiSearchInputConsumption", consumption)
        setField(ime, "mEmojiSearchPanel", Mockito.mock(EmojiSearchPanel::class.java))
        setField(ime, "mEmojiSearchSession", EmojiSearchSession {})
        ime.onStartInput(EditorInfo(), false)
        Mockito.verify(connection).setImeConsumesInput(false)
        ime.closeEmojiSearch()
        Mockito.verify(connection, Mockito.times(1)).setImeConsumesInput(false)
    }

    @Test fun sameEditorRestartRestoresOnTheNewConnection() {
        val ime = Mockito.spy(Robolectric.setupService(LatinIME::class.java))
        val original = Mockito.mock(InputConnection::class.java)
        val restarted = Mockito.mock(InputConnection::class.java)
        val oldEditor = EditorInfo().apply { packageName = "app"; fieldId = 7 }
        val newEditor = EditorInfo().apply { packageName = "app"; fieldId = 7 }
        var current = true
        val consumption = EmojiSearchInputConsumption(original) { current }
        consumption.open()
        current = false
        Mockito.doReturn(restarted).`when`(ime).currentInputConnection
        Mockito.doReturn(newEditor).`when`(ime).currentInputEditorInfo
        setField(ime, "mEmojiSearchInputConsumption", consumption)
        setField(ime, "mEmojiSearchTarget", EmojiSearchTarget(original, oldEditor, "app", 7))
        setField(ime, "mEmojiSearchPanel", Mockito.mock(EmojiSearchPanel::class.java))
        setField(ime, "mEmojiSearchSession", EmojiSearchSession {})

        ime.onStartInput(newEditor, true)
        Mockito.verify(original).setImeConsumesInput(true)
        Mockito.verifyNoMoreInteractions(original)
        Mockito.verify(restarted).setImeConsumesInput(false)
        Mockito.verifyNoMoreInteractions(restarted)
    }

    @Test fun restartWithDifferentEditorMetadataDoesNotNotifyNewConnection() {
        val ime = Mockito.spy(Robolectric.setupService(LatinIME::class.java))
        val original = Mockito.mock(InputConnection::class.java)
        val other = Mockito.mock(InputConnection::class.java)
        val oldEditor = EditorInfo().apply { packageName = "app"; fieldId = 7 }
        var current = true
        val consumption = EmojiSearchInputConsumption(original) { current }
        consumption.open()
        current = false
        Mockito.doReturn(other).`when`(ime).currentInputConnection
        setField(ime, "mEmojiSearchInputConsumption", consumption)
        setField(ime, "mEmojiSearchTarget", EmojiSearchTarget(original, oldEditor, "app", 7))
        setField(ime, "mEmojiSearchPanel", Mockito.mock(EmojiSearchPanel::class.java))
        setField(ime, "mEmojiSearchSession", EmojiSearchSession {})

        ime.onStartInput(EditorInfo().apply { packageName = "app"; fieldId = 8 }, true)
        Mockito.verifyNoInteractions(other)
        Mockito.verify(original).setImeConsumesInput(true)
        Mockito.verifyNoMoreInteractions(original)
    }

    @Test
    fun olderAndroidDoesNotNotifyTheEditor() {
        val connection = Mockito.mock(InputConnection::class.java)
        val consumption = EmojiSearchInputConsumption(connection, sdkVersion = 30) { true }
        consumption.open()
        consumption.closeForRestart(connection)
        consumption.close()
        Mockito.verifyNoInteractions(connection)
    }

    /** Installs a search session without constructing its dictionary-backed panel. */
    private fun setField(ime: LatinIME, name: String, value: Any) {
        LatinIME::class.java.getDeclaredField(name).apply { isAccessible = true }.set(ime, value)
    }
}
