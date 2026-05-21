package org.fossify.messages.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.getAllCategories
import org.fossify.messages.helpers.SavedViewsStore
import org.fossify.messages.models.SavedView

class SetCategoryDialog(
    activity: SimpleActivity,
    currentCategory: String = "",
    val callback: (category: String) -> Unit
) {

    init {
        val categories = activity.getAllCategories().map { it.name }
        val folders = SavedViewsStore(activity.config).getViews()
            .filter { it.id != SavedView.MAIN_VIEW_ID }
            .map { it.title }
        
        val allOptions = (categories + folders).distinct()
        
        val selected = currentCategory
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
            .toMutableSet()
        val checkedItems = allOptions.map { it in selected }.toBooleanArray()

        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.set_category)
        builder.setMultiChoiceItems(allOptions.toTypedArray(), checkedItems) { _, which, isChecked ->
            val name = allOptions.getOrNull(which) ?: return@setMultiChoiceItems
            if (isChecked) {
                selected.add(name)
            } else {
                selected.remove(name)
            }
        }
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            val normalized = allOptions.filter { it in selected }.joinToString(", ")
            callback(normalized)
        }
        builder.setNeutralButton(R.string.clear) { _, _ ->
            callback("")
        }
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }
}


