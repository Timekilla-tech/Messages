package org.fossify.messages.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.messages.R
import java.util.Locale

data class CategoryKeywords(
    val plainWords: List<String> = emptyList(),
    val regexPatterns: List<String> = emptyList()
)

@Composable
fun KeywordManager(
    modifier: Modifier = Modifier,
    initialPlainWords: List<String> = emptyList(),
    initialRegexPatterns: List<String> = emptyList(),
    onChanged: (plainWords: List<String>, regexPatterns: List<String>) -> Unit = { _, _ -> },
) {
    val plainWords = remember {
        mutableStateListOf<String>().apply { addAll(initialPlainWords) }
    }
    val regexPatterns = remember {
        mutableStateListOf<String>().apply { addAll(initialRegexPatterns) }
    }

    var pendingInput by remember { mutableStateOf("") }
    var isRegexMode by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var duplicateWarning by remember { mutableStateOf(false) }

    val signature by remember {
        derivedStateOf {
            plainWords.joinToString() + "|" + regexPatterns.joinToString()
        }
    }

    val currentOnChanged = rememberUpdatedState(onChanged)
    LaunchedEffect(signature) {
        currentOnChanged.value(plainWords.toList(), regexPatterns.toList())
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(text = "Keywords", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Plain words (case-insensitive) or Regex patterns",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Combined Chips Section
        if (plainWords.isNotEmpty() || regexPatterns.isNotEmpty()) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Show plain words first
                plainWords.forEachIndexed { index, word ->
                    KeywordChip(
                        text = word,
                        isRegex = false,
                        onRemove = { plainWords.removeAt(index) },
                        onEdit = { newValue, wasRegex ->
                            plainWords.removeAt(index)
                            if (wasRegex) {
                                addRegexPattern(newValue, regexPatterns)
                            } else {
                                addPlainWords(newValue, plainWords)
                            }
                        }
                    )
                }
                // Then regex patterns
                regexPatterns.forEachIndexed { index, pattern ->
                    KeywordChip(
                        text = pattern,
                        isRegex = true,
                        onRemove = { regexPatterns.removeAt(index) },
                        onEdit = { newValue, wasRegex ->
                            regexPatterns.removeAt(index)
                            if (wasRegex) {
                                addRegexPattern(newValue, regexPatterns)
                            } else {
                                addPlainWords(newValue, plainWords)
                            }
                        }
                    )
                }
            }
        }

        // Single Input Field
        OutlinedTextField(
            value = pendingInput,
            onValueChange = {
                pendingInput = it
                duplicateWarning = false
                inputError = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(if (isRegexMode) "Add regex pattern" else "Add plain word(s)") },
            isError = inputError != null || duplicateWarning,
            supportingText = {
                if (inputError != null) {
                    Text(text = inputError!!, color = MaterialTheme.colorScheme.error)
                } else if (duplicateWarning) {
                    Text(text = "Already exists", color = MaterialTheme.colorScheme.error)
                } else {
                    Text(if (isRegexMode) "Invalid regex will be rejected" else "Comma-separated for multiple")
                }
            },
            leadingIcon = {
                IconButton(onClick = { isRegexMode = !isRegexMode }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_regex_vector),
                        contentDescription = "Toggle Regex",
                        tint = if (isRegexMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        processInput(
                            pendingInput, isRegexMode, plainWords, regexPatterns,
                            onError = { inputError = it },
                            onDuplicate = { duplicateWarning = true },
                            onSuccess = { pendingInput = "" }
                        )
                    },
                    enabled = pendingInput.isNotBlank()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (pendingInput.isNotBlank()) {
                        processInput(
                            pendingInput, isRegexMode, plainWords, regexPatterns,
                            onError = { inputError = it },
                            onDuplicate = { duplicateWarning = true },
                            onSuccess = { pendingInput = "" }
                        )
                    }
                }
            )
        )
    }
}

private fun processInput(
    input: String,
    isRegex: Boolean,
    plainWords: MutableList<String>,
    regexPatterns: MutableList<String>,
    onError: (String) -> Unit,
    onDuplicate: () -> Unit,
    onSuccess: () -> Unit
) {
    if (isRegex) {
        val error = addRegexPattern(input, regexPatterns)
        if (error != null) {
            onError(error)
        } else {
            onSuccess()
        }
    } else {
        val added = addPlainWords(input, plainWords)
        if (!added) {
            onDuplicate()
        } else {
            onSuccess()
        }
    }
}

@Composable
private fun KeywordChip(
    text: String,
    isRegex: Boolean,
    onRemove: () -> Unit,
    onEdit: (String, Boolean) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    InputChip(
        selected = false,
        onClick = { showEditDialog = true },
        label = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = if (isRegex) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        },
        leadingIcon = if (isRegex) {
            {
                Icon(
                    painter = painterResource(id = R.drawable.ic_regex_vector),
                    contentDescription = "Regex",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else null,
        trailingIcon = {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onRemove() },
            )
        },
    )

    if (showEditDialog) {
        EditKeywordDialog(
            initialText = text,
            initialIsRegex = isRegex,
            onDismiss = { showEditDialog = false },
            onConfirm = { newText, newIsRegex ->
                onEdit(newText, newIsRegex)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun EditKeywordDialog(
    initialText: String,
    initialIsRegex: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var isRegex by remember { mutableStateOf(initialIsRegex) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Keyword") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Keyword/Pattern") },
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(error!!, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    trailingIcon = {
                        IconButton(onClick = { isRegex = !isRegex }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_regex_vector),
                                contentDescription = "Toggle Regex",
                                tint = if (isRegex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Regex mode: ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (isRegex) "ON" else "OFF",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isRegex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isRegex) {
                        try {
                            Regex(text)
                            onConfirm(text, true)
                        } catch (_: Exception) {
                            error = "Invalid regex"
                        }
                    } else {
                        onConfirm(text, false)
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun addPlainWords(input: String, list: MutableList<String>): Boolean {
    var anyAdded = false
    input
        .split(",")
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotEmpty() }
        .forEach { word ->
            if (!list.contains(word)) {
                list.add(word)
                anyAdded = true
            }
        }
    return anyAdded
}

private fun addRegexPattern(input: String, list: MutableList<String>): String? {
    val pattern = input.trim()
    if (pattern.isEmpty()) return null
    return try {
        Regex(pattern)
        if (!list.contains(pattern)) {
            list.add(pattern)
        }
        null
    } catch (e: Exception) {
        "Invalid: ${e.message?.take(60)}"
    }
}
