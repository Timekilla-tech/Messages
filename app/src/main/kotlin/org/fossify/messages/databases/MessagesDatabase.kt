@file:Suppress("MagicNumber")
package org.fossify.messages.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fossify.messages.helpers.Converters
import org.fossify.messages.interfaces.AttachmentsDao
import org.fossify.messages.interfaces.CategoryDao
import org.fossify.messages.interfaces.ConversationsDao
import org.fossify.messages.interfaces.DraftsDao
import org.fossify.messages.interfaces.MessageAttachmentsDao
import org.fossify.messages.interfaces.MessagesDao
import org.fossify.messages.models.Attachment
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Draft
import org.fossify.messages.models.Message
import org.fossify.messages.models.MessageAttachment
import org.fossify.messages.models.RecycleBinMessage
import org.fossify.messages.models.Category

@Database(
    entities = [
        Conversation::class,
        Attachment::class,
        MessageAttachment::class,
        Message::class,
        RecycleBinMessage::class,
        Draft::class,
        Category::class
    ],
    version = 11
)
@TypeConverters(Converters::class)
abstract class MessagesDatabase : RoomDatabase() {

    abstract fun ConversationsDao(): ConversationsDao

    abstract fun AttachmentsDao(): AttachmentsDao

    abstract fun MessageAttachmentsDao(): MessageAttachmentsDao

    abstract fun MessagesDao(): MessagesDao

    abstract fun DraftsDao(): DraftsDao
    
    abstract fun CategoryDao(): CategoryDao

    companion object {
        private var db: MessagesDatabase? = null

        fun getInstance(context: Context): MessagesDatabase {
            if (db == null) {
                synchronized(MessagesDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(
                            context = context.applicationContext,
                            klass = MessagesDatabase::class.java,
                            name = "conversations.db"
                        )
                            .fallbackToDestructiveMigration(dropAllTables = true)
                            .addMigrations(MIGRATION_1_2)
                            .addMigrations(MIGRATION_2_3)
                            .addMigrations(MIGRATION_3_4)
                            .addMigrations(MIGRATION_4_5)
                            .addMigrations(MIGRATION_5_6)
                            .addMigrations(MIGRATION_6_7)
                            .addMigrations(MIGRATION_7_8)
                            .addMigrations(MIGRATION_8_9)
                            .addMigrations(MIGRATION_9_10)
                            .addMigrations(MIGRATION_10_11)
                            .build()
                    }
                }
            }
            return db!!
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY NOT NULL, `body` TEXT NOT NULL, `type` INTEGER NOT NULL, `participants` TEXT NOT NULL, `date` INTEGER NOT NULL, `read` INTEGER NOT NULL, `thread_id` INTEGER NOT NULL, `is_mms` INTEGER NOT NULL, `attachment` TEXT, `sender_name` TEXT NOT NULL, `sender_photo_uri` TEXT NOT NULL, `subscription_id` INTEGER NOT NULL)")

                    execSQL("CREATE TABLE IF NOT EXISTS `message_attachments` (`id` INTEGER PRIMARY KEY NOT NULL, `text` TEXT NOT NULL, `attachments` TEXT NOT NULL)")

                    execSQL("CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `message_id` INTEGER NOT NULL, `uri_string` TEXT NOT NULL, `mimetype` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `filename` TEXT NOT NULL)")
                    execSQL("CREATE UNIQUE INDEX `index_attachments_message_id` ON `attachments` (`message_id`)")
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    execSQL("CREATE TABLE conversations_new (`thread_id` INTEGER NOT NULL PRIMARY KEY, `snippet` TEXT NOT NULL, `date` INTEGER NOT NULL, `read` INTEGER NOT NULL, `title` TEXT NOT NULL, `photo_uri` TEXT NOT NULL, `is_group_conversation` INTEGER NOT NULL, `phone_number` TEXT NOT NULL)")

                    execSQL(
                        "INSERT OR IGNORE INTO conversations_new (thread_id, snippet, date, read, title, photo_uri, is_group_conversation, phone_number) " +
                                "SELECT thread_id, snippet, date, read, title, photo_uri, is_group_conversation, phone_number FROM conversations"
                    )

                    execSQL("DROP TABLE conversations")

