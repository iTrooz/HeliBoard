// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks that paging preserves all search results in order. */
class EmojiSearchPagesTest {
    @Test fun pagesKeepOverflowAndPartialRows() {
        assertEquals(listOf((1..6).toList(), (7..12).toList(), listOf(13)),
            emojiSearchPages((1..13).toList(), columns = 3, rows = 2))
        assertEquals(listOf(emptyList()), emojiSearchPages(emptyList<Int>(), columns = 3, rows = 2))
        assertEquals(listOf(listOf(1, 2, 3), listOf(4)),
            emojiSearchPages((1..4).toList(), columns = 3, rows = 1))
    }
}
