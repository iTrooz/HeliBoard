// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.suggestions.SuggestedWordInfo
import helium314.keyboard.latin.suggestions.SuggestedWords
import helium314.keyboard.latin.suggestions.SuggestionResults
import org.mockito.Mockito
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests language selection, dictionary lifetime and merged search results. */
class EmojiDictionaryFacilitatorTest {
    @Test fun reloadsOnSecondaryChangesAndClosesOldDictionaries() {
        val loaded = mutableListOf<Locale>()
        val dictionaries = mutableListOf<SingleDictionaryFacilitator>()
        val facilitator = EmojiDictionaryFacilitator { locale ->
            loaded.add(locale)
            if (locale == Locale.ENGLISH) null else Mockito.mock(SingleDictionaryFacilitator::class.java).also { dictionaries.add(it) }
        }

        assertTrue(facilitator.update(Locale.ENGLISH, listOf(Locale.FRENCH, Locale.FRENCH, Locale.ENGLISH)))
        assertEquals(listOf(Locale.ENGLISH, Locale.FRENCH), loaded)
        assertFalse(facilitator.isEmpty)
        assertFalse(facilitator.update(Locale.ENGLISH, listOf(Locale.FRENCH)))
        assertTrue(facilitator.update(Locale.ENGLISH, listOf(Locale.GERMAN)))
        Mockito.verify(dictionaries[0]).closeDictionaries()
        assertEquals(listOf(Locale.ENGLISH, Locale.FRENCH, Locale.ENGLISH, Locale.GERMAN), loaded)
        facilitator.closeDictionaries()
        Mockito.verify(dictionaries[1]).closeDictionaries()
    }

    @Test fun mergesResultsByBestScoreWithoutDuplicates() {
        val primary = Mockito.mock(SingleDictionaryFacilitator::class.java)
        val secondary = Mockito.mock(SingleDictionaryFacilitator::class.java)
        val words = listOf("cat")
        Mockito.`when`(primary.getSuggestions("cat")).thenReturn(results("🐈" to 10, "🐕" to 20))
        Mockito.`when`(secondary.getSuggestions("cat")).thenReturn(results("🐈" to 30, "🐦" to 15))
        val facilitator = EmojiDictionaryFacilitator { if (it == Locale.ENGLISH) primary else secondary }
        facilitator.update(Locale.ENGLISH, listOf(Locale.FRENCH))

        assertEquals(listOf("🐈" to 30, "🐕" to 20, "🐦" to 15),
            facilitator.getSuggestions(words).map { it.word to it.score })
    }

    @Test fun combinesMatchesAcrossLanguagesForMultiwordQueries() {
        val primary = Mockito.mock(SingleDictionaryFacilitator::class.java)
        val secondary = Mockito.mock(SingleDictionaryFacilitator::class.java)
        Mockito.`when`(primary.getSuggestions("cat")).thenReturn(results("🐈" to 100, "🐕" to 90))
        Mockito.`when`(secondary.getSuggestions("cat")).thenReturn(results("🐈" to 80))
        Mockito.`when`(primary.getSuggestions("chat")).thenReturn(results())
        Mockito.`when`(secondary.getSuggestions("chat")).thenReturn(results("🐈" to 60))
        val facilitator = EmojiDictionaryFacilitator { if (it == Locale.ENGLISH) primary else secondary }
        facilitator.update(Locale.ENGLISH, listOf(Locale.FRENCH))

        val results = facilitator.getSuggestions(listOf("cat", "chat")).toList()
        assertEquals(listOf("🐈", "🐕"), results.map { it.word })
        assertEquals(80, results[0].mScore)
        assertEquals(((90L - Int.MIN_VALUE) / 2 + Int.MIN_VALUE).toInt(), results[1].mScore)
    }

    @Test fun retriesMissingDictionariesAndReloadsAfterClose() {
        val dictionary = Mockito.mock(SingleDictionaryFacilitator::class.java)
        var available = false
        val facilitator = EmojiDictionaryFacilitator { if (available) dictionary else null }

        assertTrue(facilitator.update(Locale.ENGLISH, emptyList()))
        assertTrue(facilitator.isEmpty)
        available = true
        assertTrue(facilitator.update(Locale.ENGLISH, emptyList()))
        assertFalse(facilitator.isEmpty)
        facilitator.closeDictionaries()
        assertTrue(facilitator.update(Locale.ENGLISH, emptyList()))
        Mockito.verify(dictionary).closeDictionaries()
    }

    // Build scored search results without opening native dictionaries.
    private fun results(vararg entries: Pair<String, Int>): SuggestionResults =
        SuggestionResults(SuggestedWords.MAX_SUGGESTIONS, NgramContext.EMPTY_PREV_WORDS_INFO, false).apply {
            entries.forEach { (word, score) ->
                add(SuggestedWordInfo(word, "", score, SuggestedWordInfo.KIND_CORRECTION,
                    Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
            }
        }
}
