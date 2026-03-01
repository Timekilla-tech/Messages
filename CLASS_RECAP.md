# Fossify Messages - Class & Architecture Recap

## Overview
Fossify Messages is an open-source Android SMS/MMS messaging application built with Kotlin, Room Database, and Material Design. It provides features for messaging, blocking, archiving, importing/exporting messages, and message scheduling.

---

## Core Application

### **App.kt**
- Extends `FossifyApp` (base Fossify framework class)
- Initializes the application lifecycle
- Registers `ContentObserver` for contacts changes to invalidate caches when contacts are updated
- Reschedules all scheduled messages on app startup
- Has app lock feature enabled
- **Purpose**: Application entry point and initialization

---

## Activities (UI/UX Layer)

### **SimpleActivity.kt** (Base Activity)
- Abstract base class extending `BaseSimpleActivity`
- Provides app icon launcher configurations (19 different color variations)
- Defines repository name and app launcher name
- **Purpose**: Common functionality for all activities in the app

### **MainActivity.kt** (Conversations List)
- Main conversations screen displaying all SMS/MMS threads
- Features search functionality and real-time conversation updates
- Handles conversation creation, deletion, archiving, and management
- Integrates with EventBus for listening to message/conversation updates
- Supports shortcuts and quick actions
- Manages app role (default SMS app) setting
- **Key Features**: Search, filtering, selection actions, conversation management

### **ThreadActivity.kt** (Message Thread/Conversation Details)
- Displays individual conversation messages in a thread view
- Handles message sending (SMS/MMS) with attachment support
- Supports message scheduling, editing, deletion, and reactions
- Manages media attachments (images, videos, documents, vCards)
- Handles notification features (mark as read/unread, delete)
- Integrates with keyboard visibility and UI animations
- **Key Features**: Message composition, attachment handling, message history, delivery status

### **SettingsActivity.kt** (Settings & Configuration)
- Provides user settings interface
- Manages message import/export functionality
- Handles blocked numbers, blocked keywords management
- Font size, date/time format, notification, and privacy settings
- Manages recycle bin (deleted messages retention)
- **Key Features**: Preferences, import/export, backup management

### **SplashActivity.kt**
- Initial startup screen before main app loads

### **NewConversationActivity.kt**
- Handles creating new conversations with contacts
- Contact selection and validation

### **ConversationDetailsActivity.kt**
- Shows detailed information about a conversation
- Contact details and message statistics

### **ArchivedConversationsActivity.kt**
- Displays archived conversations
- Unarchive functionality

### **RecycleBinConversationsActivity.kt**
- Shows deleted conversations pending permanent deletion
- Recovery and permanent deletion options

### **ManageBlockedKeywordsActivity.kt**
- Allows users to manage blocked keywords
- Add/remove keywords that trigger message filtering

### **VCardViewerActivity.kt**
- Displays contact information (vCard format) from received messages

---

## Database Layer

### **MessagesDatabase.kt** (Room Database)
- Central database for message persistence
- Version 10 with migration support
- **Tables**:
  - `Conversation` - SMS/MMS thread metadata
  - `Message` - Individual messages
  - `RecycleBinMessage` - Deleted messages awaiting permanent deletion
  - `Attachment` - File attachments (images, videos, etc.)
  - `MessageAttachment` - Links between messages and attachments
  - `Draft` - Draft message text per conversation
- **DAOs**: ConversationsDao, MessagesDao, AttachmentsDao, MessageAttachmentsDao, DraftsDao
- **Type Converters**: Handles serialization of complex types (ArrayList, etc.)

---

## Database Interfaces (DAOs)

### **ConversationsDao**
- CRUD operations for Conversation entities
- Query conversations by thread ID, date range, read status
- Update conversation properties (title, archived status, unread count)
- Batch operations for multiple conversations

### **MessagesDao**
- CRUD operations for Message entities
- Query messages by thread ID, sender, date range
- Filter by read/unread, scheduled status
- Support for pagination and sorting

### **AttachmentsDao**
- Manage attachment metadata
- Query attachments by message ID

### **MessageAttachmentsDao**
- Link messages to attachments (many-to-many relationship)

### **DraftsDao**
- Store and retrieve message drafts per conversation

---

## Data Models

