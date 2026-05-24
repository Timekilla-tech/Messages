package org.fossify.messages.extensions

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.PhoneLookup
import android.provider.OpenableColumns
import android.provider.Telephony.*
import android.telephony.SubscriptionManager
import android.text.TextUtils
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.mms.pdu_alt.PduHeaders
import org.fossify.commons.extensions.areDigitsOnly
import org.fossify.commons.extensions.getBlockedNumbers
import org.fossify.commons.extensions.getIntValue
import org.fossify.commons.extensions.getIntValueOr
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.queryCursor
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.trimToComparableNumber
import org.fossify.commons.helpers.*
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.R
import org.fossify.messages.databases.MessagesDatabase
import org.fossify.messages.helpers.*
import org.fossify.messages.helpers.AttachmentUtils.parseAttachmentNames
import org.fossify.messages.interfaces.*
import org.fossify.messages.messaging.MessagingUtils
import org.fossify.messages.messaging.MessagingUtils.Companion.ADDRESS_SEPARATOR
import org.fossify.messages.messaging.SmsSender
import org.fossify.messages.messaging.scheduleMessage
import org.fossify.messages.models.*
import org.xmlpull.v1.XmlPullParserException
import java.io.FileNotFoundException
import java.util.Locale
import kotlin.text.Regex
import kotlin.text.contains
import kotlin.text.equals
import kotlin.text.isBlank
import kotlin.text.isEmpty
import kotlin.text.isNotBlank
import kotlin.text.isNotEmpty
import kotlin.text.lineSequence
import kotlin.text.lowercase
import kotlin.text.orEmpty
import kotlin.text.split
import kotlin.text.startsWith
import kotlin.text.take
import kotlin.text.toInt
import kotlin.text.toLong
import kotlin.text.trim
import kotlin.time.Duration.Companion.minutes

val Context.config: Config
    get() = Config.newInstance(applicationContext)

fun Context.getMessagesDB() = MessagesDatabase.getInstance(this)

val Context.conversationsDB: ConversationsDao
    get() = getMessagesDB().ConversationsDao()

val Context.attachmentsDB: AttachmentsDao
    get() = getMessagesDB().AttachmentsDao()

val Context.messageAttachmentsDB: MessageAttachmentsDao
    get() = getMessagesDB().MessageAttachmentsDao()

val Context.messagesDB: MessagesDao
    get() = getMessagesDB().MessagesDao()

val Context.draftsDB: DraftsDao
    get() = getMessagesDB().DraftsDao()

val Context.categoryDB: CategoryDao
    get() = getMessagesDB().CategoryDao()

val Context.notificationHelper
    get() = NotificationHelper(this)

val Context.messagingUtils
    get() = MessagingUtils(this)

val Context.smsSender
    get() = SmsSender.getInstance(applicationContext as Application)

val Context.shortcutHelper get() = ShortcutHelper(this)

fun Context.getMessages(
    threadId: Long,
    dateFrom: Int = -1,
    includeScheduledMessages: Boolean = true,
    limit: Int = MESSAGES_LIMIT,
): ArrayList<Message> {
    val localMessagesById = try {
        messagesDB.getNonRecycledThreadMessages(threadId).associateBy { it.id }
    } catch (_: Exception) {
        emptyMap()
    }

    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms._ID,
        Sms.BODY,
        Sms.TYPE,
        Sms.ADDRESS,
        Sms.DATE,
        Sms.READ,
        Sms.THREAD_ID,
        Sms.SUBSCRIPTION_ID,
        Sms.STATUS
    )

    val rangeQuery = if (dateFrom == -1) "" else "AND ${Sms.DATE} < ${dateFrom.toLong() * 1000}"
    val selection = "${Sms.THREAD_ID} = ? $rangeQuery"
    val selectionArgs = arrayOf(threadId.toString())
    val sortOrder = "${Sms.DATE} DESC LIMIT $limit"

    val blockStatus = HashMap<String, Boolean>()
    val blockedNumbers = getBlockedNumbers()
    var messages = ArrayList<Message>()
    queryCursor(uri, projection, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val senderNumber = cursor.getStringValue(Sms.ADDRESS) ?: return@queryCursor
        val isNumberBlocked = blockStatus.getOrPut(senderNumber) { isNumberBlocked(senderNumber, blockedNumbers) }
        if (isNumberBlocked) {
            return@queryCursor
        }

        val id = cursor.getLongValue(Sms._ID)
        val body = cursor.getStringValue(Sms.BODY)
        val type = cursor.getIntValue(Sms.TYPE)
        val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
        val senderName = namePhoto.name
        val photoUri = namePhoto.photoUri ?: ""
        val date = (cursor.getLongValue(Sms.DATE) / 1000).toInt()
        val read = cursor.getIntValue(Sms.READ) == 1
        val thread = cursor.getLongValue(Sms.THREAD_ID)
        val subscriptionId = cursor.getIntValueOr(
            key = Sms.SUBSCRIPTION_ID,
            defaultValue = SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )

        val status = cursor.getIntValue(Sms.STATUS)
        val participants = senderNumber.split(ADDRESS_SEPARATOR).map { number ->
            val phoneNumber = PhoneNumber(number, 0, "", number)
            val participantPhoto = getNameAndPhotoFromPhoneNumber(number)
            SimpleContact(
                rawId = 0,
                contactId = 0,
                name = participantPhoto.name,
                photoUri = photoUri,
                phoneNumbers = arrayListOf(phoneNumber),
                birthdays = ArrayList(),
                anniversaries = ArrayList()
            )
        }
        val isMMS = false
        val localMessage = localMessagesById[id]?.takeIf { !it.isMMS }
        val message =
            Message(
                id = id,
                body = body,
                type = type,
                status = status,
                participants = ArrayList(participants),
                date = date,
                read = read,
                threadId = thread,
                isMMS = isMMS,
                attachment = null,
                senderPhoneNumber = senderNumber,
                senderName = senderName,
                senderPhotoUri = photoUri,
                subscriptionId = subscriptionId,
                isScheduled = false,
                categoryName = localMessage?.categoryName ?: "",
                categoryId = localMessage?.categoryId ?: 0
            )
        messages.add(message)
    }

    messages.addAll(getMMS(threadId, sortOrder, dateFrom, localMessagesById))

    if (includeScheduledMessages) {
        try {
            val scheduledMessages = messagesDB.getScheduledThreadMessages(threadId)
            messages.addAll(scheduledMessages)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    messages = messages
        .filter { it.participants.isNotEmpty() }
        .filterNot { it.isScheduled && it.millis() < System.currentTimeMillis() }
        .sortedWith(compareBy<Message> { it.date }.thenBy { it.id })
        .takeLast(limit)
        .toMutableList() as ArrayList<Message>

    return messages
}

// as soon as a message contains multiple recipients it counts as an MMS instead of SMS
fun Context.getMMS(
    threadId: Long? = null,
    sortOrder: String? = null,
    dateFrom: Int = -1,
    localMessagesById: Map<Long, Message>? = null,
): ArrayList<Message> {
    val resolvedLocalMessagesById = localMessagesById ?: if (threadId != null) {
        try {
            messagesDB.getNonRecycledThreadMessages(threadId).associateBy { it.id }
        } catch (_: Exception) {
            emptyMap()
        }
    } else {
        emptyMap()
    }

    val uri = Mms.CONTENT_URI
    val projection = arrayOf(
        Mms._ID,
        Mms.DATE,
        Mms.READ,
        Mms.MESSAGE_BOX,
        Mms.THREAD_ID,
        Mms.SUBSCRIPTION_ID,
        Mms.STATUS
    )

    var selection: String? = null
    var selectionArgs: Array<String>? = null

    if (threadId == null && dateFrom != -1) {
        // Should not multiply 1000 here, because date in mms's database is different from sms's.
        selection = "${Sms.DATE} < ${dateFrom.toLong()}"
    } else if (threadId != null && dateFrom == -1) {
        selection = "${Sms.THREAD_ID} = ?"
        selectionArgs = arrayOf(threadId.toString())
    } else if (threadId != null) {
        selection = "${Sms.THREAD_ID} = ? AND ${Sms.DATE} < ${dateFrom.toLong()}"
        selectionArgs = arrayOf(threadId.toString())
    }

    val messages = ArrayList<Message>()
    val contactsMap = HashMap<Int, SimpleContact>()
    queryCursor(uri, projection, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val mmsId = cursor.getLongValue(Mms._ID)
        val type = cursor.getIntValue(Mms.MESSAGE_BOX)
        val date = cursor.getLongValue(Mms.DATE).toInt()
        val read = cursor.getIntValue(Mms.READ) == 1
        val threadId = cursor.getLongValue(Mms.THREAD_ID)
        val subscriptionId = cursor.getIntValue(Mms.SUBSCRIPTION_ID)
        val status = cursor.getIntValue(Mms.STATUS)
        val participants = getThreadParticipants(threadId, contactsMap)

        val isMMS = true
        val attachment = getMmsAttachment(mmsId)
        val body = attachment.text
        var senderNumber = ""
        var senderName = ""
        var senderPhotoUri = ""

        if (type != Mms.MESSAGE_BOX_SENT && type != Mms.MESSAGE_BOX_FAILED) {
            senderNumber = getMMSSender(mmsId)
            val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
            senderName = namePhoto.name
            senderPhotoUri = namePhoto.photoUri ?: ""
        }

        val localMessage = resolvedLocalMessagesById[mmsId]?.takeIf { it.isMMS }
        val message =
            Message(
                id = mmsId,
                body = body,
                type = type,
                status = status,
                participants = participants,
                date = date,
                read = read,
                threadId = threadId,
                isMMS = isMMS,
                attachment = attachment,
                senderPhoneNumber = senderNumber,
                senderName = senderName,
                senderPhotoUri = senderPhotoUri,
                subscriptionId = subscriptionId,
                isScheduled = false,
                categoryName = localMessage?.categoryName ?: "",
                categoryId = localMessage?.categoryId ?: 0
            )
        messages.add(message)

        participants.forEach {
            contactsMap[it.rawId] = it
        }
    }

    return messages
}

fun Context.getMMSSender(msgId: Long): String {
    val uri = "${Mms.CONTENT_URI}/$msgId/addr".toUri()
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )

    val selection = "${Mms.Addr.TYPE} = ?"
    val selectionArgs = arrayOf(PduHeaders.FROM.toString())

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getStringValue(Mms.Addr.ADDRESS)
            }
        }
    } catch (_: Exception) {
    }
    return ""
}

