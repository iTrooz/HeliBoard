// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.view.KeyEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode

/** Edits a keyboard-owned query without sending input to the current editor. Offsets are UTF-16. */
class EmojiSearchInput(initialText: String = "", private val onTextChanged: (String) -> Unit) {
    enum class Result { HANDLED, DONE, UNHANDLED }

    var text: String = initialText
        private set
    var selectionStart: Int = initialText.length
        private set
    var selectionEnd: Int = initialText.length
        private set

    /** Sets the selection, clamping offsets and avoiding splitting surrogate pairs. */
    fun setSelection(start: Int, end: Int) {
        selectionStart = boundary(start)
        selectionEnd = boundary(end)
    }

    /** Replaces the selection with software text; returns whether it was consumed. */
    fun onTextInput(value: String?): Boolean {
        if (value == null) return false
        replaceSelection(value)
        return true
    }

    /** Routes software key codes; unrelated keyboard controls remain available to the caller. */
    fun onCodeInput(code: Int): Result = when (code) {
        KeyCode.DELETE -> handled { deleteBackward() }
        KeyCode.ARROW_LEFT -> handled { moveCursor(false) }
        KeyCode.ARROW_RIGHT -> handled { moveCursor(true) }
        KeyCode.MOVE_START_OF_LINE -> handled { setSelection(0, 0) }
        KeyCode.MOVE_END_OF_LINE -> handled { setSelection(text.length, text.length) }
        10, 13 -> Result.DONE
        else -> if (Character.isValidCodePoint(code) && !Character.isISOControl(code) &&
            code !in 0xD800..0xDFFF) {
            replaceSelection(String(Character.toChars(code)))
            Result.HANDLED
        } else Result.UNHANDLED
    }

    /** Routes hardware key-down events. Key-up events and modified shortcuts are not consumed. */
    fun onKeyDown(event: KeyEvent): Result {
        if (event.action != KeyEvent.ACTION_DOWN || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed)
            return Result.UNHANDLED
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> handled { deleteBackward() }
            KeyEvent.KEYCODE_FORWARD_DEL -> handled { deleteForward() }
            KeyEvent.KEYCODE_DPAD_LEFT -> handled { moveCursor(false) }
            KeyEvent.KEYCODE_DPAD_RIGHT -> handled { moveCursor(true) }
            KeyEvent.KEYCODE_MOVE_HOME -> handled { setSelection(0, 0) }
            KeyEvent.KEYCODE_MOVE_END -> handled { setSelection(text.length, text.length) }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> Result.DONE
            else -> onCodeInput(event.unicodeChar)
        }
    }

    /** Clears the query and notifies the listener only if its text changed. */
    fun clear() {
        setSelection(0, text.length)
        replaceSelection("")
    }

    private fun handled(action: () -> Unit): Result {
        action()
        return Result.HANDLED
    }

    private fun boundary(offset: Int): Int {
        val index = offset.coerceIn(0, text.length)
        return if (index > 0 && index < text.length && Character.isHighSurrogate(text[index - 1]) &&
            Character.isLowSurrogate(text[index])) index - 1 else index
    }

    private fun replaceSelection(value: String) {
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)
        val updated = text.replaceRange(start, end, value)
        selectionStart = start + value.length
        selectionEnd = selectionStart
        if (updated != text) {
            text = updated
            onTextChanged(text)
        }
    }

    private fun deleteBackward() {
        if (selectionStart == selectionEnd && selectionStart > 0)
            selectionStart = text.offsetByCodePoints(selectionStart, -1)
        replaceSelection("")
    }

    private fun deleteForward() {
        if (selectionStart == selectionEnd && selectionEnd < text.length)
            selectionEnd = text.offsetByCodePoints(selectionEnd, 1)
        replaceSelection("")
    }

    private fun moveCursor(forward: Boolean) {
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)
        val position = if (start != end) {
            if (forward) end else start
        } else if (forward && end < text.length) {
            text.offsetByCodePoints(end, 1)
        } else if (!forward && start > 0) {
            text.offsetByCodePoints(start, -1)
        } else start
        setSelection(position, position)
    }
}
