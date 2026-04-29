package org.fossify.messages.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.extensions.getAllCategories

class SetCategoryDialog(
    activity: SimpleActivity,
    currentCategory: String = "",
    val callback: (category: String) -> Unit
) {

    init {
        val categories = activity.getAllCategories()
        val categoryNames = categories.map { it.name }
        val selected = currentCategory
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
            .toMutableSet()
        val checkedItems = categoryNames.map { it in selected }.toBooleanArray()

        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.set_category)
        builder.setMultiChoiceItems(categoryNames.toTypedArray(), checkedItems) { _, which, isChecked ->
            val name = categoryNames.getOrNull(which) ?: return@setMultiChoiceItems
            if (isChecked) {
                selected.add(name)
            } else {
                selected.remove(name)
            }
        }
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            val normalized = categoryNames.filter { it in selected }.joinToString(", ")
            callback(normalized)
        }
        builder.setNeutralButton(R.string.clear) { _, _ ->
            callback("")
        }
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }
}


