package org.fossify.messages.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.value
import org.fossify.messages.R
import org.fossify.messages.databinding.DialogAddOrEditCategoryBinding
import androidx.compose.ui.platform.ComposeView
import org.fossify.messages.ui.KeywordItem
import org.fossify.messages.ui.KeywordManager
import org.fossify.messages.extensions.createCategory
import org.fossify.messages.extensions.updateCategory
import org.fossify.messages.models.Category
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast


class AddOrEditCategoryDialog(
    val activity: BaseSimpleActivity,
    private  val originalCategory: Category? = null,
    val callback: () -> Unit
) {
    init {
        var selectedColor = originalCategory?.color ?: activity.getProperTextColor()
        val colorOptions = listOf(
            "Blue" to 0xFF2196F3.toInt(),
            "Green" to 0xFF4CAF50.toInt(),
            "Orange" to 0xFFFF9800.toInt(),
            "Red" to 0xFFF44336.toInt(),
            "Purple" to 0xFF9C27B0.toInt(),
            "Teal" to 0xFF009688.toInt(),
            "Pink" to 0xFFE91E63.toInt(),
            "Indigo" to 0xFF3F51B5.toInt(),
            "Brown" to 0xFF795548.toInt(),
            "Gray" to 0xFF607D8B.toInt(),
        )

        // keywords list shared between the Compose view and the save handler
        val currentKeywords = mutableListOf<KeywordItem>()

        val binding = DialogAddOrEditCategoryBinding.inflate(activity.layoutInflater).apply {
            if (originalCategory != null) {
                addCategoryNameEdittext.setText(originalCategory.name)
                addCategoryDescriptionEdittext.setText(originalCategory.description)
                // keywords will be shown in the Compose KeywordManager; initial value will be set below
                addCategoryIconEdittext.setText(originalCategory.icon)
            }

            // Prepare initial keywords list for the KeywordManager
            val initialKeywordItems = mutableListOf<KeywordItem>()
            if (originalCategory != null) {
                if (originalCategory.keywordIsRegex) {
                    originalCategory.keywords
                        .lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { initialKeywordItems.add(KeywordItem(it, true)) }
                } else {
                    // previous format: comma-separated keywords
                    originalCategory.keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        .forEach { initialKeywordItems.add(KeywordItem(it, false)) }
                }
            }

            // initialize shared currentKeywords from initial items
            currentKeywords.addAll(initialKeywordItems)

            // wire the ComposeView to show KeywordManager
            addCategoryKeywordsCompose.setContent {
                KeywordManager(
                    initialKeywords = initialKeywordItems,
                    onKeywordsChanged = { list ->
                        currentKeywords.clear()
                        currentKeywords.addAll(list)
                    }
                )
            }

            fun updateColorPreview() {
                addCategoryColorPreview.background?.mutate()?.setTint(selectedColor)
                addCategoryPickColor.setTextColor(selectedColor.getContrastColor())
                addCategoryPickColor.background?.mutate()?.setTint(selectedColor)
            }

            addCategoryPickColor.setOnClickListener {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.choose_color)
                    .setItems(colorOptions.map { it.first }.toTypedArray()) { _, which ->
                        selectedColor = colorOptions[which].second
                        updateColorPreview()
                    }
                    .show()
            }

            updateColorPreview()
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    alertDialog.showKeyboard(binding.addCategoryNameEdittext)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = binding.addCategoryNameEdittext.value
                        val description = binding.addCategoryDescriptionEdittext.value
                        val icon = binding.addCategoryIconEdittext.value

                        if (name.isEmpty()) {
                            activity.toast(R.string.category_name_cannot_be_empty)
                            return@setOnClickListener
                        }

                        val hasRegexKeywords = currentKeywords.any { it.isRegex }
                        val keywords: String = if (hasRegexKeywords) {
                            currentKeywords.joinToString("\n") { keywordItem ->
                                if (keywordItem.isRegex) keywordItem.keyword else Regex.escape(keywordItem.keyword)
                            }
                        } else {
                            currentKeywords.joinToString(",") { it.keyword }
                        }

                        // Validate regex pattern(s) if any regex chip is enabled
                        if (hasRegexKeywords && keywords.isNotEmpty()) {
                            try {
                                currentKeywords.forEach { item ->
                                    if (item.isRegex && item.keyword.isNotEmpty()) Regex(item.keyword)
                                }
                            } catch (e: Exception) {
                                activity.showErrorToast("${activity.getString(R.string.invalid_regex_pattern)}: ${e.message}")
                                return@setOnClickListener
                            }
                        }

                        if (originalCategory != null) {
                            // Edit existing
                            val updatedCategory = originalCategory.copy(
                                name = name,
                                color = selectedColor,
                                description = description,
                                keywords = keywords,
                                icon = icon,
                                keywordIsRegex = hasRegexKeywords
                            )
                            activity.updateCategory(updatedCategory) {
                                callback()
                                alertDialog.dismiss()
                            }
                        } else {
                            // Create new
                            activity.createCategory(
                                name = name,
                                color = selectedColor,
                                description = description,
                                keywords = keywords,
                                icon = icon,
                                keywordIsRegex = hasRegexKeywords
                            ) {
                                callback()
                                alertDialog.dismiss()
                            }
                        }
                    }
                }
            }
    }
}

