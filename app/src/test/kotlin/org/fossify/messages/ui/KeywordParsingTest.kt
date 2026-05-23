package org.fossify.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordParsingTest {
    @Test
    fun normalizePlainKeywords_trimsLowercasesAndDropsEmpties() {
        val result = normalizePlainKeywords("  Sale,Offer,  ,  Promo ,SALE  ")

        assertEquals(listOf("sale", "offer", "promo", "sale"), result)
    }

    @Test
    fun appendPlainKeywords_deduplicatesAgainstExistingList() {
        val words = mutableListOf("sale")

        val added = appendPlainKeywords(" Sale, offer,SALE,  promo ", words)

        assertTrue(added)
        assertEquals(listOf("sale", "offer", "promo"), words)
    }

    @Test
    fun validateRegexPattern_rejectsInvalidPatterns() {
        val error = validateRegexPattern("(")

        assertTrue(error?.startsWith("Invalid:") == true)
    }

    @Test
    fun appendRegexPattern_trimsAndAvoidsDuplicates() {
        val patterns = mutableListOf("^sale.*$")

        val error = appendRegexPattern("  ^sale.*$  ", patterns)

        assertNull(error)
        assertEquals(listOf("^sale.*$"), patterns)
    }

    @Test
    fun appendRegexPattern_returnsNullForBlankInput() {
        val patterns = mutableListOf<String>()

        val error = appendRegexPattern("   ", patterns)

        assertNull(error)
        assertFalse(patterns.isNotEmpty())
    }
}