fun Context.getUnreadCountsByThread(): Map<Long, Int> {
    val result = HashMap<Long, Int>(128)

    fun bump(id: Long) {
        result[id] = (result[id] ?: 0) + 1
    }

    // Unread SMS
    queryCursor(
        uri = Sms.CONTENT_URI,
        projection = arrayOf(Sms.THREAD_ID),
        selection = "${Sms.READ}=0 AND ${Sms.TYPE}=${Sms.MESSAGE_TYPE_INBOX}",
        selectionArgs = null,
        showErrors = false
    ) { bump(it.getLongValue(Sms.THREAD_ID)) }

    // Unread MMS
    queryCursor(
        uri = Mms.CONTENT_URI,
        projection = arrayOf(Mms.THREAD_ID),
        selection = "${Mms.READ}=0 AND ${Mms.MESSAGE_BOX}=${Mms.MESSAGE_BOX_INBOX}",
        selectionArgs = null,
        showErrors = false
    ) { bump(it.getLongValue(Mms.THREAD_ID)) }

    return result
}

fun Context.getConversations(
    threadId: Long? = null,
    privateContacts: ArrayList<SimpleContact> = ArrayList(),
): ArrayList<Conversation> {
    val archiveAvailable = config.isArchiveAvailable

    val uri = "${Threads.CONTENT_URI}?simple=true".toUri()
    val projection = mutableListOf(
        Threads._ID,
        Threads.SNIPPET,
        Threads.DATE,
        Threads.READ,
        Threads.RECIPIENT_IDS,
    )

    if (archiveAvailable) {
        projection += Threads.ARCHIVED
    }

    var selection = "${Threads.MESSAGE_COUNT} > 0"
    var selectionArgs = arrayOf<String>()
    if (threadId != null) {
        selection += " AND ${Threads._ID} = ?"
        selectionArgs += threadId.toString()
    }

    val sortOrder = "${Threads.DATE} DESC"

    val conversations = ArrayList<Conversation>()
    val simpleContactHelper = SimpleContactsHelper(this)
    val blockedNumbers = getBlockedNumbers()
    val unreadMap = getUnreadCountsByThread()
    try {
        queryCursorUnsafe(
            uri,
            projection.toTypedArray(),
            selection,
            selectionArgs,
            sortOrder
        ) { cursor ->
            val id = cursor.getLongValue(Threads._ID)
            var snippet = cursor.getStringValue(Threads.SNIPPET) ?: ""
            if (snippet.isEmpty()) {
                snippet = getThreadSnippet(id)
            }

            var date = cursor.getLongValue(Threads.DATE)
            if (date.toString().length > 10) {
                date /= 1000
            }

            // drafts are stored locally they take priority over the original date
            val draft = draftsDB.getDraftById(id)
            if (draft != null) {
                date = draft.date / 1000
            }

            val rawIds = cursor.getStringValue(Threads.RECIPIENT_IDS)
            val recipientIds =
                rawIds.split(" ").filter { it.areDigitsOnly() }.map { it.toInt() }.toMutableList()
            val phoneNumbers = getThreadPhoneNumbers(recipientIds)
            if (phoneNumbers.isEmpty() || phoneNumbers.any {
                    isNumberBlocked(
                        it,
                        blockedNumbers
                    )
                }) {
                return@queryCursorUnsafe
            }

            val names = getThreadContactNames(phoneNumbers, privateContacts)
            val title = TextUtils.join(", ", names.toTypedArray())
            val photoUri =
                if (phoneNumbers.size == 1) simpleContactHelper.getPhotoUriFromPhoneNumber(
                    phoneNumbers.first()
                ) else ""
            val isGroupConversation = phoneNumbers.size > 1
            val read = cursor.getIntValue(Threads.READ) == 1
            val archived =
                if (archiveAvailable) cursor.getIntValue(Threads.ARCHIVED) == 1 else false
            val unreadCount = if (!read) unreadMap[id] ?: 0 else 0
            
            val cachedConv = conversationsDB.getConversationWithThreadId(id)
            val conversation = Conversation(
                threadId = id,
                snippet = snippet,
                date = date.toInt(),
                read = read,
                title = title,
                photoUri = photoUri,
                isGroupConversation = isGroupConversation,
                phoneNumber = phoneNumbers.first(),
                isArchived = archived,
                unreadCount = unreadCount,
                category = cachedConv?.category ?: ""
            )
            conversations.add(conversation)
        }
    } catch (sqliteException: SQLiteException) {
        if (
            sqliteException.message?.contains("no such column: archived") == true
            && archiveAvailable
        ) {
            config.isArchiveAvailable = false
            return getConversations(threadId, privateContacts)
        } else {
            showErrorToast(sqliteException)
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    conversations.sortByDescending { it.date }
    return conversations
}

private fun Context.queryCursorUnsafe(
    uri: Uri,
    projection: Array<String>,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null,
    callback: (cursor: Cursor) -> Unit,
) {
    val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
    cursor?.use {
        if (cursor.moveToFirst()) {
            do {
                callback(cursor)
            } while (cursor.moveToNext())
        }
    }
}

fun Context.getConversationIds(): List<Long> {
    val projection = arrayOf(Threads._ID)
    val sortOrder = "${Threads.DATE} ASC"
    val conversationIds = mutableListOf<Long>()
    queryCursor(Threads.CONTENT_URI, projection, null, null, sortOrder, true) { cursor ->
        val id = cursor.getLongValue(Threads._ID)
        conversationIds.add(id)
    }
    return conversationIds
}

// based on https://stackoverflow.com/a/6446831/1967672
@SuppressLint("NewApi")
fun Context.getMmsAttachment(id: Long): MessageAttachment {
    val uri = if (isQPlus()) {
        Mms.Part.CONTENT_URI
    } else {
        "content://mms/part".toUri()
    }

    val projection = arrayOf(
        Mms._ID,
        Mms.Part.CONTENT_TYPE,
        Mms.Part.TEXT
    )
    val selection = "${Mms.Part.MSG_ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    val messageAttachment = MessageAttachment(id, "", arrayListOf())

    var attachmentNames: List<String>? = null
    var attachmentCount = 0
    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val partId = cursor.getLongValue(Mms._ID)
        val mimetype = cursor.getStringValue(Mms.Part.CONTENT_TYPE)
        if (mimetype == "text/plain") {
            messageAttachment.text = cursor
                .getStringValue(Mms.Part.TEXT)
                ?.take(MAX_MESSAGE_LENGTH)
                .orEmpty()
        } else if (mimetype.startsWith("image/") || mimetype.startsWith("video/")) {
            val fileUri = Uri.withAppendedPath(uri, partId.toString())
            messageAttachment.attachments.add(
                Attachment(
                    id = partId,
                    messageId = id,
                    uriString = fileUri.toString(),
                    mimetype = mimetype,
                    width = 0,
                    height = 0,
                    filename = ""
                )
            )
        } else if (mimetype != "application/smil") {
            val attachmentName = attachmentNames?.getOrNull(attachmentCount) ?: ""
            val attachment = Attachment(
                id = partId,
                messageId = id,
                uriString = Uri.withAppendedPath(uri, partId.toString()).toString(),
                mimetype = mimetype,
                width = 0,
                height = 0,
                filename = attachmentName
            )
            messageAttachment.attachments.add(attachment)
            attachmentCount++
        } else {
            val text = cursor.getStringValue(Mms.Part.TEXT)
            attachmentNames = try {
                parseAttachmentNames(text)
            } catch (e: XmlPullParserException) {
                e.printStackTrace()
                null
            }
        }
    }

    return messageAttachment
}

fun Context.getLatestMMS(): Message? {
    val sortOrder = "${Mms.DATE} DESC LIMIT 1"
    return getMMS(sortOrder = sortOrder).firstOrNull()
}

fun Context.getThreadSnippet(threadId: Long): String {
    val sortOrder = "${Mms.DATE} DESC LIMIT 1"
    val latestMms = getMMS(threadId, sortOrder).firstOrNull()
    var snippet = latestMms?.body ?: ""

    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.BODY
    )

    val selection = "${Sms.THREAD_ID} = ? AND ${Sms.DATE} > ?"
    val selectionArgs = arrayOf(
        threadId.toString(),
        latestMms?.date?.toString() ?: "0"
    )
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            if (cursor.moveToFirst()) {
                snippet = cursor.getStringValue(Sms.BODY)
            }
        }
    } catch (_: Exception) {
    }
    return snippet
}

