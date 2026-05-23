package org.fossify.messages.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.messages.R

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
    textColor: Int = 0,
    primaryColor: Int = 0,
    backgroundColor: Int = 0,
) {
    val plainWords = remember {
        mutableStateListOf<String>().apply { addAll(initialPlainWords) }
    }
    val regexPatterns = remember {
        mutableStateListOf<String>().apply { addAll(initialRegexPatterns) }
    }

    val composeTextColor = if (textColor != 0) Color(textColor) else MaterialTheme.colorScheme.onSurface
    val composePrimaryColor = if (primaryColor != 0) Color(primaryColor) else MaterialTheme.colorScheme.primary
    val composeBackgroundColor = if (backgroundColor != 0) Color(backgroundColor) else MaterialTheme.colorScheme.surface

    val signature by remember {
        derivedStateOf {
            plainWords.joinToString(",") + "|" + regexPatterns.joinToString("\n")
        }
    }

    val currentOnChanged = rememberUpdatedState(onChanged)
    LaunchedEffect(signature) {
        currentOnChanged.value(plainWords.toList(), regexPatterns.toList())
    }

    val colorScheme = if (textColor == -1) { // -1 is white, usually means dark mode in Fossify context
        darkColorScheme(
            primary = composePrimaryColor,
            surface = composeBackgroundColor,
            onSurface = composeTextColor,
            onSurfaceVariant = composeTextColor.copy(alpha = 0.7f),
            background = composeBackgroundColor,
            onBackground = composeTextColor,
            outline = composeTextColor.copy(alpha = 0.5f),
            surfaceVariant = composeBackgroundColor
        )
    } else {
        lightColorScheme(
            primary = composePrimaryColor,
            surface = composeBackgroundColor,
            onSurface = composeTextColor,
            onSurfaceVariant = composeTextColor.copy(alpha = 0.7f),
            background = composeBackgroundColor,
            onBackground = composeTextColor,
            outline = composeTextColor.copy(alpha = 0.5f),
            surfaceVariant = composeBackgroundColor
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(color = composeBackgroundColor) {
            KeywordManagerContent(
                modifier = modifier,
                plainWords = plainWords,
                regexPatterns = regexPatterns,
                textColor = composeTextColor
            )
        }
    }
}

@Composable
private fun KeywordManagerContent(
    modifier: Modifier,
    plainWords: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    regexPatterns: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    textColor: Color
) {
    var pendingInput by remember { mutableStateOf("") }
    var isRegexMode by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var duplicateWarning by remember { mutableStateOf(false) }

    val secondaryTextColor = textColor.copy(alpha = 0.7f)

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(4.dp))

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
                        },
                        textColor = textColor
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
                        },
                        textColor = textColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
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
            placeholder = { Text(if (isRegexMode) "Add regex pattern" else "Add plain word(s)", color = secondaryTextColor) },
            isError = inputError != null || duplicateWarning,
            textStyle = androidx.compose.ui.text.TextStyle(color = textColor),
            supportingText = {
                if (inputError != null) {
                    Text(text = inputError!!, color = MaterialTheme.colorScheme.error)
                } else if (duplicateWarning) {
                    Text(text = "Already exists", color = MaterialTheme.colorScheme.error)
                } else {
                    Text(if (isRegexMode) "Invalid regex will be rejected" else "Comma-separated for multiple", color = secondaryTextColor)
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
    onEdit: (String, Boolean) -> Unit,
    textColor: Color = MaterialTheme.colorScheme.onSurface
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
        colors = InputChipDefaults.inputChipColors(
            labelColor = textColor,
            trailingIconColor = textColor.copy(alpha = 0.7f)
        ),
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
            },
            textColor = textColor
        )
    }
}

@Composable
private fun EditKeywordDialog(
    initialText: String,
    initialIsRegex: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    var text by remember { mutableStateOf(initialText) }
    var isRegex by remember { mutableStateOf(initialIsRegex) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = textColor,
        textContentColor = textColor,
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
                    label = { Text("Keyword/Pattern", color = textColor.copy(alpha = 0.7f)) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = textColor),
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
                    Text("Regex mode: ", style = MaterialTheme.typography.bodyMedium, color = textColor)
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
    return appendPlainKeywords(input, list)
}

private fun addRegexPattern(input: String, list: MutableList<String>): String? {
    return appendRegexPattern(input, list)
}
