package org.fossify.messages.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.extensions.getAllCategories

class SetCategoryDialog(
    activity: SimpleActivity,
    private val currentCategory: String = "",
    val callback: (category: String) -> Unit
) {

    init {
        val categories = activity.getAllCategories()
        val categoryNames = mutableListOf<String>()
        categoryNames.add("") // Add empty option to clear category
        categoryNames.addAll(categories.map { it.name })

        val checkedItem = categoryNames.indexOfFirst { it == currentCategory }

        AlertDialog.Builder(activity)
            .setTitle(R.string.set_category)
            .setSingleChoiceItems(
                categoryNames.toTypedArray(),
                checkedItem
            ) { dialog, which ->
                val selectedCategory = categoryNames.getOrNull(which) ?: ""
                callback(selectedCategory)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}


