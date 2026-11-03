// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.view.Window
import android.view.WindowManager
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/** Verifies that search only borrows the dim state of its original window. */
@RunWith(RobolectricTestRunner::class)
class EmojiSearchWindowDimTest {
    @Test
    fun restoresPreviouslyUndimmedWindowOnlyOnce() {
        val window = mock(Window::class.java)
        val attributes = WindowManager.LayoutParams().apply { dimAmount = 0.7f }
        `when`(window.attributes).thenReturn(attributes)
        val dim = EmojiSearchWindowDim()

        dim.open(window)
        dim.open(window)
        dim.close()
        dim.close()

        verify(window).setDimAmount(0.25f)
        verify(window).setDimAmount(0.7f)
        verify(window).attributes
        verify(window).addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        verify(window).clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        verifyNoMoreInteractions(window)
    }

    @Test
    fun restoresExistingDimFlagAndAmount() {
        val window = mock(Window::class.java)
        val attributes = WindowManager.LayoutParams().apply {
            flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND
            dimAmount = 0.45f
        }
        `when`(window.attributes).thenReturn(attributes)
        val dim = EmojiSearchWindowDim()

        dim.open(window)
        dim.close()

        verify(window).setDimAmount(0.45f)
        verify(window, times(2)).addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        verify(window, never()).clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }
}
