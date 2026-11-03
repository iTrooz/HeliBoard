// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.view.KeyEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Exercises query editing independently of an activity or input connection. */
@RunWith(RobolectricTestRunner::class)
class EmojiSearchInputTest {
    @Test fun softwareInputReplacesSelectionAndNotifiesOnlyOnChange() {
        val changes = mutableListOf<String>()
        val input = EmojiSearchInput("hello", changes::add)
        input.setSelection(4, 1)
        input.onTextInput("i")
        assertEquals("hio", input.text)
        assertEquals(2, input.selectionStart)
        input.onCodeInput(KeyCode.DELETE)
        input.onCodeInput('a'.code)
        input.onCodeInput(KeyCode.ARROW_LEFT)
        assertEquals(listOf("hio", "ho", "hao"), changes)
    }

    @Test fun supplementaryCharactersAreNotSplitBySelectionMovementOrDeletion() {
        val input = EmojiSearchInput("a🐸b") {}
        input.setSelection(2, 2)
        assertEquals(1, input.selectionStart)
        input.onCodeInput(KeyCode.ARROW_RIGHT)
        assertEquals(3, input.selectionStart)
        input.onCodeInput(KeyCode.DELETE)
        assertEquals("ab", input.text)
        input.onCodeInput(0x1F438)
        assertEquals("a🐸b", input.text)
    }

    @Test fun hardwareEditingAndUnsupportedKeysHaveExplicitResults() {
        val input = EmojiSearchInput("a🐸b") {}
        input.setSelection(1, 1)
        assertEquals(EmojiSearchInput.Result.HANDLED, input.onKeyDown(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL)))
        assertEquals("ab", input.text)
        assertEquals(EmojiSearchInput.Result.DONE, input.onKeyDown(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)))
        assertEquals(EmojiSearchInput.Result.UNHANDLED, input.onKeyDown(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)))
        assertEquals(EmojiSearchInput.Result.UNHANDLED, input.onCodeInput(KeyCode.SHIFT))
        assertEquals(EmojiSearchInput.Result.UNHANDLED, input.onCodeInput(0))
        input.clear()
        assertEquals("", input.text)
    }
}