### **Conversation.kt**
- **Fields**: threadId (PK), snippet, date, read, title, photoUri, isGroupConversation, phoneNumber, isScheduled, usesCustomTitle, isArchived, unreadCount
- Represents SMS/MMS conversation thread metadata
- Includes comparison utilities for DiffUtil

### **Message.kt**
- **Fields**: id (PK), body, type, status, participants, date, read, threadId, isMMS, attachment, senderPhoneNumber, senderName, senderPhotoUri, subscriptionId, isScheduled
- Extends ThreadItem abstract class
- Contains helper methods: isReceivedMessage(), getSender(), getStableId(), getSelectionKey()
- **Type**: Can be SMS or MMS

### **Draft.kt**
- **Fields**: threadId (PK), body, date
- Stores draft message content per conversation

### **Attachment.kt**
- **Fields**: id, messageId, uriString, mimetype, width, height, filename
- Represents file attachments (images, documents, vCards)
- Provides Uri conversion utility

### **MessageAttachment.kt**
- Links messages to multiple attachments
- Supports many-to-many relationship

### **RecycleBinMessage.kt**
- Represents messages in recycle bin before permanent deletion
- Includes retention period before auto-deletion

### **Events.kt**
- EventBus event classes for inter-component communication
- Allows UI updates when messages/conversations change

### **NamePhoto.kt**
- Contact name and photo caching object
- Used by MessagingCache for performance

### **ImportResult.kt**
- Result of import operation (success/failure counts)

### **BackupType.kt**
- Enum: SMS_BACKUP, MMS_BACKUP for backup type identification

---

## Adapters (RecyclerView Binding)

### **BaseConversationsAdapter.kt** (Abstract Base)
- Common functionality for conversation list adapters
- Selection mode support with context action bar (CAB)
- Multiselect capabilities
- Fast scroll support

### **ConversationsAdapter.kt** (Main Conversations)
- Displays conversations in list format
- Context action menu: delete, archive, block, mark read/unread, rename
- Long-press selection and swipe actions
- Real-time update support

### **ArchivedConversationsAdapter.kt**
- Same structure as ConversationsAdapter but for archived items

### **RecycleBinConversationsAdapter.kt**
- Lists deleted conversations
- Restore and permanent delete options

### **ThreadAdapter.kt** (Message List)
- Displays messages within a conversation thread
- Multiple item types: sent messages, received messages, date dividers, sending status, attachments
- Handles message selection and context actions
- Attachment preview support (images, documents, vCards)
- DiffUtil for efficient updates
- Glide image loading with caching

### **SearchResultsAdapter.kt**
- Displays search results across all messages/conversations

### **ContactsAdapter.kt**
- Shows contact list for conversation creation

### **AttachmentsAdapter.kt**
- Displays attachment previews and metadata

### **AutoCompleteTextViewAdapter.kt**
- Autocomplete suggestions for contact selection

### **VCardViewerAdapter.kt**
- Displays vCard contact information

---

## Messaging/SMS Layer

### **SmsManager.kt**
- Wrapper around Android `TelephonyManager.SmsManager`
- Subscription-aware SMS management (multi-SIM support)
- Singleton pattern with subscription caching
- **Purpose**: Central SMS sending infrastructure

### **SmsSender.kt**
- Handles SMS message sending logic
- Validates destination and message content
- Divides long messages appropriately
- Sends multipart SMS with delivery/sent intents
- Supports delivery reports
- **Process**: Validation → Splitting → Sending via SmsManager

### **SmsException.kt**
- Custom exception for SMS sending errors
- Error codes: EMPTY_DESTINATION, ERROR_SENDING_MESSAGE

### **Messaging.kt**
- High-level messaging interfaces and utilities
- Abstract base for message operations

### **MessagingUtils.kt**
- Helper utilities for messaging operations
- Short code detection, phone number formatting
- Message type detection (SMS vs MMS)

### **ScheduledMessage.kt**
- Data class for scheduled message metadata
- Stores send time, recipient, message body

---

## Broadcast Receivers (System Integration)

### **SmsReceiver.kt** (SMS Received)
- Listens for incoming SMS messages
- Validates sender against blocked numbers/keywords
- Creates/updates conversations
- Shows notifications for new messages
- Handles subscription information
- **Process**: Receive → Validate → Database Insert → Notify UI

### **MmsReceiver.kt**
- Handles incoming MMS messages
- Similar flow to SmsReceiver with attachment handling