                    execSQL("ALTER TABLE conversations_new RENAME TO conversations")

                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_conversations_id` ON `conversations` (`thread_id`)")
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    execSQL("ALTER TABLE messages ADD COLUMN status INTEGER NOT NULL DEFAULT -1")
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    execSQL("ALTER TABLE messages ADD COLUMN is_scheduled INTEGER NOT NULL DEFAULT 0")
                    execSQL("ALTER TABLE conversations ADD COLUMN is_scheduled INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    execSQL("ALTER TABLE conversations ADD COLUMN uses_custom_title INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    execSQL("ALTER TABLE messages ADD COLUMN sender_phone_number TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    execSQL("ALTER TABLE conversations ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                    execSQL("CREATE TABLE IF NOT EXISTS `recycle_bin_messages` (`id` INTEGER NOT NULL PRIMARY KEY, `deleted_ts` INTEGER NOT NULL)")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recycle_bin_messages_id` ON `recycle_bin_messages` (`id`)")
                }
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    execSQL("CREATE TABLE IF NOT EXISTS `drafts` (`thread_id` INTEGER NOT NULL PRIMARY KEY, `body` TEXT NOT NULL, `date` INTEGER NOT NULL)")
                }
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    execSQL("ALTER TABLE conversations ADD COLUMN unread_count INTEGER NOT NULL DEFAULT 0")
                }
            }
        }
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    // 1) Create Room-aligned categories table
                    execSQL("""
                CREATE TABLE IF NOT EXISTS `categories` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `color` INTEGER NOT NULL,
                    `icon` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `is_default` INTEGER NOT NULL,
                    `keywords` TEXT NOT NULL
                )
            """.trimIndent())

                    // 2) Backfill from legacy table only if it exists
                    if (hasTable("category")) {
                        execSQL("""
                    INSERT OR IGNORE INTO `categories` (`id`, `name`, `color`, `icon`, `description`, `is_default`, `keywords`)
                    SELECT `id`, `name`, 0, '', '', 0, '' FROM `category`
                """.trimIndent())
                    }

                    // 3) Ensure category columns exist for entities using them
                    try { execSQL("ALTER TABLE `messages` ADD COLUMN `category_name` TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
                    try { execSQL("ALTER TABLE `messages` ADD COLUMN `category_id` INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
                    try { execSQL("ALTER TABLE `conversations` ADD COLUMN `category` TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}

                    // 4) Rebuild messages table so category_* columns match Room schema (no SQL default metadata)
                    execSQL("DROP TABLE IF EXISTS `messages_new`")
                    execSQL("CREATE TABLE `messages_new` (`id` INTEGER PRIMARY KEY NOT NULL, `body` TEXT NOT NULL, `type` INTEGER NOT NULL, `status` INTEGER NOT NULL, `participants` TEXT NOT NULL, `date` INTEGER NOT NULL, `read` INTEGER NOT NULL, `thread_id` INTEGER NOT NULL, `is_mms` INTEGER NOT NULL, `attachment` TEXT, `sender_phone_number` TEXT NOT NULL, `sender_name` TEXT NOT NULL, `sender_photo_uri` TEXT NOT NULL, `subscription_id` INTEGER NOT NULL, `is_scheduled` INTEGER NOT NULL, `category_name` TEXT NOT NULL, `category_id` INTEGER NOT NULL)")

                    execSQL("INSERT INTO messages_new (id, body, type, status, participants, date, read, thread_id, is_mms, attachment, sender_phone_number, sender_name, sender_photo_uri, subscription_id, is_scheduled, category_name, category_id) SELECT id, body, type, status, participants, date, read, thread_id, is_mms, attachment, sender_phone_number, sender_name, sender_photo_uri, subscription_id, is_scheduled, category_name, category_id FROM messages")

                    execSQL("DROP TABLE messages")
                    execSQL("ALTER TABLE messages_new RENAME TO messages")
                    execSQL("CREATE INDEX IF NOT EXISTS `index_messages_category_id` ON `messages` (`category_id`)")
                }
            }
        }

        private fun SupportSQLiteDatabase.hasTable(tableName: String): Boolean {
            query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='$tableName' LIMIT 1")
                .use { cursor ->
                    return cursor.moveToFirst()
                }
        }

    }
}
