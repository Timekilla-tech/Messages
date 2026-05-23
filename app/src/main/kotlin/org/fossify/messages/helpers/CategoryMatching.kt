@file:Suppress("unused")

package org.fossify.messages.helpers

import org.fossify.messages.models.Category

internal fun isMessageMatchingCategory(
    body: String,
    sender: String,
    category: Category,
): Boolean {
    if (category.plainKeywords.isNotEmpty()) {
        val plainMatch = category.plainKeywords
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .any { keyword ->
                body.lowercase().contains(keyword) || sender.contains(keyword)
            }

        if (plainMatch) return true
    }

    if (category.regexPatterns.isNotEmpty()) {
        val regexes = category.regexPatterns
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { pattern ->
                try {
                    Regex(pattern)
                } catch (_: Exception) {
                    null
                }
            }
            .toList()

        if (regexes.isNotEmpty()) {
            val regexMatch = regexes.any { regex ->
                regex.containsMatchIn(body) || regex.containsMatchIn(sender)
            }
            if (regexMatch) return true
        }
    }

    if (category.plainKeywords.isEmpty() && category.regexPatterns.isEmpty() && category.keywords.isNotEmpty()) {
        return if (category.keywordIsRegex) {
            val regexes = category.keywords
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { pattern ->
                    try {
                        Regex(pattern)
                    } catch (_: Exception) {
                        null
                    }
                }
                .toList()

            if (regexes.isEmpty()) {
                false
            } else {
                regexes.any { regex ->
                    regex.containsMatchIn(body) || regex.containsMatchIn(sender)
                }
            }
        } else {
            val keywords = category.keywords.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            keywords.any { keyword ->
                body.lowercase().contains(keyword) || sender.contains(keyword)
            }
        }
    }

    return false
}

