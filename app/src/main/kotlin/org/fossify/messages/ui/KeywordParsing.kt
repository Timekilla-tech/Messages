package org.fossify.messages.ui

import java.util.Locale

internal fun normalizePlainKeywords(input: String): List<String> {
    return input
        .split(",")
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotEmpty() }
}

internal fun appendPlainKeywords(input: String, list: MutableList<String>): Boolean {
    var anyAdded = false
    normalizePlainKeywords(input).forEach { word ->
        if (!list.contains(word)) {
            list.add(word)
            anyAdded = true
        }
    }
    return anyAdded
}

internal fun validateRegexPattern(input: String): String? {
    val pattern = input.trim()
    if (pattern.isEmpty()) return null
    return try {
        Regex(pattern)
        null
    } catch (e: Exception) {
        "Invalid: ${e.message?.take(60)}"
    }
}

internal fun appendRegexPattern(input: String, list: MutableList<String>): String? {
    val pattern = input.trim()
    if (pattern.isEmpty()) return null
    val error = validateRegexPattern(pattern)
    if (error == null && !list.contains(pattern)) {
        list.add(pattern)
    }
    return error
}