### **SmsSentReceiver.kt**
- Listens for SMS sent confirmation
- Updates message status in database

### **SmsStatusSentReceiver.kt**
- Handles SMS delivery status updates
- Updates message delivery status

### **SmsStatusDeliveredReceiver.kt**
- Confirms SMS delivery to recipient

### **MmsSentReceiver.kt**
- Confirms MMS sent status

### **SendStatusReceiver.kt**
- Generic send status handler

### **DeleteSmsReceiver.kt**
- Notification action receiver for delete
- Removes message from database

### **MarkAsReadReceiver.kt**
- Notification action receiver for mark as read
- Updates message read status

### **DirectReplyReceiver.kt**
- Handles direct reply from notification
- Sends reply message without opening app

### **ScheduledMessageReceiver.kt**
- Triggers scheduled message sending at specified time
- Alarm manager integration

### **RescheduleAlarmsReceiver.kt**
- Boot receiver to reschedule alarms after device restart

---

## Dialogs (User Input/Confirmation)

### **DeleteConfirmationDialog.kt**
- Confirms message deletion with permanent/temporary options

### **MessageDetailsDialog.kt**
- Shows detailed message information (timestamp, sender, delivery status)

### **SelectTextDialog.kt**
- Allows user to select and copy text from messages

### **RenameConversationDialog.kt**
- Allows renaming group conversations

### **ScheduleMessageDialog.kt**
- UI for scheduling message sending at future time

### **AddBlockedKeywordDialog.kt**
- Add keyword to blocking filter list

### **ManageBlockedKeywordsAdapter.kt**
- List and manage blocked keywords

### **ExportBlockedKeywordsDialog.kt**
- Export blocked keywords to file

### **ExportMessagesDialog.kt**
- Options for exporting messages (JSON/XML format)

### **ImportMessagesDialog.kt**
- Handles message import with file selection and validation

### **InvalidNumberDialog.kt**
- Error dialog for invalid phone numbers

---

## Helpers & Utilities

### **Config.kt** (Preferences/Settings)
- App-wide configuration management
- Stores user preferences: character counter, send on enter, delivery reports, lock screen visibility, font size, etc.
- Extends `BaseConfig` from Fossify commons
- **Key Settings**: Message formatting, notification behavior, privacy, appearance

### **MessagingCache.kt** (LRU Cache)
- In-memory LRU cache (size: 512)
- Caches contact names/photos
- Caches participant lists per conversation
- **Purpose**: Performance optimization for frequent lookups

### **NotificationHelper.kt**
- Creates and manages notifications for received messages
- Notification channels for Android O+
- Direct reply and delete actions
- Custom notification sounds/vibration
- **Features**: Rich notifications, large icons, direct reply

### **AttachmentUtils.kt**
- Parses MMS SMIL (Synchronized Multimedia Integration Language) attachments
- Extracts attachment information from MMS metadata
- Supports images, videos, audio, vCards

### **AttachmentPreviews.kt**
- Generates preview images/thumbnails for attachments
- Handles different media types

### **ImageCompressor.kt**
- Compresses images before sending as MMS
- Quality and size optimization
- Prevents MMS size limit exceeded errors

### **MessagesImporter.kt**
- Imports messages from backup files (JSON/XML)
- Handles batch message insertion
- Format validation and error handling
- **Process**: Parse → Validate → Insert into Database

### **MessagesReader.kt**
- Reads messages from Android SMS/MMS provider
- Builds conversation threads from provider data
- **Purpose**: Loading existing conversations from device

### **MessagesWriter.kt**
- Writes messages to device SMS/MMS provider
- Persistence of messages to Android SMS app
- Maintains synchronization with system SMS database

### **BlockedKeywordsExporter.kt**
- Exports blocked keywords list to file

### **BlockedKeywordsImporter.kt**
- Imports blocked keywords from file

### **VCardParser.kt**
- Parses vCard format contact data from messages

### **ReceiverUtils.kt**
- Helper utilities for broadcast receivers
- Message filtering logic

### **ShortcutHelper.kt**
- Creates app shortcuts (conversation shortcuts)
- Quick access to frequently contacted people

### **SmsIntentParser.kt**
- Parses SMS intents from system
- Extracts phone numbers and messages from system intents

