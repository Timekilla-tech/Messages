package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.activities.ManageBlockedNumbersActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.messages.R
import org.fossify.messages.adapters.CategoryAdapter
import org.fossify.messages.databinding.ActivityManageCategoriesBinding
import org.fossify.messages.dialogs.AddOrEditCategoryDialog
import org.fossify.messages.extensions.getAllCategories
import org.fossify.messages.extensions.deleteCategory
import org.fossify.messages.models.Category

class ManageCategoriesActivity : SimpleActivity(), RefreshRecyclerViewListener {

    private val binding by viewBinding(ActivityManageCategoriesBinding::inflate)
    private lateinit var adapter: CategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateCategories()
        setupOptionsMenu()
        setupManageBlockedNumbers()
        setupManageBlockedKeywords()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.manageCategoriesList))
        setupMaterialScrollListener(
            scrollingView = binding.manageCategoriesList,
            topAppBar = binding.categoriesAppbar
        )
        updateTextColors(binding.manageCategoriesWrapper)

        binding.manageCategoriesPlaceholder2.apply {
            underlineText()
            setTextColor(getProperPrimaryColor())
            setOnClickListener {
                addOrEditCategory()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.categoriesAppbar, NavigationIcon.Arrow)
    }

    private fun setupOptionsMenu() {
        binding.categoriesToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_category -> {
                    addOrEditCategory()
                    true
                }

                else -> false
            }
        }
    }

    override fun refreshItems() {
        updateCategories()
    }

    private fun updateCategories() {
        ensureBackgroundThread {
            try {
                val categories = getAllCategories().sortedBy { it.name }.toMutableList()
                runOnUiThread {
                    adapter = CategoryAdapter(
                        activity = this,
                        recyclerView = binding.manageCategoriesList,
                        onRefresh = { updateCategories() },
                        itemClick = { item ->
                            val category = item as Category
                            addOrEditCategory(category)
                        },
                        onDeleteClick = { category ->
                            askConfirmDelete(category)
                        }
                    ).apply {
                        binding.manageCategoriesList.adapter = this
                        submitList(categories)
                    }

                    binding.manageCategoriesPlaceholder.beVisibleIf(categories.isEmpty())
                    binding.manageCategoriesPlaceholder2.beVisibleIf(categories.isEmpty())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showErrorToast(e)
            }
        }
    }

    private fun addOrEditCategory(category: Category? = null) {
        AddOrEditCategoryDialog(this, category) {
            updateCategories()
        }
    }

    private fun askConfirmDelete(category: Category) {
        val question = String.format(
            getString(org.fossify.commons.R.string.deletion_confirmation),
            category.name
        )

        ConfirmationDialog(this, question) {
            ensureBackgroundThread {
                deleteCategory(category) {
                    updateCategories()
                }
            }
        }
    }

    private fun setupManageBlockedNumbers() {
        binding.manageBlockedNumbersHolder.setOnClickListener {
            Intent(this, ManageBlockedNumbersActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupManageBlockedKeywords() {
        binding.manageBlockedKeywordsHolder.setOnClickListener {
            Intent(this, ManageBlockedKeywordsActivity::class.java).apply {
                startActivity(this)
            }
        }
    }
}

