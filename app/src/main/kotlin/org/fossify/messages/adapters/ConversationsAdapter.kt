package org.fossify.messages.adapters

import android.content.Intent
import android.text.TextUtils
import android.view.Menu
import androidx.recyclerview.widget.ItemTouchHelper
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.addBlockedNumber
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.MainActivity
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.dialogs.DeleteConfirmationDialog
import org.fossify.messages.dialogs.RenameConversationDialog
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.deleteConversation
import org.fossify.messages.extensions.dialNumber
import org.fossify.messages.extensions.launchConversationDetails
import org.fossify.messages.extensions.markThreadMessagesRead
import org.fossify.messages.extensions.markThreadMessagesUnread
import org.fossify.messages.extensions.renameConversation
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.helpers.INBOX_SWIPE_ACTION_ARCHIVE
import org.fossify.messages.helpers.INBOX_SWIPE_ACTION_BLOCK
import org.fossify.messages.helpers.INBOX_SWIPE_ACTION_DELETE
import org.fossify.messages.helpers.INBOX_SWIPE_ACTION_NONE
import org.fossify.messages.helpers.INBOX_SWIPE_ACTION_TOGGLE_READ_STATUS
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.messaging.isShortCodeWithLetters
import org.fossify.messages.models.Conversation

class ConversationsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    onRefresh: () -> Unit,
    itemClick: (Any) -> Unit
) : BaseConversationsAdapter(activity, recyclerView, onRefresh, itemClick) {
    override fun getActionMenuId() = R.menu.cab_conversations

    fun getSelectedConversations() = getSelectedItems()

    fun assignFolderToSelectedConversations(folderId: String) {
        if (selectedKeys.isEmpty()) {
            return
        }

        val selectedConversations = getSelectedItems()
        ensureBackgroundThread {
            selectedConversations.forEach { conversation ->
                activity.config.setConversationFolder(conversation.threadId, folderId)
            }

            activity.runOnUiThread {
                refreshConversationsAndFinishActMode()
            }
        }
    }

    override fun onActionModeCreated() {
        super.onActionModeCreated()
        (activity as? MainActivity)?.updateSelectionBottomBar(selectedKeys.size)
    }

    override fun onActionModeDestroyed() {
        super.onActionModeDestroyed()
        (activity as? MainActivity)?.updateSelectionBottomBar(0)
    }

    override fun prepareActionMode(menu: Menu) {
        val selectedItems = getSelectedItems()
        val isSingleSelection = isOneItemSelected()
        val selectedConversation = selectedItems.firstOrNull() ?: return
        val isGroupConversation = selectedConversation.isGroupConversation
        val archiveAvailable = activity.config.isArchiveAvailable

        menu.apply {
            findItem(R.id.cab_block_number).title =
                activity.getText(org.fossify.commons.R.string.block_number)
            findItem(R.id.cab_add_number_to_contact).isVisible =
                isSingleSelection && !isGroupConversation
            findItem(R.id.cab_dial_number).isVisible =
                isSingleSelection && !isGroupConversation &&
                        !isShortCodeWithLetters(selectedConversation.phoneNumber)
            findItem(R.id.cab_copy_number).isVisible = isSingleSelection && !isGroupConversation
            findItem(R.id.cab_rename_conversation).isVisible =
                isSingleSelection && isGroupConversation
            findItem(R.id.cab_conversation_details).isVisible = isSingleSelection
            findItem(R.id.cab_mark_as_read).isVisible = selectedItems.any { !it.read }
            findItem(R.id.cab_mark_as_unread).isVisible = selectedItems.any { it.read }
            findItem(R.id.cab_archive).isVisible = archiveAvailable
            checkPinBtnVisibility(this)
        }
        (activity as? MainActivity)?.updateSelectionBottomBar(selectedKeys.size)
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_add_number_to_contact -> addNumberToContact()
            R.id.cab_block_number -> tryBlocking()
            R.id.cab_dial_number -> dialNumber()
            R.id.cab_copy_number -> copyNumberToClipboard()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_archive -> askConfirmArchive()
            R.id.cab_rename_conversation -> renameConversation(getSelectedItems().first())
            R.id.cab_conversation_details ->
                activity.launchConversationDetails(getSelectedItems().first().threadId)

            R.id.cab_mark_as_read -> {
                val items = getSelectedItems()
                if (items.any { !it.read }) {
                    markAsRead()
                } else {
                    markAsUnread()
                }
            }
            R.id.cab_mark_as_unread -> markAsUnread()
            R.id.cab_pin_conversation -> {
                val pinnedConversations = activity.config.pinnedConversations
                val allPinned = getSelectedItems().all { pinnedConversations.contains(it.threadId.toString()) }
                pinConversation(!allPinned)
            }
            R.id.cab_unpin_conversation -> pinConversation(false)
            R.id.cab_set_category -> showFolderPickerDialog()
            R.id.cab_select_all -> selectAll()
        }
    }

    private fun showFolderPickerDialog() {
        val folders = (activity as? MainActivity)?.savedViewsStore?.getViews()
            ?.filter { it.id != org.fossify.messages.models.SavedView.MAIN_VIEW_ID } ?: return
        
        if (folders.isEmpty()) {
            activity.toast(R.string.no_folders)
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(R.string.assign_folder)
            .setItems(folders.map { it.title }.toTypedArray()) { _, which ->
                val folder = folders[which]
                assignFolderToSelectedConversations(folder.id)
            }
            .setNeutralButton(R.string.clear) { _, _ ->
                val selectedConversations = getSelectedItems()
                ensureBackgroundThread {
                    selectedConversations.forEach {
                        activity.config.setConversationFolder(it.threadId, null)
                    }
                    activity.runOnUiThread {
                        refreshConversationsAndFinishActMode()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun canHandleSwipe() = selectedKeys.isEmpty()

    fun getSwipeActionForPosition(position: Int, direction: Int): Int {
        if (!canHandleSwipe()) return INBOX_SWIPE_ACTION_NONE

        val conversation = currentList.getOrNull(position) ?: return INBOX_SWIPE_ACTION_NONE
        val configuredAction = when (direction) {
            ItemTouchHelper.START -> activity.config.inboxSwipeStartAction
            ItemTouchHelper.END -> activity.config.inboxSwipeEndAction
            else -> INBOX_SWIPE_ACTION_NONE
        }

        return if (canApplySwipeAction(conversation, configuredAction)) {
            configuredAction
        } else {
            INBOX_SWIPE_ACTION_NONE
        }
    }

    fun onSwiped(position: Int, direction: Int): Boolean {
        val conversation = currentList.getOrNull(position) ?: return false
        val action = getSwipeActionForPosition(position, direction)
        if (action == INBOX_SWIPE_ACTION_NONE) {
            notifyItemChanged(position)
            return false
        }

        when (action) {
            INBOX_SWIPE_ACTION_ARCHIVE -> archiveConversation(conversation)
            INBOX_SWIPE_ACTION_TOGGLE_READ_STATUS -> toggleConversationReadStatus(conversation, position)
            INBOX_SWIPE_ACTION_DELETE -> deleteConversationBySwipe(conversation, position)
            INBOX_SWIPE_ACTION_BLOCK -> blockConversationBySwipe(conversation)
            else -> {
                notifyItemChanged(position)
                return false
            }
        }

        return true
    }

    private fun canApplySwipeAction(conversation: Conversation, action: Int): Boolean {
        return when (action) {
            INBOX_SWIPE_ACTION_NONE,
            INBOX_SWIPE_ACTION_TOGGLE_READ_STATUS,
            INBOX_SWIPE_ACTION_DELETE -> true

            INBOX_SWIPE_ACTION_ARCHIVE -> activity.config.isArchiveAvailable
            INBOX_SWIPE_ACTION_BLOCK -> !conversation.isGroupConversation && conversation.phoneNumber.isNotBlank()
            else -> false
        }
    }

    private fun archiveConversation(conversation: Conversation) {
        ensureBackgroundThread {
            activity.updateConversationArchivedStatus(conversation.threadId, true)
            activity.notificationManager.cancel(conversation.threadId.hashCode())
            val updatedList = currentList.toMutableList().apply { remove(conversation) }
            activity.runOnUiThread {
                submitList(updatedList)
                if (updatedList.isEmpty()) {
                    refreshConversations()
                }
            }
        }
    }
    private fun deleteConversationBySwipe(conversation: Conversation, position: Int) {
        // Reset swipe state immediately, then delete only after explicit confirmation.
        notifyItemChanged(position)
        DeleteConfirmationDialog(
            activity = activity,
            message = activity.getString(R.string.delete_whole_conversation_confirmation),
            showSkipRecycleBinOption = false,
        ) {
            ensureBackgroundThread {
                activity.deleteConversation(conversation.threadId)
                activity.notificationManager.cancel(conversation.threadId.hashCode())
                val updatedList = currentList.toMutableList().apply { remove(conversation) }
                activity.runOnUiThread {
                    submitList(updatedList)
                    if (updatedList.isEmpty()) {
                        refreshConversations()
                    }
                }
            }
        }
    }

    private fun blockConversationBySwipe(conversation: Conversation) {
        ensureBackgroundThread {
            activity.addBlockedNumber(conversation.phoneNumber)
            val updatedList = currentList.toMutableList().apply { remove(conversation) }
            activity.runOnUiThread {
                submitList(updatedList)
                if (updatedList.isEmpty()) {
                    refreshConversations()
                }
            }
        }
    }

    private fun toggleConversationReadStatus(conversation: Conversation, position: Int) {
        ensureBackgroundThread {
            if (conversation.read) {
                activity.markThreadMessagesUnread(conversation.threadId)
            } else {
                activity.markThreadMessagesRead(conversation.threadId)
            }

            val updatedList = currentList.toMutableList()
            val updatedConversation = conversation.copy(read = !conversation.read)
            if (position in updatedList.indices) {
                updatedList[position] = updatedConversation
            }

            activity.runOnUiThread {
                submitList(updatedList)
            }
        }
    }

    private fun tryBlocking() {
            askConfirmBlock()
    }

    private fun askConfirmBlock() {
        val numbers = getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber }
        val numbersString = TextUtils.join(", ", numbers)
        val question = String.format(
            resources.getString(org.fossify.commons.R.string.block_confirmation),
            numbersString
        )

        ConfirmationDialog(activity, question) {
            blockNumbers()
        }
    }

    private fun blockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val numbersToBlock = getSelectedItems()
        val newList = currentList.toMutableList().apply { removeAll(numbersToBlock) }

        ensureBackgroundThread {
            numbersToBlock.map { it.phoneNumber }.forEach { number ->
                activity.addBlockedNumber(number)
            }

            activity.runOnUiThread {
                submitList(newList)
                finishActMode()
            }
        }
    }

    private fun dialNumber() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        activity.dialNumber(conversation.phoneNumber) {
            finishActMode()
        }
    }

    private fun copyNumberToClipboard() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        activity.copyToClipboard(conversation.phoneNumber)
        finishActMode()
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)

        val baseString = org.fossify.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteConversations()
            }
        }
    }

    private fun askConfirmArchive() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)

        val baseString = R.string.archive_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                archiveConversations()
            }
        }
    }

    private fun archiveConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove =
            currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        conversationsToRemove.forEach {
            activity.updateConversationArchivedStatus(it.threadId, true)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }

        val newList = try {
            currentList.toMutableList().apply { removeAll(conversationsToRemove) }
        } catch (_: Exception) {
            currentList.toMutableList()
        }

        activity.runOnUiThread {
            if (newList.none { selectedKeys.contains(it.hashCode()) }) {
                refreshConversations()
                finishActMode()
            } else {
                submitList(newList)
                if (newList.isEmpty()) {
                    refreshConversations()
                }
            }
        }
    }

    private fun deleteConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove =
            currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        conversationsToRemove.forEach {
            activity.deleteConversation(it.threadId)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }

        val newList = try {
            currentList.toMutableList().apply { removeAll(conversationsToRemove) }
        } catch (_: Exception) {
            currentList.toMutableList()
        }

        activity.runOnUiThread {
            if (newList.none { selectedKeys.contains(it.hashCode()) }) {
                refreshConversations()
                finishActMode()
            } else {
                submitList(newList)
                if (newList.isEmpty()) {
                    refreshConversations()
                }
            }
        }
    }

    private fun renameConversation(conversation: Conversation) {
        RenameConversationDialog(activity, conversation) {
            ensureBackgroundThread {
                val updatedConv = activity.renameConversation(conversation, newTitle = it)
                activity.runOnUiThread {
                    finishActMode()
                    currentList.toMutableList().apply {
                        set(indexOf(conversation), updatedConv)
                        updateConversations(this as ArrayList<Conversation>)
                    }
                }
            }
        }
    }

    private fun markAsRead() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsMarkedAsRead =
            currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        ensureBackgroundThread {
            conversationsMarkedAsRead.filter { conversation -> !conversation.read }.forEach {
                activity.markThreadMessagesRead(it.threadId)
            }

            refreshConversationsAndFinishActMode()
        }
    }

    private fun markAsUnread() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsMarkedAsUnread =
            currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        ensureBackgroundThread {
            conversationsMarkedAsUnread.filter { conversation -> conversation.read }.forEach {
                activity.markThreadMessagesUnread(it.threadId)
            }

            refreshConversationsAndFinishActMode()
        }
    }

    private fun addNumberToContact() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, conversation.phoneNumber)
            activity.launchActivityIntent(this)
        }
    }

    private fun pinConversation(pin: Boolean) {
        val conversations = getSelectedItems()
        if (conversations.isEmpty()) {
            return
        }

        if (pin) {
            activity.config.addPinnedConversations(conversations)
        } else {
            activity.config.removePinnedConversations(conversations)
        }

        getSelectedItemPositions().forEach {
            notifyItemChanged(it)
        }
        refreshConversationsAndFinishActMode()
    }

    private fun checkPinBtnVisibility(menu: Menu) {
        val pinnedConversations = activity.config.pinnedConversations
        val selectedConversations = getSelectedItems()
        menu.findItem(R.id.cab_pin_conversation).isVisible =
            selectedConversations.any { !pinnedConversations.contains(it.threadId.toString()) }
        menu.findItem(R.id.cab_unpin_conversation).isVisible =
            selectedConversations.all { pinnedConversations.contains(it.threadId.toString()) }
    }

    private fun refreshConversationsAndFinishActMode() {
        activity.runOnUiThread {
            refreshConversations()
            finishActMode()
        }
    }
}