fun Context.getMessageRecipientAddress(messageId: Long): String {
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.ADDRESS
    )

    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(messageId.toString())

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getStringValue(Sms.ADDRESS)
            }
        }
    } catch (_: Exception) {
    }

    return ""
}

fun Context.getThreadParticipants(
    threadId: Long,
    contactsMap: HashMap<Int, SimpleContact>?,
): ArrayList<SimpleContact> {
    MessagingCache.participantsCache.get(threadId)?.let {
        return it.map { contact ->
            contact.copy(
                phoneNumbers = contact.phoneNumbers.toArrayList(),
                birthdays = contact.birthdays.toArrayList(),
                anniversaries = contact.anniversaries.toArrayList()
            )
        }.toArrayList()
    }

    val uri = "${MmsSms.CONTENT_CONVERSATIONS_URI}?simple=true".toUri()
    val projection = arrayOf(
        ThreadsColumns.RECIPIENT_IDS
    )
    val selection = "${Mms._ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    val participants = ArrayList<SimpleContact>()
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                val address = cursor.getStringValue(ThreadsColumns.RECIPIENT_IDS)
                address.split(" ").filter { it.areDigitsOnly() }.forEach {
                    val addressId = it.toInt()
                    if (contactsMap?.containsKey(addressId) == true) {
                        participants.add(contactsMap[addressId]!!)
                        return@forEach
                    }

                    val number = getPhoneNumberFromAddressId(addressId)
                    val namePhoto = getNameAndPhotoFromPhoneNumber(number)
                    val name = namePhoto.name
                    val photoUri = namePhoto.photoUri ?: ""
                    val phoneNumber = PhoneNumber(number, 0, "", number)
                    val contact = SimpleContact(
                        rawId = addressId,
                        contactId = addressId,
                        name = name,
                        photoUri = photoUri,
                        phoneNumbers = arrayListOf(phoneNumber),
                        birthdays = ArrayList(),
                        anniversaries = ArrayList()
                    )
                    participants.add(contact)
                }
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    MessagingCache.participantsCache.put(threadId, participants)
    return participants
}

fun Context.getThreadPhoneNumbers(recipientIds: List<Int>): ArrayList<String> {
    val numbers = ArrayList<String>()
    recipientIds.forEach {
        numbers.add(getPhoneNumberFromAddressId(it))
    }
    return numbers
}

