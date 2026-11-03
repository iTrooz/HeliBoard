// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests selection delivery and cancellation across editor lifetimes. */
class EmojiSearchSessionTest {
    @Test fun repeatedSelectionsStayInTheOriginalSession() {
        val firstEditor = mutableListOf<String>()
        val session = EmojiSearchSession { firstEditor.add(it) }
        session.select("🐸")
        session.select("🐈")
        session.close()
        val secondEditor = mutableListOf<String>()
        val nextSession = EmojiSearchSession { secondEditor.add(it) }
        session.select("🐕")
        nextSession.select("🐝")
        assertEquals(listOf("🐸", "🐈"), firstEditor)
        assertEquals(listOf("🐝"), secondEditor)
    }

    @Test fun changedTargetPermanentlyRejectsSelections() {
        val original = Any()
        var current = original
        val committed = mutableListOf<String>()
        val session = EmojiSearchSession({ current === original }) { committed.add(it) }
        session.select("🐸")
        current = Any()
        session.select("🐕")
        current = original
        session.select("🐈")
        assertEquals(listOf("🐸"), committed)
    }

    @Test fun targetRequiresOriginalConnectionAndEditorAndUnchangedMetadata() {
        val connection = Any()
        val editor = Any()
        val target = EmojiSearchTarget(connection, editor, "app", 42)
        assertTrue(target.matches(connection, editor, "app", 42))
        assertFalse(target.matches(Any(), editor, "app", 42))
        assertFalse(target.matches(connection, Any(), "app", 42))
        assertFalse(target.matches(connection, editor, "other", 42))
        assertFalse(target.matches(connection, editor, "app", 43))
        assertFalse(target.matches(null, null, null, 42))
    }
}
