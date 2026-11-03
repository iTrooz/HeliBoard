// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.view.KeyEvent
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.keyboard.emoji.EmojiSearchInput
import helium314.keyboard.keyboard.emoji.EmojiSearchPanel
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies mid-query edits do not affect the host editor. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowLocaleManagerCompat::class, ShadowInputMethodManager2::class,
    ShadowInputMethodService::class, ShadowKeyboardSwitcher::class, ShadowHandler::class, ShadowFacilitator2::class])
class EmojiSearchCursorRoutingTest {
    @Test fun insertionAndDeletionAtQueryCursorLeaveHostSelectionUntouched() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        ShadowInputMethodService.reset()
        ShadowInputMethodService.text = "host selection"
        ShadowInputMethodService.selectionStart = 5
        ShadowInputMethodService.selectionEnd = 14
        val input = EmojiSearchInput("frog") {}
        input.setSelection(2, 2)
        setField(ime, "mEmojiSearchInput", input)
        setField(ime, "mEmojiSearchPanel", Mockito.mock(EmojiSearchPanel::class.java))

        ime.onTextInput("x")
        assertEquals("frxog", input.text)
        ime.onKeyDown(KeyEvent.KEYCODE_DEL, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        assertEquals("frog", input.text)
        assertEquals(2, input.selectionEnd)
        assertEquals("host selection", ShadowInputMethodService.text)
        assertEquals(5, ShadowInputMethodService.selectionStart)
        assertEquals(14, ShadowInputMethodService.selectionEnd)
    }

    /** Installs search collaborators without constructing the dictionary-backed panel. */
    private fun setField(ime: LatinIME, name: String, value: Any) {
        LatinIME::class.java.getDeclaredField(name).apply { isAccessible = true }.set(ime, value)
    }
}
