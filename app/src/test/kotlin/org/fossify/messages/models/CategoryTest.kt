package org.fossify.messages.tests

import org.fossify.messages.models.Category

internal object CategoryFixtures {
    val base = Category(
        id = 1,
        name = "Promotions",
        color = 0xFF0000,
        icon = "ic_filter_list_vector",
        description = "Promo folder",
        isDefault = false,
        keywords = "sale,offer",
        keywordIsRegex = false,
        plainKeywords = "sale,offer",
        regexPatterns = ""
    )

    val same = base.copy()
    val changed = base.copy(regexPatterns = "^sale.*$")
}

