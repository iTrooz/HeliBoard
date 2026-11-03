// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.TextLayoutMode
import java.util.concurrent.TimeUnit

/** Tests cursor positioning without making the query view an editor. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@TextLayoutMode(TextLayoutMode.Mode.REALISTIC)
class EmojiSearchQueryViewTest {
    @Test fun tappingTextMovesTheModelCursorAndEditsAtThatPosition() {
        val view = newView()
        val input = EmojiSearchInput("a🐸bc") { view.text = it }
        view.text = input.text
        layout(view, 250)
        view.onCursorTap = { input.setSelection(it, it); view.setCursor(input.selectionEnd) }
        val x = view.compoundPaddingLeft + view.layout.getPrimaryHorizontal(3) - view.scrollX
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, 20f, 0)
        val up = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, x, 20f, 0)
        view.onTouchEvent(down)
        view.onTouchEvent(up)
        down.recycle()
        up.recycle()
        assertEquals(3, input.selectionEnd)
        input.onTextInput("!")
        input.onCodeInput(KeyCode.DELETE)
        assertEquals("a🐸bc", input.text)
        assertEquals(3, input.selectionEnd)
        assertFalse(view.isFocusable)
        assertFalse(view.isFocused)
    }

    @Test fun longQueryScrollsToTheCursorAndVisualMovementUsesLayout() {
        val view = newView()
        view.text = "a long query with enough characters to exceed the available width"
        layout(view, 100)
        view.setCursor(view.text.length)
        assertTrue(view.scrollX > 0)
        val previous = view.cursorToSide(view.text.length, false)
        assertTrue(previous < view.text.length)
        view.setCursor(0)
        assertEquals(0, view.scrollX)
    }

    @Test fun rtlArrowMovementUsesVisualRatherThanLogicalOrder() {
        val view = newView()
        view.text = "אבגד"
        layout(view, 150)
        val start = 2
        val left = view.cursorToSide(start, false)
        val right = view.cursorToSide(start, true)
        assertTrue(left > start)
        assertTrue(right < start)
    }

    @Test fun dragAndCancellationDoNotMoveTheCursor() {
        val view = newView()
        view.text = "search"
        layout(view, 250)
        val taps = mutableListOf<Int>()
        view.onCursorTap = { taps.add(it) }
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
        sendTouch(view, MotionEvent.ACTION_DOWN, 10f, 20f)
        sendTouch(view, MotionEvent.ACTION_MOVE, 10f + slop + 1, 20f)
        sendTouch(view, MotionEvent.ACTION_UP, 10f, 20f)
        sendTouch(view, MotionEvent.ACTION_DOWN, 10f, 20f)
        sendTouch(view, MotionEvent.ACTION_CANCEL, 10f, 20f)
        sendTouch(view, MotionEvent.ACTION_UP, 10f, 20f)
        assertTrue(taps.isEmpty())
        sendTouch(view, MotionEvent.ACTION_DOWN, 10f, 20f)
        sendTouch(view, MotionEvent.ACTION_UP, 10f, 20f)
        assertEquals(1, taps.size)
    }

    @Test fun missingOrStaleLayoutMovesByWholeCodePoints() {
        val view = newView()
        view.text = "a🐸b"
        assertEquals(3, view.cursorToSide(1, true))
        assertEquals(1, view.cursorToSide(3, false))
        layout(view, 250)
        view.text = "a🐸bc"
        assertEquals(4, view.cursorToSide(3, true))
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    fun cursorBlinkRestartsOnInteractionAndStopsWhenHiddenOrDetached() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val parent = FrameLayout(controller.get())
        controller.get().setContentView(parent)
        val view = EmojiSearchQueryView(controller.get())
        layout(view, 250)
        parent.addView(view, FrameLayout.LayoutParams(250, 40))
        val looper = shadowOf(Looper.getMainLooper())
        val cursorVisible = EmojiSearchQueryView::class.java.getDeclaredField("cursorVisible").apply { isAccessible = true }
        val blink = EmojiSearchQueryView::class.java.getDeclaredField("blink").apply { isAccessible = true }.get(view) as Runnable
        val handler = view.handler
        fun visible() = cursorVisible.getBoolean(view)

        try {
            assertTrue(visible())
            looper.idleFor(500, TimeUnit.MILLISECONDS)
            assertFalse(visible())
            view.setCursor(0)
            assertTrue(visible())
            looper.idleFor(499, TimeUnit.MILLISECONDS)
            assertTrue(visible())
            looper.idleFor(1, TimeUnit.MILLISECONDS)
            assertFalse(visible())
            sendTouch(view, MotionEvent.ACTION_DOWN, 10f, 20f)
            sendTouch(view, MotionEvent.ACTION_UP, 10f, 20f)
            assertTrue(visible())
            parent.visibility = View.GONE
            assertFalse(handler.hasCallbacks(blink))
            looper.idleFor(1500, TimeUnit.MILLISECONDS)
            assertTrue(visible())
            parent.visibility = View.VISIBLE
            looper.idleFor(500, TimeUnit.MILLISECONDS)
            assertFalse(visible())
            assertTrue(handler.hasCallbacks(blink))
            parent.removeView(view)
            val visibleAfterDetach = visible()
            assertFalse(handler.hasCallbacks(blink))
            looper.idleFor(1500, TimeUnit.MILLISECONDS)
            assertEquals(visibleAfterDetach, visible())
            parent.addView(view)
            assertTrue(visible())
            looper.idleFor(500, TimeUnit.MILLISECONDS)
            assertFalse(visible())
        } finally {
            parent.removeView(view)
            controller.pause().stop().destroy()
        }
    }

    /** Uses the same text size as the search panel. */
    private fun newView() = EmojiSearchQueryView(ApplicationProvider.getApplicationContext()).apply {
        textSize = 16f
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    }

    /** Dispatches a single gesture event to the display. */
    private fun sendTouch(view: View, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0, 1, action, x, y, 0)
        view.onTouchEvent(event)
        event.recycle()
    }

    /** Measures and lays out the query display at a fixed size. */
    private fun layout(view: View, width: Int) {
        view.layoutParams = ViewGroup.LayoutParams(width, 40)
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, 40)
    }
}
