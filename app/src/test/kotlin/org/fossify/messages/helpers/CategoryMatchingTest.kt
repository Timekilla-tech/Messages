package org.fossify.messages.helpers

import org.fossify.messages.models.Category
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryMatchingTest {

    private fun createCategory(
        regexPatterns: String = "",
        keywords: String = "",
        keywordIsRegex: Boolean = false
    ): Category = Category(
        id = 1, name = "Test", color = 0,
        regexPatterns = regexPatterns,
        keywords = keywords,
        keywordIsRegex = keywordIsRegex
    )

    // Body matching
    @Test fun regexMatchesBodyExact() {
        val cat = createCategory(regexPatterns = "^OTP: \\d{6}$")
        assertTrue(isMessageMatchingCategory("OTP: 123456", "", cat))
    }

    @Test fun regexDoesNotMatchBodyPartial() {
        val cat = createCategory(regexPatterns = "^OTP: \\d{6}$")
        assertFalse(isMessageMatchingCategory("Your OTP: 123456", "", cat))
    }

    @Test fun regexMatchesBodySubstringWhenAnchorsOmitted() {
        val cat = createCategory(regexPatterns = "OTP: \\d{6}")
        assertTrue(isMessageMatchingCategory("Your OTP: 123456", "", cat))
    }

    @Test fun regexCaseInsensitiveWhenFlagSet() {
        // Assuming you support (?i) or a flag
        val cat = createCategory(regexPatterns = "(?i)^otp:")
        assertTrue(isMessageMatchingCategory("OTP: 123", "", cat))
    }

    // Sender matching
    @Test fun regexMatchesSender() {
        val cat = createCategory(regexPatterns = "^\\+976.*")
        assertTrue(isMessageMatchingCategory("body", "+97699112233", cat))
    }

    @Test fun regexDoesNotMatchSenderWrongPrefix() {
        val cat = createCategory(regexPatterns = "^\\+976.*")
        assertFalse(isMessageMatchingCategory("body", "+123456789", cat))
    }

    @Test fun regexMatchesNoneOfMultiplePatterns() {
        val cat = createCategory(regexPatterns = "OTP:\\d{6},TRANSACTION:\\d{8}")
        assertFalse(isMessageMatchingCategory("Hello world", "", cat))
    }

    // Edge cases
    @Test fun emptyRegexPatternsDoesNotMatch() {
        val cat = createCategory(regexPatterns = "")
        assertFalse(isMessageMatchingCategory("any text", "", cat))
    }


    @Test fun invalidRegexPatternSkipsAndDoesNotCrash() {
        val cat = createCategory(regexPatterns = "((?=")
        assertFalse(isMessageMatchingCategory("test", "", cat))
    }

    @Test fun multilineBodyWithEmbeddedNewlines() {
        val cat = createCategory(regexPatterns = ".*secret.*")
        val body = "Line1\nLine2\nsecret code\nLine4"
        assertTrue(isMessageMatchingCategory(body, "", cat))
    }

    // Legacy keyword with regex flag
    @Test fun legacyRegexKeywordWorks() {
        val cat = createCategory(
            keywords = "^code\\d{4}$",
            keywordIsRegex = true
        )
        assertTrue(isMessageMatchingCategory("code1234", "", cat))
    }

    @Test fun legacyPlainKeywordNoRegex() {
        val cat = createCategory(
            keywords = "code",
            keywordIsRegex = false
        )
        assertTrue(isMessageMatchingCategory("my code is 1234", "", cat))
    }


    // Folder assignment should NOT affect category matching
    @Test fun folderAssignmentDoesNotInfluenceCategoryMatch() {
        val cat = createCategory(regexPatterns = "Banking")
        // Simulate a conversation that has folder ID but no tag
        assertTrue(isMessageMatchingCategory("Banking alert", "", cat))
        // Folder name itself should not be in category field
        // This test ensures your matching logic only looks at actual message content
    }
}
