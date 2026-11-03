// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.ShadowFacilitator2
import helium314.keyboard.latin.ShadowHandler
import helium314.keyboard.latin.ShadowKeyboardSwitcher
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercises selection forwarding from a real panel's emoji page. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowInputMethodService::class,
    ShadowKeyboardSwitcher::class,
    ShadowHandler::class,
    ShadowFacilitator2::class,
])
class EmojiSearchPanelTest {
    /** Both single-code-point and composed emoji reach the installed listener. */
    @Test
    fun pageSelectionForwardsEmojiToPanelListener() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        val panel = EmojiSearchPanel(ime)
        val selected = mutableListOf<String>()
        panel.onEmojiSelected = androidx.core.util.Consumer { selected.add(it) }

        val pager = panel.getChildAt(1) as ViewPager2
        val holder = pager.adapter!!.onCreateViewHolder(FrameLayout(ime), 0)
        val page = holder.itemView as EmojiPageKeyboardView
        val callback = EmojiPageKeyboardView::class.java.getDeclaredField("mEmojiViewCallback").apply {
            isAccessible = true
        }.get(page) as EmojiViewCallback
        val params = EmojiSearchPanel::class.java.getDeclaredField("keyboardParams").apply {
            isAccessible = true
        }.get(panel) as KeyboardParams

        val frog = Key.KeyParams("🐸", 0x1F438, null, null, Key.LABEL_FLAGS_FONT_NORMAL, params).createKey()
        val family = Key.KeyParams("👨‍👩‍👧", KeyCode.MULTIPLE_CODE_POINTS, null, null, Key.LABEL_FLAGS_FONT_NORMAL, params).createKey()
        callback.onReleaseKey(frog)
        callback.onReleaseKey(family)

        assertEquals(listOf("🐸", "👨‍👩‍👧"), selected)
        panel.close()
    }
}
