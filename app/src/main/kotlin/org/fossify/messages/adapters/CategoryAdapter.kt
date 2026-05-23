package org.fossify.messages.adapters

import android.graphics.drawable.GradientDrawable
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemCategoryBinding
import org.fossify.messages.models.Category

class CategoryAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    onRefresh: () -> Unit,
    itemClick: (Any) -> Unit,
    private val onDeleteClick: (Category) -> Unit = {}
) : MyRecyclerViewListAdapter<Category>(
    activity = activity,
    recyclerView = recyclerView,
    diffUtil = CategoryDiffCallback(),
    itemClick = itemClick,
    onRefresh = onRefresh
) {
    override fun getActionMenuId() = 0
    override fun prepareActionMode(menu: Menu) {}
    override fun actionItemPressed(id: Int) {}
    override fun getSelectableItemCount() = itemCount
    override fun getIsItemSelectable(position: Int) = false
    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.id?.toInt()
    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.id.toInt() == key }
    override fun onActionModeCreated() {}
    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = getItem(position)
        holder.bindView(category, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, category)
        }
        bindViewHolder(holder)
    }

    override fun getItemId(position: Int) = getItem(position).id


    private fun setupView(view: View, category: Category) {
        ItemCategoryBinding.bind(view).apply {
            categoryName.text = category.name
            categoryName.setTextColor(activity.getProperTextColor())

            categoryDescription.text = category.description
            categoryDescription.setTextColor(activity.getProperTextColor())
            categoryDescription.visibility = if (category.description.isEmpty()) View.GONE else View.VISIBLE
            
            val plainCount = category.plainKeywords.split(",").count { it.trim().isNotEmpty() }
            val regexCount = category.regexPatterns.lineSequence().count { it.trim().isNotEmpty() }
            categoryKeywordCount.text = (plainCount + regexCount).toString()
            categoryKeywordCount.setTextColor(activity.getProperTextColor().adjustAlpha(0.7f))

            val bg = (categoryColor.background?.mutate() as? GradientDrawable)
            bg?.setColor(category.color)
            categoryColor.background = bg

            defaultBadge.visibility = if (category.isDefault) View.VISIBLE else View.GONE
            defaultBadge.setTextColor(activity.getProperTextColor())

            btnDelete.setOnClickListener { onDeleteClick(category) }
            btnDelete.applyColorFilter(activity.getProperTextColor())
        }
    }

    private class CategoryDiffCallback : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(oldItem: Category, newItem: Category): Boolean {
            return Category.areItemsTheSame(oldItem, newItem)
        }

        override fun areContentsTheSame(oldItem: Category, newItem: Category): Boolean {
            return Category.areContentsTheSame(oldItem, newItem)
        }
    }
}