### **Constants.kt**
- Application-wide constants (strings, IDs, keys)
- Intent extra keys, notification IDs, file types

### **Converters.kt** (Room Type Converters)
- Serialization/deserialization for Room Database
- Handles ArrayList<SimpleContact>, custom types
- JSON conversion utilities

---

## Extensions (Kotlin Extensions)

### **Context.kt**
- Extension methods on Context for database/helper access
- Convenient property getters for config, database, cache

### **Activity.kt**
- Activity-specific convenience methods
- Navigation helpers

### **String.kt**
- String utility extensions

### **Cursor.kt**
- Cursor manipulation helpers for database queries

### **Collections.kt**
- Collection utility extensions

### **Bitmap.kt**
- Bitmap manipulation and compression

### **RecyclerView.kt**
- RecyclerView-specific helpers

### **View.kt**
- View animation and state helpers

### **Temporal.kt**
- Date/time formatting helpers

### **Math.kt**
- Math utility functions

### **SimpleContact.kt**
- Extensions for SimpleContact model from commons

### **gson/** (JSON Processing)
- **Gson.kt**: Gson configuration and utilities
- **JsonObject.kt**: JSON object manipulation
- **JsonElement.kt**: JSON element utilities
- **MapDeserializerDoubleAsIntFix.kt**: Fixes for JSON number parsing

---

## Services

### **HeadlessSmsSendService.kt**
- Service for sending SMS in background without UI
- Handles SMS delivery when app is not active
- **Purpose**: Background message sending operations

---

## Architecture Patterns

### **Design Patterns Used**:
1. **Repository Pattern**: Database access through DAOs
2. **Singleton Pattern**: MessagingCache, SmsManager
3. **Observer Pattern**: BroadcastReceivers, EventBus
4. **Adapter Pattern**: RecyclerView adapters
5. **Factory Pattern**: Dialog creation
6. **Strategy Pattern**: Different backup/export formats

### **Threading**:
- `ensureBackgroundThread()` for database operations
- Main thread for UI updates
- AlarmManager for scheduled messages

### **Data Flow**:
1. **Incoming Message**: BroadcastReceiver → Database → Notification → UI Update
2. **Outgoing Message**: UI Input → SmsManager → BroadcastReceiver (Sent/Delivered) → Update Status
3. **Scheduled Message**: AlarmManager → ScheduledMessageReceiver → Send

### **Caching Strategy**:
- LRU memory cache for contacts (512 items)
- Database for persistent storage
- ContentObserver for cache invalidation

---

## Key Features Implementation

| Feature | Components |
|---------|-----------|
| **SMS/MMS** | SmsManager, SmsSender, SmsReceiver, MmsReceiver |
| **Conversations** | ConversationsAdapter, ConversationsDao, MainActivity |
| **Threading** | ThreadAdapter, ThreadActivity, MessagesDao |
| **Blocking** | ReceiverUtils, Config, blocked keywords/numbers |
| **Notifications** | NotificationHelper, SmsReceiver, BroadcastReceivers |
| **Backup/Restore** | MessagesImporter, MessagesWriter, MessagesExporter |
| **Scheduling** | ScheduleMessageDialog, ScheduledMessageReceiver, AlarmManager |
| **Attachments** | AttachmentUtils, AttachmentPreviews, ImageCompressor |
| **Searching** | SearchResultsAdapter, MainActivity, MessagesDao |
| **Archiving** | Config, ConversationsDao, ArchivedConversationsActivity |
| **Import/Export** | MessagesImporter, MessagesWriter, ExportMessagesDialog |

---

## Dependencies (Key Libraries)
- **Room**: Database persistence
- **Glide**: Image loading and caching
- **EventBus**: Component communication
- **Gson**: JSON serialization
- **android-smsmms**: SMS/MMS sending (Klinker library)
- **Fossify Commons**: Base utilities and UI components

---

## Summary
Fossify Messages is a well-architected messaging app that separates concerns into:
- **UI Layer**: Activities and Adapters
- **Data Layer**: Room Database, DAOs, Models
- **Business Logic**: Helpers, Messaging services, Receivers
- **System Integration**: BroadcastReceivers, Services

The app uses modern Android patterns (Room DB, LiveData events via EventBus), efficiently handles permissions, and provides comprehensive messaging functionality while maintaining code organization and reusability.

