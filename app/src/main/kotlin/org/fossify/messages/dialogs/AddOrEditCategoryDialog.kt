package org.fossify.messages.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.value
import org.fossify.messages.R
import org.fossify.messages.databinding.DialogAddOrEditCategoryBinding
import org.fossify.messages.ui.KeywordManager
import org.fossify.messages.extensions.createCategory
import org.fossify.messages.extensions.updateCategory
import org.fossify.messages.models.Category
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast


class AddOrEditCategoryDialog(
    val activity: BaseSimpleActivity,
    private val originalCategory: Category? = null,
    val callback: () -> Unit
) {
    init {
        var selectedColor = originalCategory?.color ?: activity.getProperTextColor()
        val colorOptions = listOf(
            0xFF2196F3.toInt(), // Blue
            0xFF4CAF50.toInt(), // Green
            0xFFFF9800.toInt(), // Orange
            0xFFF44336.toInt(), // Red
            0xFF9C27B0.toInt(), // Purple
            0xFF009688.toInt(), // Teal
            0xFFE91E63.toInt(), // Pink
            0xFF3F51B5.toInt(), // Indigo
            0xFF795548.toInt(), // Brown
            0xFF607D8B.toInt(), // Gray
            0xFF000000.toInt(), // Black
            0xFF8BC34A.toInt(), // Light Green
            0xFFFFEB3B.toInt(), // Yellow
            0xFFFFC107.toInt(), // Amber
            0xFF03A9F4.toInt(), // Light Blue
            0xFF00BCD4.toInt(), // Cyan
        )

        // keywords lists shared between the Compose view and the save handler
        val currentPlainWords = mutableListOf<String>()
        val currentRegexPatterns = mutableListOf<String>()

        val binding = DialogAddOrEditCategoryBinding.inflate(activity.layoutInflater).apply {
            if (originalCategory != null) {
                addCategoryNameEdittext.setText(originalCategory.name)
            }

            // Prepare initial keywords lists for the KeywordManager
            val initialPlainWords = mutableListOf<String>()
            val initialRegexPatterns = mutableListOf<String>()
            if (originalCategory != null) {
                // Try to load from new columns first (if they exist)
                if (originalCategory.plainKeywords.isNotEmpty()) {
                    originalCategory.plainKeywords
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { initialPlainWords.add(it) }
                }
                if (originalCategory.regexPatterns.isNotEmpty()) {
                    originalCategory.regexPatterns
                        .lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { initialRegexPatterns.add(it) }
                }

                // Fallback to old format for backward compatibility
                if (initialPlainWords.isEmpty() && initialRegexPatterns.isEmpty()) {
                    if (originalCategory.keywordIsRegex) {
                        // regex format: newline-separated (legacy)
                        originalCategory.keywords
                            .lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { initialRegexPatterns.add(it) }
                    } else {
                        // plain format: comma-separated (legacy)
                        originalCategory.keywords
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { initialPlainWords.add(it) }
                    }
                }
            }

            // wire the ComposeView to show KeywordManager
            addCategoryKeywordsCompose.setContent {
                KeywordManager(
                    initialPlainWords = initialPlainWords,
                    initialRegexPatterns = initialRegexPatterns,
                    onChanged = { plainWords, regexPatterns ->
                        currentPlainWords.clear()
                        currentPlainWords.addAll(plainWords)
                        currentRegexPatterns.clear()
                        currentRegexPatterns.addAll(regexPatterns)
                    },
                    textColor = activity.getProperTextColor(),
                    primaryColor = activity.getProperPrimaryColor(),
                    backgroundColor = activity.getProperBackgroundColor()
                )
            }

            fun setupColorOptions() {
                addCategoryColorOptionsLayout.removeAllViews()
                val circleSize = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.list_icon_size_small)
                val margin = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)

                colorOptions.forEach { color ->
                    val view = android.view.View(activity).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(circleSize, circleSize).apply {
                            setMargins(0, 0, margin, 0)
                        }
                        background = androidx.appcompat.content.res.AppCompatResources.getDrawable(activity, org.fossify.commons.R.drawable.circle_background)?.mutate()?.apply {
                            androidx.core.graphics.drawable.DrawableCompat.setTint(this, color)
                        }

                        if (selectedColor == color) {
                            alpha = 1.0f
                            elevation = 4f
                        } else {
                            alpha = 0.4f
                            elevation = 0f
                        }

                        setOnClickListener {
                            selectedColor = color
                            setupColorOptions()
                        }
                    }
                    addCategoryColorOptionsLayout.addView(view)
                }
            }

            setupColorOptions()
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    alertDialog.showKeyboard(binding.addCategoryNameEdittext)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = binding.addCategoryNameEdittext.value

                        if (name.isEmpty()) {
                            activity.toast(R.string.category_name_cannot_be_empty)
                            return@setOnClickListener
                        }

                        // Store plain words as comma-separated, regex patterns as newline-separated
                        val plainWordsStr = currentPlainWords.joinToString(",")
                        val regexPatternsStr = currentRegexPatterns.joinToString("\n")
                        val hasRegexPatterns = currentRegexPatterns.isNotEmpty()

                        // Regex patterns are already validated during input (immediate validation in KeywordManager)
                        // But validate again before save to be safe
                        if (hasRegexPatterns) {
                            try {
                                currentRegexPatterns.forEach { pattern ->
                                    Regex(pattern)
                                }
                            } catch (e: Exception) {
                                activity.showErrorToast("${activity.getString(R.string.invalid_regex_pattern)}: ${e.message}")
                                return@setOnClickListener
                            }
                        }

                        if (originalCategory != null) {
                            // Edit existing — save both plain words and regex patterns
                            val updatedCategory = originalCategory.copy(
                                name = name,
                                color = selectedColor,
                                plainKeywords = plainWordsStr,           // NEW: save plain words
                                regexPatterns = regexPatternsStr,        // NEW: save regex patterns
                                keywordIsRegex = hasRegexPatterns        // For backward compat
                            )
                            activity.updateCategory(updatedCategory) {
                                callback()
                                alertDialog.dismiss()
                            }
                        } else {
                            // Create new — save both plain words and regex patterns
                            activity.createCategory(
                                name = name,
                                color = selectedColor,
                                plainKeywords = plainWordsStr,           // NEW: save plain words
                                regexPatterns = regexPatternsStr,        // NEW: save regex patterns
                                keywordIsRegex = hasRegexPatterns        // For backward compat
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
