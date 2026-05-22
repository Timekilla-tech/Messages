package org.fossify.messages.helpers

import android.content.Context
import com.google.gson.reflect.TypeToken
import org.fossify.commons.helpers.BaseConfig
import org.fossify.messages.extensions.gson.gson
import org.fossify.messages.extensions.getDefaultKeyboardHeight
import org.fossify.messages.models.Conversation

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    fun saveUseSIMIdAtNumber(number: String, SIMId: Int) {
        prefs.edit().putInt(USE_SIM_ID_PREFIX + number, SIMId).apply()
    }

    fun getUseSIMIdAtNumber(number: String) = prefs.getInt(USE_SIM_ID_PREFIX + number, 0)

    var showCharacterCounter: Boolean
        get() = prefs.getBoolean(SHOW_CHARACTER_COUNTER, false)
        set(showCharacterCounter) = prefs.edit()
            .putBoolean(SHOW_CHARACTER_COUNTER, showCharacterCounter).apply()

    var useSimpleCharacters: Boolean
        get() = prefs.getBoolean(USE_SIMPLE_CHARACTERS, false)
        set(useSimpleCharacters) = prefs.edit()
            .putBoolean(USE_SIMPLE_CHARACTERS, useSimpleCharacters).apply()

    var sendOnEnter: Boolean
        get() = prefs.getBoolean(SEND_ON_ENTER, false)
        set(sendOnEnter) = prefs.edit().putBoolean(SEND_ON_ENTER, sendOnEnter).apply()

    var enableDeliveryReports: Boolean
        get() = prefs.getBoolean(ENABLE_DELIVERY_REPORTS, false)
        set(enableDeliveryReports) = prefs.edit()
            .putBoolean(ENABLE_DELIVERY_REPORTS, enableDeliveryReports).apply()

    var sendLongMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_LONG_MESSAGE_MMS, false)
        set(sendLongMessageMMS) = prefs.edit().putBoolean(SEND_LONG_MESSAGE_MMS, sendLongMessageMMS)
            .apply()

    var sendGroupMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_GROUP_MESSAGE_MMS, false)
        set(sendGroupMessageMMS) = prefs.edit()
            .putBoolean(SEND_GROUP_MESSAGE_MMS, sendGroupMessageMMS).apply()

    var lockScreenVisibilitySetting: Int
        get() = prefs.getInt(LOCK_SCREEN_VISIBILITY, LOCK_SCREEN_SENDER_MESSAGE)
        set(lockScreenVisibilitySetting) = prefs.edit()
            .putInt(LOCK_SCREEN_VISIBILITY, lockScreenVisibilitySetting).apply()

    var mmsFileSizeLimit: Long
        get() = prefs.getLong(MMS_FILE_SIZE_LIMIT, FILE_SIZE_600_KB)
        set(mmsFileSizeLimit) = prefs.edit().putLong(MMS_FILE_SIZE_LIMIT, mmsFileSizeLimit).apply()

    var pinnedConversations: Set<String>
        get() = prefs.getStringSet(PINNED_CONVERSATIONS, HashSet<String>())!!
        set(pinnedConversations) = prefs.edit()
            .putStringSet(PINNED_CONVERSATIONS, pinnedConversations).apply()

    fun addPinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.plus(threadId.toString())
    }

    fun addPinnedConversations(conversations: List<Conversation>) {
        pinnedConversations = pinnedConversations.plus(conversations.map { it.threadId.toString() })
    }

    fun removePinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.minus(threadId.toString())
    }

    fun removePinnedConversations(conversations: List<Conversation>) {
        pinnedConversations =
            pinnedConversations.minus(conversations.map { it.threadId.toString() })
    }

    var blockedKeywords: Set<String>
        get() = prefs.getStringSet(BLOCKED_KEYWORDS, HashSet<String>())!!
        set(blockedKeywords) = prefs.edit().putStringSet(BLOCKED_KEYWORDS, blockedKeywords).apply()

    fun addBlockedKeyword(keyword: String) {
        blockedKeywords = blockedKeywords.plus(keyword)
    }

    fun removeBlockedKeyword(keyword: String) {
        blockedKeywords = blockedKeywords.minus(keyword)
    }

    var exportSms: Boolean
        get() = prefs.getBoolean(EXPORT_SMS, true)
        set(exportSms) = prefs.edit().putBoolean(EXPORT_SMS, exportSms).apply()

    var exportMms: Boolean
        get() = prefs.getBoolean(EXPORT_MMS, true)
        set(exportMms) = prefs.edit().putBoolean(EXPORT_MMS, exportMms).apply()

    var importSms: Boolean
        get() = prefs.getBoolean(IMPORT_SMS, true)
        set(importSms) = prefs.edit().putBoolean(IMPORT_SMS, importSms).apply()

    var importMms: Boolean
        get() = prefs.getBoolean(IMPORT_MMS, true)
        set(importMms) = prefs.edit().putBoolean(IMPORT_MMS, importMms).apply()

    var wasDbCleared: Boolean
        get() = prefs.getBoolean(WAS_DB_CLEARED, false)
        set(wasDbCleared) = prefs.edit().putBoolean(WAS_DB_CLEARED, wasDbCleared).apply()

    var keyboardHeight: Int
        get() = prefs.getInt(SOFT_KEYBOARD_HEIGHT, context.getDefaultKeyboardHeight())
        set(keyboardHeight) = prefs.edit().putInt(SOFT_KEYBOARD_HEIGHT, keyboardHeight).apply()

    var useRecycleBin: Boolean
        get() = prefs.getBoolean(USE_RECYCLE_BIN, false)
        set(useRecycleBin) = prefs.edit().putBoolean(USE_RECYCLE_BIN, useRecycleBin).apply()

    var lastRecycleBinCheck: Long
        get() = prefs.getLong(LAST_RECYCLE_BIN_CHECK, 0L)
        set(lastRecycleBinCheck) = prefs.edit().putLong(LAST_RECYCLE_BIN_CHECK, lastRecycleBinCheck)
            .apply()

    var isArchiveAvailable: Boolean
        get() = prefs.getBoolean(IS_ARCHIVE_AVAILABLE, true)
        set(isArchiveAvailable) = prefs.edit().putBoolean(IS_ARCHIVE_AVAILABLE, isArchiveAvailable)
            .apply()

    var customNotifications: Set<String>
        get() = prefs.getStringSet(CUSTOM_NOTIFICATIONS, HashSet<String>())!!
        set(customNotifications) = prefs.edit()
            .putStringSet(CUSTOM_NOTIFICATIONS, customNotifications).apply()

    fun addCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.plus(threadId.toString())
    }

    fun removeCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.minus(threadId.toString())
    }

    var lastBlockedKeywordExportPath: String
        get() = prefs.getString(LAST_BLOCKED_KEYWORD_EXPORT_PATH, "")!!
        set(lastBlockedNumbersExportPath) = prefs.edit()
            .putString(LAST_BLOCKED_KEYWORD_EXPORT_PATH, lastBlockedNumbersExportPath).apply()

    var keepConversationsArchived: Boolean
        get() = prefs.getBoolean(KEEP_CONVERSATIONS_ARCHIVED, false)
        set(keepConversationsArchived) = prefs.edit()
            .putBoolean(KEEP_CONVERSATIONS_ARCHIVED, keepConversationsArchived).apply()

    var inboxSwipeStartAction: Int
        get() = sanitizeInboxSwipeAction(
            prefs.getInt(INBOX_SWIPE_START_ACTION, INBOX_SWIPE_ACTION_ARCHIVE)
        )
        set(action) = prefs.edit()
            .putInt(INBOX_SWIPE_START_ACTION, sanitizeInboxSwipeAction(action)).apply()

    var inboxSwipeEndAction: Int
        get() = sanitizeInboxSwipeAction(
            prefs.getInt(INBOX_SWIPE_END_ACTION, INBOX_SWIPE_ACTION_TOGGLE_READ_STATUS)
        )
        set(action) = prefs.edit()
            .putInt(INBOX_SWIPE_END_ACTION, sanitizeInboxSwipeAction(action)).apply()

    var screenViewMode: Int
        get() = sanitizeScreenViewMode(prefs.getInt(SCREEN_VIEW_MODE, SCREEN_VIEW_MODE_AUTO))
        set(screenViewMode) = prefs.edit().putInt(SCREEN_VIEW_MODE, sanitizeScreenViewMode(screenViewMode)).apply()

    private fun sanitizeInboxSwipeAction(action: Int): Int {
        return when (action) {
            INBOX_SWIPE_ACTION_NONE,
            INBOX_SWIPE_ACTION_ARCHIVE,
            INBOX_SWIPE_ACTION_TOGGLE_READ_STATUS,
            INBOX_SWIPE_ACTION_DELETE,
            INBOX_SWIPE_ACTION_BLOCK -> action

            else -> INBOX_SWIPE_ACTION_NONE
        }
    }

    private fun sanitizeScreenViewMode(mode: Int): Int {
        return when (mode) {
            SCREEN_VIEW_MODE_AUTO,
            SCREEN_VIEW_MODE_SINGLE,
            SCREEN_VIEW_MODE_TWO_PANE -> mode

            else -> SCREEN_VIEW_MODE_AUTO
        }
    }

    var savedViewsJson: String
        get() = prefs.getString(SAVED_VIEWS_JSON, "")!!
        set(savedViewsJson) = prefs.edit().putString(SAVED_VIEWS_JSON, savedViewsJson).apply()

    var lastSavedViewId: String
        get() = prefs.getString(LAST_SAVED_VIEW_ID, "main")!!
        set(lastSavedViewId) = prefs.edit().putString(LAST_SAVED_VIEW_ID, lastSavedViewId).apply()

    private var conversationFolderMapJson: String
        get() = prefs.getString("conversation_folders", "{}") ?: "{}"
        set(value) = prefs.edit().putString("conversation_folders", value).apply()

    private fun getConversationFolderMap(): Map<String, String> {
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(conversationFolderMapJson, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun setConversationFolder(threadId: Long, folderId: String?) {
        val map = getConversationFolderMap().toMutableMap()
        if (folderId == null) {
            map.remove(threadId.toString())
        } else {
            map[threadId.toString()] = folderId
        }
        conversationFolderMapJson = gson.toJson(map)
    }

    fun getConversationFolder(threadId: Long): String? {
        return getConversationFolderMap()[threadId.toString()]
    }

    fun setLastUsedFolderForConversation(threadId: Long, folderId: String) {
        val map = getConversationFolderMap().toMutableMap()
        map["last_used_$threadId"] = folderId
        conversationFolderMapJson = gson.toJson(map)
    }

    fun getLastUsedFolderForConversation(threadId: Long): String? {
        return getConversationFolderMap()["last_used_$threadId"]
    }

    fun setUserPrimaryFolderForConversation(threadId: Long, folderId: String) {
        setConversationFolder(threadId, folderId)
    }

    fun getUserPrimaryFolderForConversation(threadId: Long): String? {
        return getConversationFolder(threadId)
    }
}