fun Context.getThreadContactNames(
    phoneNumbers: List<String>,
    privateContacts: ArrayList<SimpleContact>,
): ArrayList<String> {
    val names = ArrayList<String>()
    phoneNumbers.forEach { number ->
        val name = SimpleContactsHelper(this).getNameFromPhoneNumber(number)
        if (name != number) {
            names.add(name)
        } else {
            val privateContact = privateContacts.firstOrNull { it.doesHavePhoneNumber(number) }
            if (privateContact == null) {
                names.add(name)
            } else {
                names.add(privateContact.name)
            }
        }
    }
    return names
}

fun Context.getPhoneNumberFromAddressId(canonicalAddressId: Int): String {
    val uri = Uri.withAppendedPath(MmsSms.CONTENT_URI, "canonical-addresses")
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )

    val selection = "${Mms._ID} = ?"
    val selectionArgs = arrayOf(canonicalAddressId.toString())
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getStringValue(Mms.Addr.ADDRESS)
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
    return ""
}

fun Context.getSuggestedContacts(
    privateContacts: ArrayList<SimpleContact> = ArrayList(),
): ArrayList<SimpleContact> {
    val contacts = ArrayList<SimpleContact>()
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.ADDRESS
    )

    val sortOrder = "${Sms.DATE} DESC LIMIT 50"
    val blockedNumbers = getBlockedNumbers()

    queryCursor(uri, projection, null, null, sortOrder, showErrors = true) { cursor ->
        val senderNumber = cursor.getStringValue(Sms.ADDRESS) ?: return@queryCursor
        val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
        var senderName = namePhoto.name
        var photoUri = namePhoto.photoUri ?: ""
        if (isNumberBlocked(senderNumber, blockedNumbers)) {
            return@queryCursor
        } else if (namePhoto.name == senderNumber) {
            if (privateContacts.isNotEmpty()) {
                val privateContact = privateContacts.firstOrNull {
                    it.phoneNumbers.first().normalizedNumber == senderNumber
                }
                if (privateContact != null) {
                    senderName = privateContact.name
                    photoUri = privateContact.photoUri
                } else {
                    return@queryCursor
                }
            } else {
                return@queryCursor
            }
        }

        val phoneNumber = PhoneNumber(senderNumber, 0, "", senderNumber)
        val contact = SimpleContact(
            rawId = 0,
            contactId = 0,
            name = senderName,
            photoUri = photoUri,
            phoneNumbers = arrayListOf(phoneNumber),
            birthdays = ArrayList(),
            anniversaries = ArrayList()
        )
        if (!contacts.map { it.phoneNumbers.first().normalizedNumber.trimToComparableNumber() }
                .contains(senderNumber.trimToComparableNumber())) {
            contacts.add(contact)
        }
    }

    return contacts
}

fun Context.getNameAndPhotoFromPhoneNumber(number: String): NamePhoto {
    MessagingCache.namePhoto.get(number)?.let { return it }
    if (!hasPermission(PERMISSION_READ_CONTACTS)) {
        return NamePhoto(number, null)
    }

    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    val projection = arrayOf(
        PhoneLookup.DISPLAY_NAME,
        PhoneLookup.PHOTO_URI
    )

    val result = try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor.use {
            if (cursor?.moveToFirst() == true) {
                val name = cursor.getStringValue(PhoneLookup.DISPLAY_NAME)
                val photoUri = cursor.getStringValue(PhoneLookup.PHOTO_URI)
                NamePhoto(name, photoUri)
            } else {
                NamePhoto(number, null)
            }
        }
    } catch (_: Exception) {
        NamePhoto(number, null)
    }

    MessagingCache.namePhoto.put(number, result)
    return result
}

fun Context.insertNewSMS(
    address: String,
    subject: String,
    body: String,
    date: Long,
    read: Int,
    threadId: Long,
    type: Int,
    subscriptionId: Int,
): Long {
    val uri = Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.ADDRESS, address)
        put(Sms.SUBJECT, subject)
        put(Sms.BODY, body)
        put(Sms.DATE, date)
        put(Sms.READ, read)
        put(Sms.THREAD_ID, threadId)
        put(Sms.TYPE, type)
        put(Sms.SUBSCRIPTION_ID, subscriptionId)
    }

    return try {
        val newUri = contentResolver.insert(uri, contentValues)
        newUri?.lastPathSegment?.toLong() ?: 0L
    } catch (_: Exception) {
        0L
    }
}

fun Context.removeAllArchivedConversations(callback: (() -> Unit)? = null) {
    ensureBackgroundThread {
        try {
            for (conversation in conversationsDB.getAllArchived()) {
                deleteConversation(conversation.threadId)
            }
            toast(R.string.archive_emptied_successfully)
            callback?.invoke()
        } catch (_: Exception) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
        }
    }
}

fun Context.deleteConversation(threadId: Long) {
    var uri = Sms.CONTENT_URI
    val selection = "${Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    try {
        contentResolver.delete(uri, selection, selectionArgs)
    } catch (e: Exception) {
        showErrorToast(e)
    }

    uri = Mms.CONTENT_URI
    try {
        contentResolver.delete(uri, selection, selectionArgs)
    } catch (e: Exception) {
        e.printStackTrace()
    }

    conversationsDB.deleteThreadId(threadId)
    messagesDB.deleteThreadMessages(threadId)
    MessagingCache.participantsCache.remove(threadId)

    if (config.customNotifications.contains(threadId.toString())) {
        config.removeCustomNotificationsByThreadId(threadId)
        notificationManager.deleteNotificationChannel(threadId.toString())
    }
    if(shortcutHelper.getShortcut(threadId) != null) {
        shortcutHelper.removeShortcutForThread(threadId)
    }
}

fun Context.checkAndDeleteOldRecycleBinMessages(callback: (() -> Unit)? = null) {
    if (
        config.useRecycleBin
        && config.lastRecycleBinCheck < System.currentTimeMillis() - DAY_SECONDS * 1000
    ) {
        config.lastRecycleBinCheck = System.currentTimeMillis()
        ensureBackgroundThread {
            try {
                messagesDB.getOldRecycleBinMessages(
                    timestamp = System.currentTimeMillis() - MONTH_SECONDS * 1000L
                ).forEach { message ->
                    deleteMessage(message.id, message.isMMS)
                }
                callback?.invoke()
            } catch (_: Exception) {
            }
        }
    }
}

fun Context.emptyMessagesRecycleBin() {
    val messages = messagesDB.getAllRecycleBinMessages()
    for (message in messages) {
        deleteMessage(message.id, message.isMMS)
    }
}

fun Context.emptyMessagesRecycleBinForConversation(threadId: Long) {
    val messages = messagesDB.getThreadMessagesFromRecycleBin(threadId)
    for (message in messages) {
        deleteMessage(message.id, message.isMMS)
    }
}

fun Context.restoreAllMessagesFromRecycleBinForConversation(threadId: Long) {
    messagesDB.deleteThreadMessagesFromRecycleBin(threadId)
}

