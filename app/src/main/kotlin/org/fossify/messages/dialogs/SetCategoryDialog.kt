package org.fossify.messages.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.extensions.getAllCategories

class SetCategoryDialog(
    val activity: SimpleActivity,
    currentCategory: String = "",
    val callback: (category: String) -> Unit
) {
    init {
        ensureBackgroundThread {
            val categories = activity.getAllCategories().map { it.name }

            activity.runOnUiThread {
                val selected = currentCategory
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toMutableSet()

                val checkedItems = categories.map { it in selected }.toBooleanArray()

                AlertDialog.Builder(activity)
                    .setTitle(R.string.set_category)
                    .setMultiChoiceItems(categories.toTypedArray(), checkedItems) { _, which, isChecked ->
                        val name = categories.getOrNull(which) ?: return@setMultiChoiceItems
                        if (isChecked) selected.add(name) else selected.remove(name)
                    }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val normalized = categories.filter { it in selected }.joinToString(", ")
                        callback(normalized)
                    }
                    .setNeutralButton(R.string.clear) { _, _ -> callback("") }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }
}
