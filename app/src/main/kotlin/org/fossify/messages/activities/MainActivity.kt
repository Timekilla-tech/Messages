package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.provider.Telephony
import android.text.TextUtils
import android.widget.EditText
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.updatePadding
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkAppSideloading
import org.fossify.commons.extensions.checkWhatsNew
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.LICENSE_EVENT_BUS
import org.fossify.commons.helpers.LICENSE_INDICATOR_FAST_SCROLL
import org.fossify.commons.helpers.LICENSE_SMS_MMS
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.PERMISSION_READ_SMS
import org.fossify.commons.helpers.PERMISSION_SEND_SMS
import org.fossify.commons.helpers.SHORT_ANIMATION_DURATION
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.Release
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.adapters.ConversationsAdapter
import org.fossify.messages.adapters.SearchResultsAdapter
import org.fossify.messages.databinding.ActivityMainBinding
import org.fossify.messages.extensions.categoryDB
import org.fossify.messages.extensions.checkAndDeleteOldRecycleBinMessages
import org.fossify.messages.extensions.clearAllMessagesIfNeeded
import org.fossify.messages.extensions.clearExpiredScheduledMessages
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getAllCategories
import org.fossify.messages.extensions.getConversations
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.insertOrUpdateConversation
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.refreshConversationCategoryLabel
import org.fossify.messages.helpers.ConversationAgeHeaderDecoration
import org.fossify.messages.helpers.INBOX_SWIPE_ACTION_ARCHIVE
import org.fossify.messages.helpers.INBOX_SWIPE_ACTION_BLOCK
import org.fossify.messages.helpers.INBOX_SWIPE_ACTION_DELETE
import org.fossify.messages.helpers.INBOX_SWIPE_ACTION_NONE
import org.fossify.messages.helpers.INBOX_SWIPE_ACTION_TOGGLE_READ_STATUS
import org.fossify.messages.helpers.SCREEN_VIEW_MODE_SINGLE
import org.fossify.messages.helpers.SCREEN_VIEW_MODE_TWO_PANE
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
import org.fossify.messages.helpers.SavedViewsStore
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.Category
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.fossify.messages.models.Message
import org.fossify.messages.models.SavedView
import org.fossify.messages.models.SavedViewConfig
import org.fossify.messages.models.SearchResult
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Locale

class MainActivity : SimpleActivity() {
    override var isSearchBarEnabled = true

    private val SAVED_SCROLL_POSITION = "saved_scroll_position"
    private val SAVED_SEARCH_TEXT = "saved_search_text"

    private var storedTextColor = 0
    private var storedFontSize = 0
    private var lastSearchedText = ""
    private val activeTagFilters = linkedSetOf<String>()
    private var activeSavedView = SavedView.mainView()
    private var conversationsBaseBottomPadding = 0
    private var bus: EventBus? = null
    private var ageHeaderDecoration: ConversationAgeHeaderDecoration? = null
    private var inboxSwipeHelper: ItemTouchHelper? = null
    val savedViewsStore by lazy { SavedViewsStore(config) }
    private val savedViewMenuIdOffset = 20_000
    private val savedViewIconOptions = listOf(
        SavedViewIconOption(SavedView.MAIN_VIEW_ICON, R.string.view_icon_home, R.drawable.ic_home_vector),
        SavedViewIconOption(SavedView.DEFAULT_CUSTOM_VIEW_ICON, R.string.view_icon_filter, R.drawable.ic_folder),
        SavedViewIconOption("ic_archive_vector", R.string.view_icon_archive, R.drawable.ic_archive_vector),
        SavedViewIconOption("ic_calendar_month_vector", R.string.view_icon_schedule, R.drawable.ic_calendar_month_vector),
        SavedViewIconOption("ic_image_vector", R.string.view_icon_image, R.drawable.ic_image_vector),
        SavedViewIconOption("ic_music_vector", R.string.view_icon_music, R.drawable.ic_music_vector),
        SavedViewIconOption("ic_pin_vector", R.string.view_icon_pin, R.drawable.ic_pin_vector),
    )

    private val binding by viewBinding(ActivityMainBinding::inflate)