fun Context.moveMessageToRecycleBin(id: Long) {
    try {
        messagesDB.insertRecycleBinEntry(RecycleBinMessage(id, System.currentTimeMillis()))
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.restoreMessageFromRecycleBin(id: Long) {
    try {
        messagesDB.deleteFromRecycleBin(id)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.updateConversationArchivedStatus(threadId: Long, archived: Boolean) {
    val uri = Threads.CONTENT_URI
    val values = ContentValues().apply {
        put(Threads.ARCHIVED, archived)
    }
    val selection = "${Threads._ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    try {
        contentResolver.update(uri, values, selection, selectionArgs)
    } catch (sqliteException: SQLiteException) {
        if (
            sqliteException.message?.contains("no such column: archived") == true
            && config.isArchiveAvailable
        ) {
            config.isArchiveAvailable = false
            return
        } else {
            throw sqliteException
        }
    }
    if (archived) {
        conversationsDB.moveToArchive(threadId)
    } else {
        conversationsDB.unarchive(threadId)
    }
}

fun Context.deleteMessage(id: Long, isMMS: Boolean) {
    val uri = if (isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    try {
        contentResolver.delete(uri, selection, selectionArgs)
        messagesDB.delete(id)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.deleteScheduledMessage(messageId: Long) {
    try {
        messagesDB.delete(messageId)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.markMessageRead(id: Long, isMMS: Boolean) {
    val uri = if (isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.READ, 1)
        put(Sms.SEEN, 1)
    }
    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    contentResolver.update(uri, contentValues, selection, selectionArgs)
    messagesDB.markRead(id)
}

fun Context.markThreadMessagesRead(threadId: Long) {
    val id = threadId.toString()

    val smsValues = ContentValues().apply {
        put(Sms.READ, 1)
        put(Sms.SEEN, 1)
    }
    val smsSelection = "${Sms.THREAD_ID}=? AND ${Sms.TYPE}=? AND (${Sms.READ}=? OR ${Sms.SEEN}=?)"
    val smsArgs = arrayOf(id, Sms.MESSAGE_TYPE_INBOX.toString(), "0", "0")
    contentResolver.update(Sms.CONTENT_URI, smsValues, smsSelection, smsArgs)

    val mmsValues = ContentValues().apply {
        put(Mms.READ, 1)
        put(Mms.SEEN, 1)
    }
    val mmsSelection = "${Mms.THREAD_ID}=? AND ${Mms.MESSAGE_BOX}=? AND (${Mms.READ}=? OR ${Mms.SEEN}=?)"
    val mmsArgs = arrayOf(id, Mms.MESSAGE_BOX_INBOX.toString(), "0", "0")
    contentResolver.update(Mms.CONTENT_URI, mmsValues, mmsSelection, mmsArgs)

    messagesDB.markThreadRead(threadId)
    conversationsDB.markRead(threadId)
}

fun Context.markThreadMessagesUnread(threadId: Long) {
    arrayOf(Sms.CONTENT_URI, Mms.CONTENT_URI).forEach { uri ->
        val contentValues = ContentValues().apply {
            put(Sms.READ, 0)
            put(Sms.SEEN, 0)
        }
        val selection = "${Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        contentResolver.update(uri, contentValues, selection, selectionArgs)
    }
    conversationsDB.markUnread(threadId)
} 

@SuppressLint("NewApi")
fun Context.getThreadId(address: String): Long {
    return try {
        Threads.getOrCreateThreadId(this, address)
    } catch (_: Exception) {
        0L
    }
}

@SuppressLint("NewApi")
fun Context.getThreadId(addresses: Set<String>): Long {
    return try {
        Threads.getOrCreateThreadId(this, addresses)
    } catch (_: Exception) {
        0L
    }
}

fun Context.showReceivedMessageNotification(
    messageId: Long,
    address: String,
    senderName: String,
    body: String,
    threadId: Long,
    bitmap: Bitmap?,
) {
    Handler(Looper.getMainLooper()).post {
        notificationHelper.showMessageNotification(
            messageId = messageId,
            address = address,
            body = body,
            threadId = threadId,
            bitmap = bitmap,
            sender = senderName
        )
    }
}

fun Context.getNameFromAddress(address: String, privateCursor: Cursor?): String {
    var sender = getNameAndPhotoFromPhoneNumber(address).name
    if (address == sender) {
        try {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            sender = privateContacts.firstOrNull { it.doesHavePhoneNumber(address) }?.name ?: address
        } catch (e: Exception) {
            // Provider may be missing on some installs/devices; fall back to address
            e.printStackTrace()
            sender = address
        }
    }
    return sender
}

fun Context.getContactFromAddress(address: String, callback: ((contact: SimpleContact?) -> Unit)) {
    val privateCursor = getMyContactsCursor(false, true)
    SimpleContactsHelper(this).getAvailableContacts(false) {
        val contact = it.firstOrNull { it.doesHavePhoneNumber(address) }
        if (contact == null) {
            try {
                val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
                val privateContact = privateContacts.firstOrNull { it.doesHavePhoneNumber(address) }
                callback(privateContact)
            } catch (e: Exception) {
                // Provider missing or visibility restriction; return null
                e.printStackTrace()
                callback(null)
            }
        } else {
            callback(contact)
        }
    }
}

fun Context.getNotificationBitmap(photoUri: String): Bitmap? {
    val size = resources.getDimension(R.dimen.notification_large_icon_size).toInt()
    if (photoUri.isEmpty()) {
        return null
    }

    val options = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        .centerCrop()

    return try {
        Glide.with(this)
            .asBitmap()
            .load(photoUri)
            .apply(options)
            .apply(RequestOptions.circleCropTransform())
            .into(size, size)
            .get()
    } catch (_: Exception) {
        null
    }
}

fun Context.removeDiacriticsIfNeeded(text: String): String {
    return if (config.useSimpleCharacters) text.normalizeString() else text
}

fun Context.getSmsDraft(threadId: Long): String {
    val draft = try {
        draftsDB.getDraftById(threadId)
    } catch (_: Exception) {
        null
    }

    return draft?.body.orEmpty()
}

fun Context.getAllDrafts(): HashMap<Long, String> {
    val drafts = HashMap<Long, String>()
    try {
        draftsDB.getAll().forEach {
            drafts[it.threadId] = it.body
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return drafts
}

fun Context.saveSmsDraft(body: String, threadId: Long) {
    val draft = Draft(
        threadId = threadId,
        body = body,
        date = System.currentTimeMillis()
    )

    try {
        draftsDB.insertOrUpdate(draft)
    } catch (e: Exception) {
        e.printStackTrace()
        showErrorToast(e)
    }
}

fun Context.deleteSmsDraft(threadId: Long) {
    try {
        draftsDB.delete(threadId)
    } catch (e: Exception) {
        e.printStackTrace()
        showErrorToast(e)
    }
}

fun Context.updateLastConversationMessage(threadId: Long) {
    updateLastConversationMessage(setOf(threadId))
}

fun Context.updateLastConversationMessage(threadIds: Iterable<Long>) {
    // update the date and the snippet of the threads, by triggering the
    // following Android code (which runs even if no messages are deleted):
    // https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/android14-release/src/com/android/providers/telephony/MmsSmsProvider.java#1409
    val uri = Threads.CONTENT_URI
    val selection =
        "1 = 0" // always-false condition, because we don't actually want to delete any messages
    try {
        contentResolver.delete(uri, selection, null)
        for (threadId in threadIds) {
            val newConversation = getConversations(threadId)[0]
            insertOrUpdateConversation(newConversation)
        }
    } catch (_: Exception) {
    }
}

fun Context.getFileSizeFromUri(uri: Uri): Long {
    val assetFileDescriptor = try {
        contentResolver.openAssetFileDescriptor(uri, "r")
    } catch (_: FileNotFoundException) {
        null
    }

    // uses ParcelFileDescriptor#getStatSize underneath if failed
    val length = assetFileDescriptor?.use { it.length } ?: FILE_SIZE_NONE
    if (length != -1L) {
        return length
    }

    // if "content://" uri scheme, try contentResolver table
    if (uri.scheme.equals(ContentResolver.SCHEME_CONTENT)) {
        return contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                // maybe shouldn't trust ContentResolver for size:
                // https://stackoverflow.com/questions/48302972/content-resolver-returns-wrong-size
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex == -1) {
                    return@use FILE_SIZE_NONE
                }
                cursor.moveToFirst()
                return try {
                    cursor.getLong(sizeIndex)
                } catch (_: Throwable) {
                    FILE_SIZE_NONE
                }
            } ?: FILE_SIZE_NONE
    } else {
        return FILE_SIZE_NONE
    }
}

// fix a glitch at enabling Release version minifying from 5.12.3
// reset messages in 5.14.3 again, as PhoneNumber is no longer minified
// reset messages in 5.19.1 again, as SimpleContact is no longer minified
fun Context.clearAllMessagesIfNeeded(callback: () -> Unit) {
    if (!config.wasDbCleared) {
        ensureBackgroundThread {
            messagesDB.deleteAll()
            config.wasDbCleared = true
            Handler(Looper.getMainLooper()).post(callback)
        }
    } else {
        callback()
    }
}

fun Context.subscriptionManagerCompat(): SubscriptionManager {
    return getSystemService(SubscriptionManager::class.java)
}

fun Context.insertOrUpdateConversation(
    conversation: Conversation,
    cachedConv: Conversation? = conversationsDB.getConversationWithThreadId(conversation.threadId),
) {
    var updatedConv = conversation

    // Keep manually/previously assigned category when refreshed conversation payload has no category.
    if (updatedConv.category.isBlank() && cachedConv?.category?.isNotBlank() == true) {
        updatedConv = updatedConv.copy(category = cachedConv.category)
    }

    if (cachedConv != null && cachedConv.usesCustomTitle) {
        updatedConv = updatedConv.copy(
            title = cachedConv.title,
            usesCustomTitle = true
        )
    }
    // Preserve the scheduled message date so it isn't overwritten by the
    // telephony provider's last-real-SMS date when a scheduled conversation is updated.
    if (cachedConv != null && cachedConv.isScheduled) {
        updatedConv = updatedConv.copy(
            date = cachedConv.date,
            isScheduled = true
        )
    }

    // Preserve the archived status from the local database, as the telephony provider
    // often doesn't support archiving or is slow to update.
    if (cachedConv != null && cachedConv.isArchived) {
        updatedConv = updatedConv.copy(isArchived = true)
    }

    conversationsDB.insertOrUpdate(updatedConv)
}

fun Context.renameConversation(conversation: Conversation, newTitle: String): Conversation {
    val updatedConv = conversation.copy(title = newTitle, usesCustomTitle = true)
    try {
        conversationsDB.insertOrUpdate(updatedConv)
        ensureBackgroundThread {
            shortcutHelper.createOrUpdateShortcut(updatedConv)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return updatedConv
}

fun Context.createTemporaryThread(
    message: Message,
    threadId: Long = generateRandomId(),
    cachedConv: Conversation?,
) {
    val simpleContactHelper = SimpleContactsHelper(this)
    val addresses = message.participants.getAddresses()
    val photoUri = if (addresses.size == 1) {
        simpleContactHelper.getPhotoUriFromPhoneNumber(addresses.first())
    } else {
        ""
    }

    val title = if (cachedConv != null && cachedConv.usesCustomTitle) {
        cachedConv.title
    } else {
        message.participants.getThreadTitle()
    }

    val conversation = Conversation(
        threadId = threadId,
        snippet = message.body,
        date = message.date,
        read = true,
        title = title,
        photoUri = photoUri,
        isGroupConversation = addresses.size > 1,
        phoneNumber = addresses.first(),
        isScheduled = true,
        usesCustomTitle = cachedConv?.usesCustomTitle == true,
        isArchived = false,
        unreadCount = 0,
        category = cachedConv?.category ?: "",
    )
    try {
        conversationsDB.insertOrUpdate(conversation)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Context.updateScheduledMessagesThreadId(messages: List<Message>, newThreadId: Long) {
    val scheduledMessages = messages.map { it.copy(threadId = newThreadId) }.toTypedArray()
    messagesDB.insertMessages(*scheduledMessages)
}

fun Context.clearExpiredScheduledMessages(threadId: Long, messagesToDelete: List<Message>? = null) {
    val messages = messagesToDelete ?: messagesDB.getScheduledThreadMessages(threadId)
    val cutoff = System.currentTimeMillis() - 1.minutes.inWholeMilliseconds

    try {
        messages.filter { it.isScheduled && it.millis() < cutoff }.forEach { msg ->
            messagesDB.delete(msg.id)
        }
        if (messages.filterNot { it.isScheduled && it.millis() < cutoff }.isEmpty()) {
            // delete empty temporary thread
            val conversation = conversationsDB.getConversationWithThreadId(threadId)
            if (conversation != null && conversation.isScheduled) {
                conversationsDB.deleteThreadId(threadId)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return
    }
}

fun Context.rescheduleAllScheduledMessages() {
    val scheduledMessages = try {
        messagesDB.getAllScheduledMessages()
    } catch (_: Exception) {
        return
    }

    scheduledMessages.forEach { message ->
        runCatching { scheduleMessage(message) }
    }
}

fun Context.getDefaultKeyboardHeight(): Int {
    return resources.getDimensionPixelSize(R.dimen.default_keyboard_height)
}

fun Context.shouldUnarchive(): Boolean {
    return config.isArchiveAvailable && !config.keepConversationsArchived
}

fun Context.copyToUri(src: Uri, dst: Uri) {
    contentResolver.openInputStream(src)?.use { input ->
        contentResolver.openOutputStream(dst, "rwt")?.use { out ->
            input.copyTo(out)
        }
    }
}

// ===== CATEGORY EXTENSIONS =====

fun Context.getAllCategories(): List<org.fossify.messages.models.Category> {
    return try {
        categoryDB.getAllCategories()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

fun Context.getCategoryById(categoryId: Long): org.fossify.messages.models.Category? {
    return try {
        categoryDB.getCategoryById(categoryId)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun Context.createCategory(
    name: String,
    color: Int,
    icon: String = "",
    description: String = "",
    keywords: String = "",
    keywordIsRegex: Boolean = false,
    plainKeywords: String = "",
    regexPatterns: String = "",
    isDefault: Boolean = false,
    callback: ((categoryId: Long) -> Unit)? = null
) {
    ensureBackgroundThread {
        try {
            val category = org.fossify.messages.models.Category(
                id = 0,
                name = name,
                color = color,
                icon = icon,
                description = description,
                isDefault = isDefault,
                keywords = keywords,
                keywordIsRegex = keywordIsRegex,
                plainKeywords = plainKeywords,
                regexPatterns = regexPatterns
            )
            val categoryId = categoryDB.insert(category)
            // Re-run categorization for all categories so message assignments reflect the new rule
            reclassifyAllCategories()
            // Notify UI to refresh so newly created category appears in lists immediately
            try {
                org.fossify.messages.helpers.refreshConversations()
                org.fossify.messages.helpers.refreshMessages()
            } catch (_: Exception) {
            }
            callback?.let {
                Handler(Looper.getMainLooper()).post {
                    it(categoryId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast(e)
        }
    }
}

fun Context.updateCategory(
    category: org.fossify.messages.models.Category,
    callback: (() -> Unit)? = null
) {
    ensureBackgroundThread {
        try {
            val previousCategory = categoryDB.getCategoryById(category.id)
            categoryDB.update(category)
            if (previousCategory != null) {
                replaceCategoryNameInConversations(previousCategory.name, category.name)
            }
            // Re-run categorization for all categories to propagate changes
            reclassifyAllCategories()
            // Notify UI to refresh so updated category name/color propagates immediately
            try {
                org.fossify.messages.helpers.refreshConversations()
                org.fossify.messages.helpers.refreshMessages()
            } catch (_: Exception) {
            }
            callback?.let {
                Handler(Looper.getMainLooper()).post {
                    it()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast(e)
        }
    }
}

fun Context.deleteCategory(
    category: org.fossify.messages.models.Category,
    callback: (() -> Unit)? = null
) {
    ensureBackgroundThread {
        try {
            android.util.Log.d("CategoryDebug", "deleteCategory: deleting ${category.id} / ${category.name}")
            categoryDB.deleteCategory(category)
            // Remove category assignment from messages and refresh affected conversations.
            val messages = messagesDB.getMessagesByCategory(category.id)
            val affectedThreadIds = messages.map { it.threadId }.toSet()
            android.util.Log.d("CategoryDebug", "deleteCategory: messages found=${messages.size}, affectedThreads=${affectedThreadIds.size}")
            messages.forEach { message ->
                messagesDB.insertOrUpdate(message.copy(categoryId = 0, categoryName = ""))
            }
            affectedThreadIds.forEach {
                android.util.Log.d("CategoryDebug", "deleteCategory: refreshing threadId=$it")
                refreshConversationCategoryLabel(it)
            }
            // Also remove stale labels from cached conversation.category strings.
            removeCategoryNameFromConversations(category.name)
            // Notify UI to refresh conversations/messages so deleted category is removed from views
            try {
                android.util.Log.d("CategoryDebug", "deleteCategory: posting RefreshConversations/RefreshMessages events")
                org.fossify.messages.helpers.refreshConversations()
                org.fossify.messages.helpers.refreshMessages()
            } catch (_: Exception) {
                // ignore any issues posting events
            }
            callback?.let {
                Handler(Looper.getMainLooper()).post {
                    it()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast(e)
        }
    }
}

fun Context.assignMessageToCategory(messageId: Long, categoryId: Long, categoryName: String = "") {
    try {
        if (categoryName.isEmpty()) {
            getCategoryById(categoryId)?.name ?: ""
        } else {
            categoryName
        }
        // Note: Will need to fetch message, update it, and re-insert
        // This depends on MessagesDao implementation
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Context.getMessagesByCategory(categoryId: Long): List<Message> {
    return try {
        messagesDB.getMessagesByCategory(categoryId)
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

fun Context.filterMessagesByKeywords(
    messages: List<Message>,
    keywords: String,
    isRegex: Boolean = false
): List<Message> {
    if (keywords.isEmpty()) return messages
    
    return if (isRegex) {
        val regexes = keywords
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { pattern ->
                try {
                    Regex(pattern)
                } catch (_: Exception) {
                    null
                }
            }
            .toList()

        if (regexes.isEmpty()) {
            messages
        } else {
            messages.filter { message ->
                regexes.any { regex ->
                    regex.containsMatchIn(message.body) ||
                        regex.containsMatchIn(message.senderPhoneNumber)
                }
            }
        }
    } else {
        val keywordList = keywords.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        
        messages.filter { message ->
            keywordList.any { keyword ->
                message.body.lowercase().contains(keyword) ||
                message.senderPhoneNumber.contains(keyword)
            }
        }
    }
}

fun Context.getDefaultCategory(): org.fossify.messages.models.Category? {
    return getAllCategories().firstOrNull { it.isDefault }
}

fun Context.isMessageMatchingCategory(
    message: Message,
    category: org.fossify.messages.models.Category
): Boolean {
    val body = message.body
    val sender = message.senderPhoneNumber

    // Check plain words first (new format)
    if (category.plainKeywords.isNotEmpty()) {
        val plainMatch = category.plainKeywords
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .any { keyword ->
                body.lowercase().contains(keyword) || sender.contains(keyword)
            }

        if (plainMatch) return true
    }

    // Check regex patterns (new format)
    if (category.regexPatterns.isNotEmpty()) {
        val regexes = category.regexPatterns
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { pattern ->
                try {
                    Regex(pattern)
                } catch (_: Exception) {
                    null
                }
            }
            .toList()

        if (regexes.isNotEmpty()) {
            val regexMatch = regexes.any { regex ->
                regex.containsMatchIn(body) || regex.containsMatchIn(sender)
            }
            if (regexMatch) return true
        }
    }

    // Fallback to old format for backward compatibility
    if (category.plainKeywords.isEmpty() && category.regexPatterns.isEmpty() && category.keywords.isNotEmpty()) {
        return if (category.keywordIsRegex) {
            val regexes = category.keywords
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { pattern ->
                    try {
                        Regex(pattern)
                    } catch (_: Exception) {
                        null
                    }
                }
                .toList()

            if (regexes.isEmpty()) {
                false
            } else {
                regexes.any { regex ->
                    regex.containsMatchIn(body) || regex.containsMatchIn(sender)
                }
            }
        } else {
            val keywords = category.keywords.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            keywords.any { keyword ->
                body.lowercase().contains(keyword) || sender.contains(keyword)
            }
        }
    }

    return false
}

fun Context.withAutoCategory(message: Message): Message {
    val matchingCategory = getAllCategories()
        .sortedBy { it.id }
        .firstOrNull { isMessageMatchingCategory(message, it) }

    if (matchingCategory == null) {
        return message
    }

    val alreadyAssigned = message.categoryId == matchingCategory.id &&
        message.categoryName.equals(matchingCategory.name, ignoreCase = true)
    if (alreadyAssigned) {
        return message
    }

    return message.copy(categoryId = matchingCategory.id, categoryName = matchingCategory.name)
}

private fun Context.applyCategoryToExistingMessages(category: org.fossify.messages.models.Category) {
    val affectedThreadIds = HashSet<Long>()
    val messagesToUpdate = mutableListOf<org.fossify.messages.models.Message>()
    
    // Process messages in batches to avoid OOM with very large databases (like 40k+ messages)
    val allMessages = try {
        messagesDB.getAll()
    } catch (e: Exception) {
        android.util.Log.e("CategoryDebug", "applyCategoryToExistingMessages failed to load messages", e)
        emptyList()
    }

    allMessages.forEach { message ->
        val isCurrentlyAssigned = message.categoryId == category.id
        val matchesCategory = isMessageMatchingCategory(message, category)

        val updatedMessage = when {
            matchesCategory -> message.copy(categoryId = category.id, categoryName = category.name)
            isCurrentlyAssigned -> message.copy(categoryId = 0, categoryName = "")
            else -> null
        }

        if (updatedMessage != null &&
            (updatedMessage.categoryId != message.categoryId || updatedMessage.categoryName != message.categoryName)
        ) {
            messagesToUpdate.add(updatedMessage)
            affectedThreadIds.add(message.threadId)
        }
    }

    // Batch update messages to avoid multiple database locking cycles
    if (messagesToUpdate.isNotEmpty()) {
        try {
            messagesToUpdate.chunked(1000).forEach { chunk ->
                messagesDB.insertMessages(*chunk.toTypedArray())
            }
        } catch (e: Exception) {
            android.util.Log.e("CategoryDebug", "applyCategoryToExistingMessages failed to batch insert", e)
        }
    }

    // Refresh snippets and labels for affected conversations
    affectedThreadIds.forEach { threadId ->
        try {
            refreshConversationCategoryLabel(threadId)
        } catch (e: Exception) {
            android.util.Log.e("CategoryDebug", "Failed to refresh label for thread $threadId", e)
        }
    }
}

private fun Context.replaceCategoryNameInConversations(oldName: String, newName: String) {
    val normalizedOld = oldName.trim()
    val normalizedNew = newName.trim()
    if (normalizedOld.isEmpty() || normalizedNew.isEmpty() || normalizedOld == normalizedNew) {
        return
    }

    val allConversations = (conversationsDB.getNonArchived() + conversationsDB.getAllArchived())
        .distinctBy { it.threadId }
    val toUpdate = mutableListOf<org.fossify.messages.models.Conversation>()

    allConversations.forEach { conversation ->
        val updatedCategories = conversation.category
            .split(",")
            .map { label ->
                val trimmed = label.trim()
                if (trimmed.equals(normalizedOld, ignoreCase = true)) normalizedNew else trimmed
            }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .joinToString(", ")

        if (updatedCategories != conversation.category) {
            toUpdate.add(conversation.copy(category = updatedCategories))
        }
    }
    
    toUpdate.forEach { conversationsDB.insertOrUpdate(it) }
}

private fun Context.removeCategoryNameFromConversations(nameToRemove: String) {
    val normalizedTarget = nameToRemove.trim()
    if (normalizedTarget.isEmpty()) {
        return
    }

    val allConversations = (conversationsDB.getNonArchived() + conversationsDB.getAllArchived())
        .distinctBy { it.threadId }
    val toUpdate = mutableListOf<org.fossify.messages.models.Conversation>()

    allConversations.forEach { conversation ->
        val updatedCategories = conversation.category
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(normalizedTarget, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .joinToString(", ")

        if (updatedCategories != conversation.category) {
            toUpdate.add(conversation.copy(category = updatedCategories))
        }
    }
    
    toUpdate.forEach { conversationsDB.insertOrUpdate(it) }
}

fun Context.refreshConversationCategoryLabel(threadId: Long) {
    val conversation = conversationsDB.getConversationWithThreadId(threadId) ?: return
    val categorizedMessages = messagesDB.getThreadMessages(threadId)
        .filter { it.categoryName.isNotBlank() }

    val existingCategories = conversation.category
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val messageCategories = categorizedMessages
        .map { it.categoryName.trim() }
        .filter { it.isNotEmpty() }

    val conversationCategories = (existingCategories + messageCategories)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .joinToString(", ")

    if (conversation.category != conversationCategories) {
        android.util.Log.d("CategoryDebug", "refreshConversationCategoryLabel: threadId=$threadId old='${conversation.category}' new='$conversationCategories'")
        conversationsDB.insertOrUpdate(conversation.copy(category = conversationCategories))
    }
}

/**
 * Re-run categorization for all categories. This ensures message-category assignments are
 * recalculated after category create/update/delete operations.
 */
fun Context.reclassifyAllCategories() {
    ensureBackgroundThread {
        try {
            val categories = try {
                categoryDB.getAllCategories().sortedBy { it.id }
            } catch (e: Exception) {
                android.util.Log.e("CategoryDebug", "reclassifyAllCategories failed to load categories", e)
                emptyList()
            }

            // Optimization: Process all categories in one single pass over messages
            val allMessages = try {
                messagesDB.getAll()
            } catch (e: Exception) {
                android.util.Log.e("CategoryDebug", "reclassifyAllCategories failed to load messages", e)
                emptyList()
            }

            if (categories.isEmpty()) {
                val toUpdate = allMessages.filter { it.categoryId != 0L }
                if (toUpdate.isNotEmpty()) {
                    val affectedThreads = toUpdate.map { it.threadId }.toSet()
                    toUpdate.chunked(1000).forEach { chunk ->
                        messagesDB.insertMessages(*chunk.map { it.copy(categoryId = 0, categoryName = "") }.toTypedArray())
                    }
                    affectedThreads.forEach { refreshConversationCategoryLabel(it) }
                }
                return@ensureBackgroundThread
            }

            val messagesToUpdate = mutableListOf<org.fossify.messages.models.Message>()
            val affectedThreadIds = HashSet<Long>()

            allMessages.forEach { message ->
                // Priority is determined by category ID (deterministic)
                val matchingCategory = categories.firstOrNull { isMessageMatchingCategory(message, it) }
                
                val newId = matchingCategory?.id ?: 0L
                val newName = matchingCategory?.name ?: ""

                if (message.categoryId != newId || message.categoryName != newName) {
                    messagesToUpdate.add(message.copy(categoryId = newId, categoryName = newName))
                    affectedThreadIds.add(message.threadId)
                }
            }

            if (messagesToUpdate.isNotEmpty()) {
                messagesToUpdate.chunked(1000).forEach { chunk ->
                    try {
                        messagesDB.insertMessages(*chunk.toTypedArray())
                    } catch (e: Exception) {
                        android.util.Log.e("CategoryDebug", "reclassifyAllCategories batch update failed", e)
                    }
                }
            }

            affectedThreadIds.forEach { threadId ->
                try {
                    refreshConversationCategoryLabel(threadId)
                } catch (e: Exception) {
                    android.util.Log.e("CategoryDebug", "Failed to refresh label for thread $threadId", e)
                }
            }

            // Notify UI
            try {
                org.fossify.messages.helpers.refreshConversations()
                org.fossify.messages.helpers.refreshMessages()
            } catch (_: Exception) {
            }
        } catch (e: Exception) {
            android.util.Log.e("CategoryDebug", "Global reclassify error", e)
        }
    }
}

