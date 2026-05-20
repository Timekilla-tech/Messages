package org.fossify.messages.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.util.Locale

data class KeywordItem(
    val keyword: String,
    val isRegex: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordManager(
    modifier: Modifier = Modifier,
    initialKeywords: List<KeywordItem> = emptyList(),
    onKeywordsChanged: (List<KeywordItem>) -> Unit = {},
) {
    val keywords = remember {
        mutableStateListOf<KeywordItem>().apply { addAll(initialKeywords) }
    }
    var isEditMode by remember { mutableStateOf(false) }
    var pendingKeyword by remember { mutableStateOf("") }
    val editScrollState = rememberScrollState()
    val currentOnKeywordsChanged = rememberUpdatedState(onKeywordsChanged)

    fun addKeyword() {
        val values = pendingKeyword
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (values.isEmpty()) return

        runCatching {
            val existing = keywords
                .map { it.keyword.lowercase(Locale.ROOT) }
                .toMutableSet()

            values.forEach { value ->
                val key = value.lowercase(Locale.ROOT)
                if (existing.add(key)) {
                    keywords.add(KeywordItem(keyword = value))
                }
            }
            pendingKeyword = ""
        }
    }

    fun removeKeyword(index: Int) {
        if (index !in keywords.indices) return

        runCatching {
            keywords.removeAt(index)
        }
    }

    fun updateRegexState(index: Int, isRegex: Boolean) {
        if (index !in keywords.indices) return

        runCatching {
            keywords[index] = keywords[index].copy(isRegex = isRegex)
        }
    }

    // Use a derived state so changes to the snapshot-state list contents change the signature
    val keywordsSignature by remember {
        derivedStateOf { keywords.joinToString("|") { "${it.keyword}:${it.isRegex}" } }
    }

    LaunchedEffect(keywordsSignature) {
        // publish the latest list asynchronously to avoid snapshot mutation conflicts
        currentOnKeywordsChanged.value(keywords.toList())
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isEditMode) "Edit keywords" else "Keywords",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { isEditMode = !isEditMode }) {
                Icon(
                    imageVector = if (isEditMode) Icons.Filled.Check else Icons.Filled.Edit,
                    contentDescription = if (isEditMode) "Done editing keywords" else "Edit keywords",
                )
            }
        }

        if (isEditMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = pendingKeyword,
                    onValueChange = { pendingKeyword = it },
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    label = { Text(text = "Add keyword(s)") },
                    supportingText = { Text(text = "One keyword/regex per line") },
                )
                IconButton(onClick = { addKeyword() }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add keyword",
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .heightIn(max = 260.dp)
                        .verticalScroll(editScrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (keywords.isEmpty()) {
                        Text(text = "No keywords added")
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            keywords.forEachIndexed { index, keywordItem ->
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    InputChip(
                                        selected = false,
                                        onClick = {},
                                        colors = InputChipDefaults.inputChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                            leadingIconColor = MaterialTheme.colorScheme.onSurface,
                                            trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                                            selectedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        label = {
                                            Text(
                                                text = if (keywordItem.isRegex) {
                                                    ".* ${keywordItem.keyword}"
                                                } else {
                                                    keywordItem.keyword
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Delete keyword",
                                                modifier = Modifier.clickable { removeKeyword(index) },
                                            )
                                        },
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        Checkbox(
                                            checked = keywordItem.isRegex,
                                            onCheckedChange = { updateRegexState(index, it) },
                                        )
                                        Text(text = "Regex")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    if (keywords.isEmpty()) {
                        Text(text = "No keywords added")
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            keywords.forEach { keywordItem ->
                                SuggestionChip(
                                    onClick = {},
                                    colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                                        disabledIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    label = {
                                        Text(
                                            text = if (keywordItem.isRegex) {
                                                ".* ${keywordItem.keyword}"
                                            } else {
                                                keywordItem.keyword
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


