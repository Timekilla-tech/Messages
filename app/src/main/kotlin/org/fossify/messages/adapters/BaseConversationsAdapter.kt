package org.fossify.messages.adapters

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Parcelable
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.activities.MainActivity
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemConversationBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.getAllDrafts
import org.fossify.messages.extensions.getAllCategories
import org.fossify.messages.models.Conversation
import java.util.Locale

@Suppress("LeakingThis")
abstract class BaseConversationsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    onRefresh: () -> Unit,
    itemClick: (Any) -> Unit,
) : MyRecyclerViewListAdapter<Conversation>(
    activity = activity,
    recyclerView = recyclerView,
    diffUtil = ConversationDiffCallback(),
    itemClick = itemClick,
    onRefresh = onRefresh
),
    RecyclerViewFastScroller.OnPopupTextUpdate {
    private var fontSize = activity.getTextSize()
    private var drafts = HashMap<Long, String>()
    private var categoryColors = HashMap<String, Int>()

    private var recyclerViewState: Parcelable? = null

    init {
        setupDragListener(true)
        setHasStableIds(true)
        updateDrafts()
        updateCategoryColors()

        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = restoreRecyclerViewState()
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
                restoreRecyclerViewState()

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) =
                restoreRecyclerViewState()
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateFontSize() {
        fontSize = activity.getTextSize()
        notifyDataSetChanged()
    }

    fun updateConversations(
        newConversations: ArrayList<Conversation>,
        commitCallback: (() -> Unit)? = null,
    ) {
        updateCategoryColors()
        saveRecyclerViewState()
        submitList(newConversations.toList(), commitCallback)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateDrafts() {
        ensureBackgroundThread {
            val newDrafts = HashMap<Long, String>()
            fetchDrafts(newDrafts)
            activity.runOnUiThread {
                if (drafts.hashCode() != newDrafts.hashCode()) {
                    drafts = newDrafts
                    notifyDataSetChanged()
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateCategoryColors() {
        ensureBackgroundThread {
            val newColors = HashMap<String, Int>()
            activity.getAllCategories().forEach {
                if (it.name.isNotBlank()) {
                    newColors[normalizeCategoryKey(it.name)] = it.color
                }
            }

            activity.runOnUiThread {
                if (categoryColors.hashCode() != newColors.hashCode()) {
                    categoryColors = newColors
                    notifyDataSetChanged()
                }
            }
        }
    }

    override fun getSelectableItemCount() = itemCount

    protected fun getSelectedItems() = currentList.filter {
        selectedKeys.contains(it.hashCode())
    } as ArrayList<Conversation>

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = getItem(position)
        holder.bindView(
            conversation,
            allowSingleClick = true,
            allowLongClick = true
        ) { itemView, _ ->
            setupView(itemView, conversation)
        }
        bindViewHolder(holder)
    }

    override fun getItemId(position: Int) = getItem(position).threadId

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val itemView = ItemConversationBinding.bind(holder.itemView)
            Glide.with(activity).clear(itemView.conversationImage)
        }
    }

    private fun fetchDrafts(drafts: HashMap<Long, String>) {
        drafts.clear()
        for ((threadId, draft) in activity.getAllDrafts()) {
            drafts[threadId] = draft
        }
    }

    private fun setupView(view: View, conversation: Conversation) {
        ItemConversationBinding.bind(view).apply {
            root.setupViewBackground(activity)

            val tintColor = (activity as? MainActivity)?.getConversationRowTintColor(conversation)
            if (tintColor == null) {
                conversationTintOverlay.beGone()
            } else {
                conversationTintOverlay.setBackgroundColor(tintColor.adjustAlpha(0.12f))
                conversationTintOverlay.beVisibleIf(true)
            }

            val smsDraft = drafts[conversation.threadId]
            draftIndicator.beVisibleIf(!smsDraft.isNullOrEmpty())
            draftIndicator.setTextColor(properPrimaryColor)

            pinIndicator.beVisibleIf(
                activity.config.pinnedConversations.contains(conversation.threadId.toString())
            )
            pinIndicator.applyColorFilter(textColor)

            conversationFrame.isSelected = selectedKeys.contains(conversation.hashCode())

            conversationAddress.apply {
                text = conversation.title
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.2f)
            }

            val categoryNames = parseCategoryNames(conversation.category)
            if (categoryNames.isNotEmpty()) {
                renderCategoryChips(categoryLabels, categoryNames)
                categoryLabels.visibility = View.VISIBLE
            } else {
                categoryLabels.removeAllViews()
                categoryLabels.visibility = View.GONE
            }

            conversationBodyShort.apply {
                text = smsDraft ?: conversation.snippet
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.9f)
            }

            conversationDate.apply {
                text = (conversation.date * 1000L).formatDateOrTime(
                    context = context,
                    hideTimeOnOtherDays = true,
                    showCurrentYear = false
                )

                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            val isUnread = !conversation.read
            val style = if (isUnread) {
                conversationBodyShort.alpha = 1f
                if (conversation.isScheduled) Typeface.BOLD_ITALIC else Typeface.BOLD
            } else {
                conversationBodyShort.alpha = 0.7f
                if (conversation.isScheduled) Typeface.ITALIC else Typeface.NORMAL
            }
            val customTypeface = FontHelper.getTypeface(activity)
            conversationAddress.setTypeface(customTypeface, style)
            conversationBodyShort.setTypeface(customTypeface, style)
            conversationDate.setTypeface(customTypeface, style)

            arrayListOf(conversationAddress, conversationBodyShort, conversationDate).forEach {
                it.setTextColor(textColor)
            }

            setupBadgeCount(unreadCountBadge, isUnread, conversation.unreadCount)
            // at group conversations we use an icon as the placeholder, not any letter
            val placeholder = if (conversation.isGroupConversation) {
                SimpleContactsHelper(activity).getColoredGroupIcon(conversation.title)
            } else {
                null
            }

            SimpleContactsHelper(activity).loadContactImage(
                path = conversation.photoUri,
                imageView = conversationImage,
                placeholderName = conversation.title,
                placeholderImage = placeholder
            )
        }
    }

    private fun setupBadgeCount(view: TextView, isUnread: Boolean, count: Int) {
        view.apply {
            beVisibleIf(isUnread)
            if (isUnread) {
                text = when {
                    count > MAX_UNREAD_BADGE_COUNT -> "$MAX_UNREAD_BADGE_COUNT+"
                    count == 0 -> ""
                    else -> count.toString()
                }
                setTextColor(properPrimaryColor.getContrastColor())
                background?.applyColorFilter(properPrimaryColor)
            }
        }
    }

    private fun normalizeCategoryKey(name: String): String {
        return name.trim().lowercase(Locale.ROOT)
    }

    private fun parseCategoryNames(raw: String): List<String> {
        return raw
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { normalizeCategoryKey(it) }
    }

    private fun renderCategoryChips(container: LinearLayout, names: List<String>) {
        container.removeAllViews()
        // Filter out any category names that no longer exist in the DB so deleted categories
        // don't continue to appear in the conversation list until DB rows are reconciled.
        val existingCategoryKeys = categoryColors.keys

        val visibleNames = names
            .filter { name -> existingCategoryKeys.isEmpty() || existingCategoryKeys.contains(normalizeCategoryKey(name)) }
            .take(MAX_VISIBLE_CATEGORY_CHIPS)

        visibleNames.forEach { name ->
            android.util.Log.d("CategoryDebug", "renderCategoryChips: adding chip for '$name'")
            val key = normalizeCategoryKey(name)
            val color = categoryColors[key] ?: properPrimaryColor

            container.addView(createCategoryChip(name, color))
        }

        val hiddenCount = names.size - visibleNames.size
        if (hiddenCount > 0) {
            container.addView(createCategoryChip("+$hiddenCount", properPrimaryColor))
        }
    }

    private fun createCategoryChip(textValue: String, color: Int): TextView {
        val horizontalPadding = 8.dp
        val verticalPadding = 2.dp
        val marginEnd = 4.dp

        return TextView(activity).apply {
            text = textValue
            maxWidth = 140.dp
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            // Make chip background a light variant and use a colored stroke so it visually matches
            // the neutral "SuggestionChip" style used in Compose
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.7f)

            // Lighten the color for the background (mix with white)
            fun lighten(c: Int, factor: Float): Int {
                val a = (c shr 24) and 0xff
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                val nr = (r + ((255 - r) * factor)).toInt().coerceIn(0, 255)
                val ng = (g + ((255 - g) * factor)).toInt().coerceIn(0, 255)
                val nb = (b + ((255 - b) * factor)).toInt().coerceIn(0, 255)
                return (a shl 24) or (nr shl 16) or (ng shl 8) or nb
            }

            val backgroundColor = lighten(color, 0.82f)
            val strokeWidth = (1.dp)
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12.dp.toFloat()
                setColor(backgroundColor)
                setStroke(strokeWidth, color)
            }
            background = drawable
            // Use a readable contrast color for the label
            setTextColor(color.getContrastColor())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = marginEnd
            }
        }
    }

    private val Int.dp: Int
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            toFloat(),
            activity.resources.displayMetrics
        ).toInt()

    override fun onChange(position: Int) = currentList.getOrNull(position)?.title ?: ""

    private fun saveRecyclerViewState() {
        recyclerViewState = recyclerView.layoutManager?.onSaveInstanceState()
    }

    private fun restoreRecyclerViewState() {
        recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
    }

    private class ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return Conversation.areItemsTheSame(oldItem, newItem)
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return Conversation.areContentsTheSame(oldItem, newItem)
        }
    }

    companion object {
        private const val MAX_UNREAD_BADGE_COUNT = 99
        private const val MAX_VISIBLE_CATEGORY_CHIPS = 3
    }
}