    private val defaultAppLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            askPermissions()
        } else {
            finish()
        }
    }

    // Two-pane state (runtime adaptive using WindowManager / androidx.window)
    private var isTwoPaneMode: Boolean = false
    private var detailFragment: ConversationDetailFragment? = null
    private var lastWindowLayoutInfo: WindowLayoutInfo? = null

    private fun onWindowLayoutInfoChanged(info: WindowLayoutInfo) {
        lastWindowLayoutInfo = info
        updateTwoPaneMode()
    }

    private fun updateTwoPaneMode() {
        val detailContainerExists = findViewById<android.view.View?>(R.id.thread_detail_container) != null
        val foldingFeature = lastWindowLayoutInfo
            ?.displayFeatures
            ?.filterIsInstance<FoldingFeature>()
            ?.firstOrNull()
        val hasSeparatingFold = foldingFeature?.isSeparating == true

        val shouldBeTwoPane = when (config.screenViewMode) {
            SCREEN_VIEW_MODE_SINGLE -> false
            SCREEN_VIEW_MODE_TWO_PANE -> detailContainerExists
            else -> detailContainerExists || hasSeparatingFold
        }

        if (shouldBeTwoPane != isTwoPaneMode) {
            isTwoPaneMode = shouldBeTwoPane
        }

        val detailContainer = findViewById<android.view.View?>(R.id.thread_detail_container)
        detailContainer?.visibility = if (isTwoPaneMode) android.view.View.VISIBLE else android.view.View.GONE
    }
    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        activeSavedView = savedViewsStore.getActiveViewOrMain()
        syncTagFiltersFromActiveView()
        setupOptionsMenu()
        refreshMenuItems()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))
        binding.conversationsList.post {
            conversationsBaseBottomPadding = binding.conversationsList.paddingBottom
            setupSavedViewsBottomBar()
            setupSelectionBottomBar()
            
            // Restore scroll position if it was saved (e.g., during fold/unfold)
            savedInstanceState?.getInt(SAVED_SCROLL_POSITION, -1)?.let { position ->
                if (position >= 0) {
                    binding.conversationsList.post {
                        binding.conversationsList.scrollToPosition(position)
                    }
                }
            }
        }

        // Restore search text if it was saved
        savedInstanceState?.getString(SAVED_SEARCH_TEXT)?.let {
            if (it.isNotEmpty()) {
                lastSearchedText = it
            }
        }

        checkAndDeleteOldRecycleBinMessages()
        clearAllMessagesIfNeeded {
            loadMessages()
        }

        if (checkAppSideloading()) {
            return
        }

        updateTwoPaneMode()

        // Setup window layout tracking to adapt to foldable / large screens at runtime
        try {
            val tracker = WindowInfoTracker.getOrCreate(this)
            lifecycleScope.launch {
                tracker.windowLayoutInfo(this@MainActivity).collect { info ->
                    onWindowLayoutInfoChanged(info)
                }
            }
        } catch (_: Exception) {
            // Window manager may not be available on older platforms - ignore silently
        }
    }

    override fun onResume() {
        super.onResume()
        refreshActiveSavedViewState()
        updateMenuColors()
        updateTwoPaneMode()

        getOrCreateConversationsAdapter().apply {
            if (storedTextColor != getProperTextColor()) {
                updateTextColor(getProperTextColor())
            }

            if (storedFontSize != config.fontSize) {
                updateFontSize()
            }

            updateDrafts()
        }

        updateTextColors(binding.mainCoordinator)
        binding.searchHolder.setBackgroundColor(getProperBackgroundColor())

        val properPrimaryColor = getProperPrimaryColor()
        binding.noConversationsPlaceholder2.setTextColor(properPrimaryColor)
        binding.noConversationsPlaceholder2.underlineText()
        binding.conversationsFastscroller.updateColors(properPrimaryColor)
        binding.conversationsProgressBar.setIndicatorColor(properPrimaryColor)
        binding.conversationsProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)
        checkShortcut()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            true
        } else {
            appLockManager.lock()
            false
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.requireToolbar().inflateMenu(R.menu.menu_main)
        binding.mainMenu.setupMenu()

        binding.mainMenu.onSearchClosedListener = {
            fadeOutSearch()
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            if (text.isNotEmpty()) {
                if (binding.searchHolder.alpha < 1f) {
                    binding.searchHolder.fadeIn()
                }
            } else {
                fadeOutSearch()
            }
            searchTextChanged(text)
        }

        binding.mainMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.filter_by_tag -> showTagFilterDialog()
                R.id.saved_views_picker -> showSavedViewPickerDialog()
                R.id.show_recycle_bin -> launchRecycleBin()
                R.id.show_archived -> launchArchivedConversations()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun refreshMenuItems() {
        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.show_recycle_bin).isVisible = config.useRecycleBin
            findItem(R.id.show_archived).isVisible = config.isArchiveAvailable
            findItem(R.id.saved_views_picker)?.title = getString(R.string.saved_views_current, activeSavedView.title)
        }
    }

    private fun refreshActiveSavedViewState() {
        activeSavedView = savedViewsStore.getActiveViewOrMain()
        syncTagFiltersFromActiveView()
        refreshMenuItems()
        setupSavedViewsBottomBar()
    }

    private fun setupSavedViewsBottomBar() {
        val bar = binding.savedViewsBottomBar
        if (binding.selectionBottomBar?.visibility == android.view.View.GONE) {
            bar.beVisible()
        } else {
            bar.beGone()
        }

        val views = savedViewsStore.getViews()
        val menu = bar.menu
        menu.clear()

        views.forEachIndexed { index, view ->
            val menuItem = menu.add(Menu.NONE, savedViewMenuIdOffset + index, index, view.title)
            
            val activeColor = view.config.color ?: getProperPrimaryColor()
            val inactiveColor = (view.config.color ?: getProperTextColor()).adjustAlpha(0.3f)

            val stateListDrawable = StateListDrawable().apply {
                val openIconRes = if (view.id == SavedView.MAIN_VIEW_ID) R.drawable.ic_home_vector else R.drawable.ic_folder_open
                val closedIconRes = if (view.id == SavedView.MAIN_VIEW_ID) R.drawable.ic_home_vector else R.drawable.ic_folder

                val activeIcon = AppCompatResources.getDrawable(this@MainActivity, openIconRes)?.mutate()
                activeIcon?.let {
                    DrawableCompat.setTint(it, activeColor)
                    addState(intArrayOf(android.R.attr.state_checked), it)
                    addState(intArrayOf(android.R.attr.state_selected), it)
                }

                val inactiveIcon = AppCompatResources.getDrawable(this@MainActivity, closedIconRes)?.mutate()
                inactiveIcon?.let {
                    DrawableCompat.setTint(it, inactiveColor)
                    addState(intArrayOf(), it)
                }
            }
            menuItem.icon = stateListDrawable
        }

        // Disable framework tinting to use our manual state list colors
        bar.itemIconTintList = null

        val activeId = activeSavedView.id
        val selectedIndex = views.indexOfFirst { it.id == activeId }.takeIf { it >= 0 } ?: 0
        bar.setOnItemSelectedListener(null)
        bar.selectedItemId = savedViewMenuIdOffset + selectedIndex

        // Ensure text color also reflects the selection state
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(android.R.attr.state_selected), intArrayOf())
        val textColors = intArrayOf(getProperPrimaryColor(), getProperPrimaryColor(), getProperTextColor().adjustAlpha(0.6f))
        bar.itemTextColor = android.content.res.ColorStateList(states, textColors)

        bar.setOnItemSelectedListener { item ->
            val viewIndex = item.itemId - savedViewMenuIdOffset
            val selectedView = views.getOrNull(viewIndex) ?: return@setOnItemSelectedListener false
            switchToSavedView(selectedView.id)
            true
        }

        bar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateBottomBarDependentPadding()
        }
        bar.post { updateBottomBarDependentPadding() }
    }

    private fun setupSelectionBottomBar() {
        val selectionBottomBar = binding.selectionBottomBar ?: return

        selectionBottomBar.setOnItemSelectedListener { item ->
            getOrCreateConversationsAdapter().actionItemPressed(item.itemId)
            false
        }

        selectionBottomBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateBottomBarDependentPadding()
        }
    }

    fun updateSelectionBottomBar(selectedCount: Int) {
        val isSelecting = selectedCount > 0
        binding.selectionBottomBar?.beVisibleIf(isSelecting)
        binding.savedViewsBottomBar.beGoneIf(isSelecting)
        
        if (isSelecting) {
            val menu = binding.selectionBottomBar?.menu ?: return
            val adapter = getOrCreateConversationsAdapter()
            val selectedItems = adapter.getSelectedConversations()
            
            val archiveAvailable = config.isArchiveAvailable
            val pinnedConversations = config.pinnedConversations

            menu.findItem(R.id.cab_archive)?.isVisible = archiveAvailable
            
            val hasUnread = selectedItems.any { !it.read }
            val readItem = menu.findItem(R.id.cab_mark_as_read)
            if (readItem != null) {
                if (hasUnread) {
                    readItem.title = getString(R.string.mark_as_read)
                    readItem.setIcon(R.drawable.ic_check_double_vector)
                } else {
                    readItem.title = getString(R.string.mark_as_unread)
                    readItem.setIcon(R.drawable.ic_check_double_vector) 
                }
            }

            val allPinned = selectedItems.all { pinnedConversations.contains(it.threadId.toString()) }
            val pinItem = menu.findItem(R.id.cab_pin_conversation)
            if (pinItem != null) {
                if (allPinned) {
                    pinItem.title = getString(R.string.unpin_conversation)
                    pinItem.setIcon(R.drawable.ic_unpin_vector)
                } else {
                    pinItem.title = getString(R.string.pin_conversation)
                    pinItem.setIcon(R.drawable.ic_pin_vector)
                }
            }
        }
    }

    private fun updateBottomBarDependentPadding() {
        val selectionBottomBar = binding.selectionBottomBar
        val barHeight = if (selectionBottomBar?.visibility == android.view.View.VISIBLE) {
            selectionBottomBar.height
        } else {
            binding.savedViewsBottomBar.height
        }
        if (barHeight == 0) {
            return
        }

        val updateLogic = {
            if (conversationsBaseBottomPadding != 0) {
                val newPadding = conversationsBaseBottomPadding + barHeight
                if (binding.conversationsList.paddingBottom != newPadding) {
                    binding.conversationsList.updatePadding(bottom = newPadding)
                }
            }

            val fabLayoutParams = binding.conversationsFab.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            val defaultFabMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
            val newBottomMargin = defaultFabMargin + barHeight
            if (fabLayoutParams.bottomMargin != newBottomMargin) {
                fabLayoutParams.bottomMargin = newBottomMargin
                binding.conversationsFab.layoutParams = fabLayoutParams
            }
        }

        if (binding.root.isInLayout) {
            binding.root.post { updateLogic() }
        } else {
            updateLogic()
        }
    }

    private fun switchToSavedView(viewId: String) {
        savedViewsStore.setActiveView(viewId)
        refreshActiveSavedViewState()
        reloadConversationsForCurrentFilter()
    }


    private fun storeStateVariables() {
        storedTextColor = getProperTextColor()
        storedFontSize = config.fontSize
    }

    private fun updateMenuColors() {
        binding.mainMenu.updateColors()
    }

    private fun loadMessages() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    askPermissions()
                } else {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    defaultAppLauncher.launch(intent)
                }
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                askPermissions()
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                defaultAppLauncher.launch(intent)
            }
        }
    }

    // while SEND_SMS and READ_SMS permissions are mandatory, READ_CONTACTS is optional.
    // If we don't have it, we just won't be able to show the contact name in some cases
    private fun askPermissions() {
        handlePermission(PERMISSION_READ_SMS) {
            if (it) {
                handlePermission(PERMISSION_SEND_SMS) {
                    if (it) {
                        handlePermission(PERMISSION_READ_CONTACTS) {
                            handleNotificationPermission { granted ->
                                if (!granted) {
                                    PermissionRequiredDialog(
                                        activity = this,
                                        textId = org.fossify.commons.R.string.allow_notifications_incoming_messages,
                                        positiveActionCallback = { openNotificationSettings() })
                                }
                            }

                            initMessenger()
                            bus = EventBus.getDefault()
                            try {
                                bus!!.register(this)
                            } catch (_: Exception) {
                            }
                        }
                    } else {
                        finish()
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun initMessenger() {
        checkWhatsNewDialog()
        storeStateVariables()
        getCachedConversations()
        binding.noConversationsPlaceholder2.setOnClickListener {
            launchNewConversation()
        }

        binding.conversationsFab.setOnClickListener {
            launchNewConversation()
        }
    }

    private fun getCachedConversations() {
        ensureBackgroundThread {
            var conversations = try {
                conversationsDB.getNonArchived().toMutableList() as ArrayList<Conversation>
            } catch (_: Exception) {
                ArrayList()
            }

            conversations.forEach { conversation ->
                refreshConversationCategoryLabel(conversation.threadId)
            }

            conversations = try {
                conversationsDB.getNonArchived().toMutableList() as ArrayList<Conversation>
            } catch (_: Exception) {
                ArrayList()
            }

            val archived = try {
                conversationsDB.getAllArchived()
            } catch (_: Exception) {
                listOf()
            }

            runOnUiThread {
                setupConversations(conversations, cached = true)
                getNewConversations(
                    (conversations + archived).toMutableList() as ArrayList<Conversation>
                )
            }
            conversations.forEach {
                clearExpiredScheduledMessages(it.threadId)
            }
        }
    }

    private fun getNewConversations(cachedConversations: ArrayList<Conversation>) {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val conversations = getConversations(privateContacts = privateContacts)

            conversations.forEach { clonedConversation ->
                val threadIds = cachedConversations.map { it.threadId }
                if (!threadIds.contains(clonedConversation.threadId)) {
                    conversationsDB.insertOrUpdate(clonedConversation)
                    cachedConversations.add(clonedConversation)
                }
            }

            cachedConversations.forEach { cachedConversation ->
                val threadId = cachedConversation.threadId

                val isTemporaryThread = cachedConversation.isScheduled
                val isConversationDeleted = !conversations.map { it.threadId }.contains(threadId)
                if (isConversationDeleted && !isTemporaryThread) {
                    conversationsDB.deleteThreadId(threadId)
                }

                val newConversation =
                    conversations.find { it.phoneNumber == cachedConversation.phoneNumber }
                if (isTemporaryThread && newConversation != null) {
                    // delete the original temporary thread and move any scheduled messages
                    // to the new thread
                    conversationsDB.deleteThreadId(threadId)
                    messagesDB.getScheduledThreadMessages(threadId)
                        .forEach { message ->
                            messagesDB.insertOrUpdate(
                                message.copy(threadId = newConversation.threadId)
                            )
                        }
                    insertOrUpdateConversation(newConversation, cachedConversation)
                }
            }

            cachedConversations.forEach { cachedConv ->
                val conv = conversations.find {
                    it.threadId == cachedConv.threadId && !Conversation.areContentsTheSame(
                        old = cachedConv, new = it
                    )
                }
                if (conv != null) {
                    insertOrUpdateConversation(conv)
                }
            }

            val allConversations = conversationsDB.getNonArchived() as ArrayList<Conversation>
            runOnUiThread {
                setupConversations(allConversations)
            }

            if (config.appRunCount == 1) {
                conversations.map { it.threadId }.forEach { threadId ->
                    val messages = getMessages(threadId, includeScheduledMessages = false)
                    messages.chunked(30).forEach { currentMessages ->
                        messagesDB.insertMessages(*currentMessages.toTypedArray())
                    }
                }
            }
        }
    }

    private fun getOrCreateConversationsAdapter(): ConversationsAdapter {
        var currAdapter = binding.conversationsList.adapter
        if (currAdapter == null) {
            hideKeyboard()
            val conversationsAdapter = ConversationsAdapter(
                activity = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )

            binding.conversationsList.adapter = conversationsAdapter
            if (ageHeaderDecoration == null) {
                ageHeaderDecoration = ConversationAgeHeaderDecoration(this) {
                    conversationsAdapter.currentList
                }
                binding.conversationsList.addItemDecoration(ageHeaderDecoration!!)
            }

            if (inboxSwipeHelper == null) {
                val swipeBackground = ColorDrawable()
                val archiveIcon = AppCompatResources.getDrawable(this, R.drawable.ic_archive_vector)
                val readIcon = AppCompatResources.getDrawable(this, R.drawable.ic_check_double_vector)
                val deleteIcon = AppCompatResources.getDrawable(this, org.fossify.commons.R.drawable.ic_delete_vector)

                inboxSwipeHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.START or ItemTouchHelper.END) {
                    override fun onMove(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder,
                    ) = false

                    override fun getSwipeDirs(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ): Int {
                        if (!conversationsAdapter.canHandleSwipe()) {
                            return 0
                        }

                        val position = viewHolder.bindingAdapterPosition
                        if (position == RecyclerView.NO_POSITION) {
                            return 0
                        }

                        var dirs = super.getSwipeDirs(recyclerView, viewHolder)
                        if (conversationsAdapter.getSwipeActionForPosition(position, ItemTouchHelper.START) == INBOX_SWIPE_ACTION_NONE) {
                            dirs = dirs and ItemTouchHelper.START.inv()
                        }
                        if (conversationsAdapter.getSwipeActionForPosition(position, ItemTouchHelper.END) == INBOX_SWIPE_ACTION_NONE) {
                            dirs = dirs and ItemTouchHelper.END.inv()
                        }
                        return dirs
                    }

                    override fun onChildDraw(
                        c: Canvas,
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        dX: Float,
                        dY: Float,
                        actionState: Int,
                        isCurrentlyActive: Boolean,
                    ) {
                        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
                            val position = viewHolder.bindingAdapterPosition
                            if (position == RecyclerView.NO_POSITION) {
                                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                                return
                            }

                            val direction = if (dX < 0) ItemTouchHelper.START else ItemTouchHelper.END
                            val action = conversationsAdapter.getSwipeActionForPosition(position, direction)
                            if (action != INBOX_SWIPE_ACTION_NONE) {
                                val itemView = viewHolder.itemView
                                val top = itemView.top
                                val bottom = itemView.bottom
                                val iconTint = getProperBackgroundColor().getContrastColor()
                                val (icon, backgroundColor) = getSwipeActionVisuals(
                                    action = action,
                                    archiveIcon = archiveIcon,
                                    readIcon = readIcon,
                                    deleteIcon = deleteIcon,
                                )

                                swipeBackground.color = backgroundColor
                                if (dX < 0) {
                                    swipeBackground.setBounds(itemView.right + dX.toInt(), top, itemView.right, bottom)
                                    swipeBackground.draw(c)
                                    drawSwipeIcon(c, icon, iconTint, itemView, alignEnd = true)
                                } else {
                                    swipeBackground.setBounds(itemView.left, top, itemView.left + dX.toInt(), bottom)
                                    swipeBackground.draw(c)
                                    drawSwipeIcon(c, icon, iconTint, itemView, alignEnd = false)
                                }
                            }
                        }

                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    }

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        conversationsAdapter.onSwiped(viewHolder.bindingAdapterPosition, direction)
                    }
                })
                inboxSwipeHelper?.attachToRecyclerView(binding.conversationsList)
            }

            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
            currAdapter = conversationsAdapter
        }
        return currAdapter as ConversationsAdapter
    }

    private fun setupConversations(
        conversations: ArrayList<Conversation>,
        cached: Boolean = false,
    ) {
        val filteredConversations = conversations
            .filter { conversationMatchesActiveView(it) }
            .toMutableList() as ArrayList<Conversation>

        val sortedConversations = filteredConversations
            .sortedWith(
                compareByDescending<Conversation> {
                    config.pinnedConversations.contains(it.threadId.toString())
                }.thenByDescending { it.date }
            ).toMutableList() as ArrayList<Conversation>

        if (cached && config.appRunCount == 1) {
            // there are no cached conversations on the first run so we show the
            // loading placeholder and progress until we are done loading from telephony
            showOrHideProgress(conversations.isEmpty())
        } else {
            showOrHideProgress(false)
            showOrHidePlaceholder(sortedConversations.isEmpty())
        }

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(sortedConversations) {
                    if (!cached) {
                        showOrHidePlaceholder(currentList.isEmpty())
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun showTagFilterDialog() {
        ensureBackgroundThread {
            val tags = getAllCategories()
                .map { it.name.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .sortedBy { it.lowercase(Locale.ROOT) }

            runOnUiThread {
                val selectedKeys = activeTagFilters.map { it.trim().lowercase(Locale.ROOT) }.toSet()
                val checkedItems = BooleanArray(tags.size) { index ->
                    tags[index].trim().lowercase(Locale.ROOT) in selectedKeys
                }

                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.filter_by_tag)
                    .setMultiChoiceItems(tags.toTypedArray(), checkedItems) { _, which, isChecked ->
                        val tag = tags[which]
                        if (isChecked) {
                            activeTagFilters.add(tag)
                        } else {
                            activeTagFilters.removeAll { it.equals(tag, ignoreCase = true) }
                        }
                    }
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        saveActiveTagFiltersToViewConfig()
                        dialog.dismiss()
                        reloadConversationsForCurrentFilter()
                    }
                    .setNeutralButton(R.string.clear) { dialog, _ ->
                        activeTagFilters.clear()
                        saveActiveTagFiltersToViewConfig()
                        dialog.dismiss()
                        reloadConversationsForCurrentFilter()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun showSavedViewPickerDialog() {
        ensureBackgroundThread {
            val views = savedViewsStore.getViews()
            val titles = views.map { it.title }
            val selectedIndex = views.indexOfFirst { it.id == activeSavedView.id }.takeIf { it >= 0 } ?: 0

            runOnUiThread {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.saved_views)
                    .setSingleChoiceItems(titles.toTypedArray(), selectedIndex) { dialog, which ->
                        val selectedView = views.getOrNull(which) ?: return@setSingleChoiceItems
                        switchToSavedView(selectedView.id)
                        dialog.dismiss()
                    }
                    .setPositiveButton(R.string.create_view) { dialog, _ ->
                        dialog.dismiss()
                        showCreateSavedViewDialog()
                    }
                    .setNeutralButton(R.string.saved_view_actions) { dialog, _ ->
                        dialog.dismiss()
                        showCurrentSavedViewActionsDialog()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun showCreateSavedViewDialog() {
        val views = savedViewsStore.getViews()
        val baseConfig = SavedViewConfig() // Start with a fresh config, don't inherit from active folder
        showSavedViewNameDialog(
            title = getString(R.string.create_view),
            initialValue = "",
            confirmLabel = getString(org.fossify.commons.R.string.ok),
        ) { viewName ->
            val positions = (0..views.size).map { it.toString() }
            AlertDialog.Builder(this)
                .setTitle(R.string.choose_position)
                .setItems(positions.toTypedArray()) { _, which ->
                    val colorOptions = listOf(
                        getString(R.string.default_label) to null,
                        getString(R.string.color_blue) to 0xFF2196F3.toInt(),
                        getString(R.string.color_green) to 0xFF4CAF50.toInt(),
                        getString(R.string.color_orange) to 0xFFFF9800.toInt(),
                        getString(R.string.color_red) to 0xFFF44336.toInt(),
                        getString(R.string.color_purple) to 0xFF9C27B0.toInt(),
                    )
                    AlertDialog.Builder(this)
                        .setTitle(R.string.choose_color)
                        .setItems(colorOptions.map { it.first }.toTypedArray()) { _, colorWhich ->
                            val selectedColor = colorOptions[colorWhich].second
                            // Start with empty tags to avoid echoing the folder name in row chips
                            val configWithColor = baseConfig.copy(color = selectedColor, tags = emptySet())
                            val createdView = savedViewsStore.createView(viewName, configWithColor, "", which)
                            switchToSavedView(createdView.id)
                        }
                        .show()
                }
                .show()
        }
    }

    private fun showCurrentSavedViewActionsDialog() {
        if (!activeSavedView.isEditable || activeSavedView.id == SavedView.MAIN_VIEW_ID) {
            toast(R.string.main_view_immutable)
            return
        }

        val options = arrayOf(
            getString(R.string.edit_view),
            getString(R.string.delete_view),
        )

        AlertDialog.Builder(this)
            .setTitle(activeSavedView.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditSavedViewDialog()
                    1 -> showDeleteSavedViewConfirmation()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditSavedViewDialog() {
        showSavedViewNameDialog(
            title = getString(R.string.edit_view),
            initialValue = activeSavedView.title,
            confirmLabel = getString(org.fossify.commons.R.string.ok),
        ) { updatedName ->
            val views = savedViewsStore.getViews().filter { it.id != SavedView.MAIN_VIEW_ID }
            val positions = (0 until views.size).map { it.toString() }
            AlertDialog.Builder(this)
                .setTitle(R.string.choose_position)
                .setItems(positions.toTypedArray()) { _, which ->
                    val colorOptions = listOf(
                        getString(R.string.default_label) to null,
                        getString(R.string.color_blue) to 0xFF2196F3.toInt(),
                        getString(R.string.color_green) to 0xFF4CAF50.toInt(),
                        getString(R.string.color_orange) to 0xFFFF9800.toInt(),
                        getString(R.string.color_red) to 0xFFF44336.toInt(),
                        getString(R.string.color_purple) to 0xFF9C27B0.toInt()
                    )
                    AlertDialog.Builder(this)
                        .setTitle(R.string.choose_color)
                        .setItems(colorOptions.map { it.first }.toTypedArray()) { _, colorWhich ->
                            val selectedColor = colorOptions[colorWhich].second
                            val updatedView = activeSavedView.copy(
                                title = updatedName,
                                iconResName = null,
                                position = which + 1, // +1 because main is at 0
                                config = activeSavedView.config.copy(color = selectedColor)
                            )
                            savedViewsStore.upsertView(updatedView)
                            switchToSavedView(updatedView.id)
                        }
                        .show()
                }
                .show()
        }
    }

    private fun showDeleteSavedViewConfirmation() {
        if (!activeSavedView.isEditable || activeSavedView.id == SavedView.MAIN_VIEW_ID) {
            toast(R.string.main_view_immutable)
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.delete_view)
            .setMessage(getString(R.string.delete_view_confirmation, activeSavedView.title))
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                val deletedId = activeSavedView.id
                val deleted = savedViewsStore.deleteView(deletedId)
                if (!deleted) {
                    return@setPositiveButton
                }
                switchToSavedView(SavedView.MAIN_VIEW_ID)
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .show()
    }

    private fun showSavedViewIconPickerDialog(currentIconResName: String, onConfirm: (String) -> Unit) {
        val iconNames = savedViewIconOptions.map { getString(it.titleRes) }
        val selectedIndex = savedViewIconOptions.indexOfFirst {
            it.iconResName.equals(currentIconResName, ignoreCase = true)
        }.takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle(R.string.choose_view_icon)
            .setSingleChoiceItems(iconNames.toTypedArray(), selectedIndex) { dialog, which ->
                onConfirm(savedViewIconOptions[which].iconResName)
                dialog.dismiss()
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .show()
    }

    private fun showSavedViewNameDialog(
        title: String,
        initialValue: String,
        confirmLabel: String,
        onConfirm: (String) -> Unit,
    ) {
        val input = EditText(this).apply {
            setText(initialValue)
            setSelection(text?.length ?: 0)
            hint = getString(R.string.enter_view_name)
            setSingleLine(true)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(confirmLabel, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text?.toString()?.trim().orEmpty()
                if (value.isEmpty()) {
                    toast(R.string.view_name_cannot_be_empty)
                    return@setOnClickListener
                }

                onConfirm(value)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun reloadConversationsForCurrentFilter() {
        ensureBackgroundThread {
            val nonArchived = try {
                conversationsDB.getNonArchived()
            } catch (_: Exception) {
                emptyList()
            }
            
            val archived = try {
                conversationsDB.getAllArchived()
            } catch (_: Exception) {
                emptyList()
            }

            val all = (nonArchived + archived).toMutableList() as ArrayList<Conversation>

            runOnUiThread {
                setupConversations(all)
            }
        }
    }

    private fun conversationMatchesActiveView(conversation: Conversation): Boolean {
        val activeId = activeSavedView.id
        if (activeId == SavedView.MAIN_VIEW_ID) {
            return true
        }

        // 1. Manual Folder Assignment check
        val manualFolderId = config.getConversationFolder(conversation.threadId)
        if (manualFolderId != null) {
            return manualFolderId == activeId
        }

        // 2. Filter-based matching
        val viewConfig = activeSavedView.config

        if (viewConfig.unreadOnly && conversation.read) {
            return false
        }

        if (viewConfig.pinnedOnly && !config.pinnedConversations.contains(conversation.threadId.toString())) {
            return false
        }

        val selectedTags = viewConfig.tags
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()

        if (selectedTags.isEmpty()) {
            return true
        }

        val conversationTags = conversation.category
            .split(",")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()

        return if (viewConfig.matchAllTags) {
            selectedTags.all { it in conversationTags }
        } else {
            selectedTags.any { it in conversationTags }
        }
    }

    private fun conversationMatchesSavedFolder(view: SavedView, conversation: Conversation): Boolean {
        if (view.id == SavedView.MAIN_VIEW_ID) {
            return false
        }

        val selectedTags = view.config.tags
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()

        if (selectedTags.isEmpty()) {
            return false
        }

        val conversationTags = conversation.category
            .split(",")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()

        return if (view.config.matchAllTags) {
            selectedTags.all { it in conversationTags }
        } else {
            selectedTags.any { it in conversationTags }
        }
    }

    private fun getPrimaryFolderForConversation(conversation: Conversation): SavedView? {
        val folders = savedViewsStore.getViews()
        
        // 1. Check if user manually assigned a folder
        config.getConversationFolder(conversation.threadId)?.let { folderId ->
            folders.firstOrNull { it.id == folderId }?.let { return it }
        }

        // 2. Fallback to containing folders (via tags)
        val containingFolders = folders.filter { view ->
            conversationMatchesSavedFolder(view, conversation)
        }

        if (containingFolders.isEmpty()) {
            return null
        }

        config.getLastUsedFolderForConversation(conversation.threadId)?.let { lastUsedId ->
            containingFolders.firstOrNull { it.id == lastUsedId }?.let { return it }
        }

        return containingFolders.minByOrNull { it.title.lowercase(Locale.ROOT) }
    }

    fun getConversationRowTintColor(conversation: Conversation): Int? {
        if (activeSavedView.id != SavedView.MAIN_VIEW_ID) {
            return activeSavedView.config.color ?: getProperPrimaryColor()
        }

        return getPrimaryFolderForConversation(conversation)?.config?.color
    }

    private fun markConversationFolderAsLastUsed(conversation: Conversation) {
        if (activeSavedView.id == SavedView.MAIN_VIEW_ID) {
            return
        }

        config.setLastUsedFolderForConversation(conversation.threadId, activeSavedView.id)
    }

    fun setUserPrimaryFolderForConversation(conversation: Conversation, folderId: String) {
        config.setUserPrimaryFolderForConversation(conversation.threadId, folderId)
    }

    private fun syncTagFiltersFromActiveView() {
        activeTagFilters.clear()
        activeTagFilters.addAll(activeSavedView.config.tags)
    }

    private fun saveActiveTagFiltersToViewConfig() {
        val normalizedTags = activeTagFilters
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .toSet()

        val updatedConfig = SavedViewConfig(
            folderId = activeSavedView.config.folderId,
            tags = normalizedTags,
            showArchived = activeSavedView.config.showArchived,
            unreadOnly = activeSavedView.config.unreadOnly,
            pinnedOnly = activeSavedView.config.pinnedOnly,
            matchAllTags = activeSavedView.config.matchAllTags,
        )

        activeSavedView = savedViewsStore.updateActiveViewConfig(updatedConfig)
        savedViewsStore.setActiveView(activeSavedView.id)
        refreshActiveSavedViewState()
    }

    private fun showOrHideProgress(show: Boolean) {
        if (show) {
            binding.conversationsProgressBar.show()
            binding.noConversationsPlaceholder.beVisible()
            binding.noConversationsPlaceholder.text = getString(R.string.loading_messages)
        } else {
            binding.conversationsProgressBar.hide()
            binding.noConversationsPlaceholder.beGone()
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        binding.conversationsFastscroller.beGoneIf(show)
        binding.noConversationsPlaceholder.beVisibleIf(show)
        
        val placeholderText = if (activeSavedView.id == SavedView.MAIN_VIEW_ID) {
            getString(R.string.no_conversations_found)
        } else {
            getString(org.fossify.commons.R.string.no_items_found)
        }
        
        binding.noConversationsPlaceholder.text = placeholderText
        binding.noConversationsPlaceholder2.beVisibleIf(show && activeSavedView.id == SavedView.MAIN_VIEW_ID)
    }

    private fun fadeOutSearch() {
        binding.searchHolder.animate()
            .alpha(0f)
            .setDuration(SHORT_ANIMATION_DURATION)
            .withEndAction {
                binding.searchHolder.beGone()
                searchTextChanged("", true)
            }.start()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateConversationsAdapter().notifyDataSetChanged()
    }

    private fun handleConversationClick(any: Any) {
        val conversation = any as Conversation
        markConversationFolderAsLastUsed(conversation)
        if (isTwoPaneMode) {
            // show conversation detail in right pane when two-pane is active
            if (detailFragment != null && detailFragment?.isAdded == true) {
                // Reuse existing fragment and update it
                detailFragment?.updateThread(conversation.threadId, conversation.title)
            } else {
                // Create a new fragment and show it
                detailFragment = ConversationDetailFragment.newInstance(conversation.threadId, conversation.title)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.thread_detail_container, detailFragment!!)
                    .setReorderingAllowed(true)
                    .commit()
            }
        } else {
            Intent(this, ThreadActivity::class.java).apply {
                putExtra(THREAD_ID, conversation.threadId)
                putExtra(THREAD_TITLE, conversation.title)
                startActivity(this)
            }
        }
    }

    private fun launchNewConversation() {
        hideKeyboard()
        Intent(this, NewConversationActivity::class.java).apply {
            startActivity(this)
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcut() {
        val appIconColor = config.appIconColor
        if (config.lastHandledShortcutColor != appIconColor) {
            val newConversation = getCreateNewContactShortcut(appIconColor)

            val manager = getSystemService(ShortcutManager::class.java)
            try {
                manager.dynamicShortcuts = listOf(newConversation)
                config.lastHandledShortcutColor = appIconColor
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.new_conversation)
        val drawable =
            AppCompatResources.getDrawable(this, org.fossify.commons.R.drawable.shortcut_plus)

        (drawable as LayerDrawable).findDrawableByLayerId(
            org.fossify.commons.R.id.shortcut_plus_background
        ).applyColorFilter(appIconColor)

        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, NewConversationActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "new_conversation")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .setRank(0)
            .build()
    }

    private fun searchTextChanged(text: String, forceUpdate: Boolean = false) {
        if (!binding.mainMenu.isSearchOpen && !forceUpdate) {
            return
        }

        lastSearchedText = text
        binding.searchPlaceholder2.beGoneIf(text.length >= 2)
        if (text.length >= 2) {
            ensureBackgroundThread {
                val searchQuery = "%$text%"
                val messages = messagesDB.getMessagesWithText(searchQuery)
                val conversations = conversationsDB.getConversationsWithText(searchQuery)
                val categories = categoryDB.getCategoryWithText(searchQuery)
                if (text == lastSearchedText) {
                    showSearchResults(messages, conversations, categories, text)
                }
            }
        } else {
            binding.searchPlaceholder.beVisible()
            binding.searchResultsList.beGone()
        }
    }

    private fun showSearchResults(
        messages: List<Message>,
        conversations: List<Conversation>,
        categories: List<Category>,
        searchedText: String,
    ) {
        val searchResults = ArrayList<SearchResult>()
        conversations.forEach { conversation ->
            val date = (conversation.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )

            val searchResult = SearchResult(
                messageId = -1,
                title = conversation.title,
                snippet = conversation.phoneNumber,
                date = date,
                threadId = conversation.threadId,
                photoUri = conversation.photoUri,
                category = conversation.category
            )
            searchResults.add(searchResult)
        }

        messages.sortedByDescending { it.id }.forEach { message ->
            var recipient = message.senderName
            if (recipient.isEmpty() && message.participants.isNotEmpty()) {
                val participantNames = message.participants.map { it.name }
                recipient = TextUtils.join(", ", participantNames)
            }

            val date = (message.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )

            val searchResult = SearchResult(
                messageId = message.id,
                title = recipient,
                snippet = message.body,
                date = date,
                threadId = message.threadId,
                photoUri = message.senderPhotoUri,
                category = categories.find { it.id == message.categoryId }?.name ?: ""
            )
            searchResults.add(searchResult)
        }

        runOnUiThread {
            binding.searchResultsList.beVisibleIf(searchResults.isNotEmpty())
            binding.searchPlaceholder.beVisibleIf(searchResults.isEmpty())

            val currAdapter = binding.searchResultsList.adapter
            if (currAdapter == null) {
                SearchResultsAdapter(this, searchResults, binding.searchResultsList, searchedText) {
                    hideKeyboard()
                    Intent(this, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, (it as SearchResult).threadId)
                        putExtra(THREAD_TITLE, it.title)
                        putExtra(SEARCHED_MESSAGE_ID, it.messageId)
                        startActivity(this)
                    }
                }.apply {
                    binding.searchResultsList.adapter = this
                }
            } else {
                (currAdapter as SearchResultsAdapter).updateItems(searchResults, searchedText)
            }
        }
    }

    private fun getSwipeActionVisuals(
        action: Int,
        archiveIcon: Drawable?,
        readIcon: Drawable?,
        deleteIcon: Drawable?,
    ): Pair<Drawable?, Int> {
        return when (action) {
            INBOX_SWIPE_ACTION_ARCHIVE -> archiveIcon to getProperPrimaryColor().adjustAlpha(0.9f)
            INBOX_SWIPE_ACTION_TOGGLE_READ_STATUS -> readIcon to getProperTextColor().adjustAlpha(0.8f)
            INBOX_SWIPE_ACTION_DELETE,
            INBOX_SWIPE_ACTION_BLOCK -> deleteIcon to getProperTextColor().adjustAlpha(0.95f)
            else -> null to getProperBackgroundColor()
        }
    }

    private fun drawSwipeIcon(
        canvas: Canvas,
        icon: Drawable?,
        tintColor: Int,
        itemView: android.view.View,
        alignEnd: Boolean,
    ) {
        icon ?: return
        val iconHeight = icon.intrinsicHeight
        val iconWidth = icon.intrinsicWidth
        if (iconHeight <= 0 || iconWidth <= 0) return

        val itemHeight = itemView.bottom - itemView.top
        val iconTop = itemView.top + (itemHeight - iconHeight) / 2
        val iconBottom = iconTop + iconHeight
        val horizontalMargin = (itemHeight - iconHeight) / 2

        val iconLeft = if (alignEnd) {
            itemView.right - horizontalMargin - iconWidth
        } else {
            itemView.left + horizontalMargin
        }
        val iconRight = iconLeft + iconWidth

        icon.mutate().setTint(tintColor)
        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
        icon.draw(canvas)
    }

    private fun launchRecycleBin() {
        hideKeyboard()
        startActivity(Intent(applicationContext, RecycleBinConversationsActivity::class.java))
    }

    private fun launchArchivedConversations() {
        hideKeyboard()
        startActivity(Intent(applicationContext, ArchivedConversationsActivity::class.java))
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_SMS_MMS or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(
                title = R.string.faq_2_title,
                text = R.string.faq_2_text
            ),
            FAQItem(
                title = R.string.faq_3_title,
                text = R.string.faq_3_text
            ),
            FAQItem(
                title = R.string.faq_4_title,
                text = R.string.faq_4_text
            ),
            FAQItem(
                title = org.fossify.commons.R.string.faq_9_title_commons,
                text = org.fossify.commons.R.string.faq_9_text_commons
            )
        )

        if (!resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)) {
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_2_title_commons,
                    text = org.fossify.commons.R.string.faq_2_text_commons
                )
            )
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_6_title_commons,
                    text = org.fossify.commons.R.string.faq_6_text_commons
                )
            )
        }

        startAboutActivity(
            appNameId = R.string.app_name,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(@Suppress("unused") event: Events.RefreshConversations) {
        android.util.Log.d("CategoryDebug", "MainActivity: received RefreshConversations event -> initMessenger")
        initMessenger()
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }

    private data class SavedViewIconOption(
        val iconResName: String,
        val titleRes: Int,
        val drawableRes: Int,
    )
}
