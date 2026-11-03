// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.view.KeyEvent
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.keyboard.emoji.EmojiSearchInput
import helium314.keyboard.keyboard.emoji.EmojiSearchPanel
import helium314.keyboard.keyboard.emoji.EmojiSearchSession
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises IME search routing against the simulated host input connection. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowInputMethodService::class,
    ShadowKeyboardSwitcher::class,
    ShadowHandler::class,
    ShadowFacilitator2::class,
])
class EmojiSearchRoutingIntegrationTest {
    private val ime = Robolectric.setupService(LatinIME::class.java)
    private val changes = mutableListOf<String>()
    private lateinit var input: EmojiSearchInput
    private lateinit var session: EmojiSearchSession

    /** Installs search state without constructing the dictionary-backed panel. */
    @BeforeTest
    fun setUp() {
        ShadowInputMethodService.reset()
        ime.prefs().edit { clear() }
        ShadowInputMethodService.text = "before selected after"
        ShadowInputMethodService.selectionStart = 7
        ShadowInputMethodService.selectionEnd = 15

        input = EmojiSearchInput("") { changes.add(it) }
        session = EmojiSearchSession { emoji -> ime.currentInputConnection.commitText(emoji, 1) }
        setField("mEmojiSearchPanel", Mockito.mock(EmojiSearchPanel::class.java))
        setField("mEmojiSearchInput", input)
        setField("mEmojiSearchSession", session)
    }

    /** Query input, deletion and closing must not change the selected host text. */
    @Test
    fun queryInputAndBackStayOutOfHostEditor() {
        ime.onTextInput("frog")
        assertTrue(ime.onKeyDown(KeyEvent.KEYCODE_DEL, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)))
        assertTrue(ime.onKeyUp(KeyEvent.KEYCODE_DEL, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)))
        assertEquals("fro", input.text)
        assertEquals(listOf("frog", "fro"), changes)
        assertHostSelectionUnchanged()

        assertTrue(ime.onKeyDown(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        assertFalse(ime.isEmojiSearch)
        assertTrue(ime.onKeyUp(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
        assertHostSelectionUnchanged()
    }

    /** Multiple selections commit to the host, while a closed session rejects stale callbacks. */
    @Test
    fun repeatedSelectionAndStaleCallback() {
        session.select("🐸")
        assertEquals("before 🐸 after", ShadowInputMethodService.text)
        assertTrue(ime.isEmojiSearch)
        session.select("🐕")
        assertEquals("before 🐸🐕 after", ShadowInputMethodService.text)

        ime.closeEmojiSearch()
        session.select("🐈")
        assertEquals("before 🐸🐕 after", ShadowInputMethodService.text)
        assertFalse(ime.isEmojiSearch)
    }

    /** Starting a different editor closes search before a late selection can reach it. */
    @Test
    fun changingEditorClosesSearchAndBlocksLateSelection() {
        ime.onStartInput(android.view.inputmethod.EditorInfo(), false)
        assertFalse(ime.isEmojiSearch)
        ShadowInputMethodService.text = "other editor"
        ShadowInputMethodService.selectionStart = 12
        ShadowInputMethodService.selectionEnd = 12
        session.select("🐸")
        assertEquals("other editor", ShadowInputMethodService.text)
    }

    /** Verifies both text and selection in the host field. */
    private fun assertHostSelectionUnchanged() {
        assertEquals("before selected after", ShadowInputMethodService.text)
        assertEquals(7, ShadowInputMethodService.selectionStart)
        assertEquals(15, ShadowInputMethodService.selectionEnd)
    }

    /** Installs the search collaborators while leaving routing in the real IME. */
    private fun setField(name: String, value: Any) {
        LatinIME::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(ime, value)
        }
    }
}
