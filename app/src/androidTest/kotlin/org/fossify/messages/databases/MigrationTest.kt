package org.fossify.messages.databases

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MessagesDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate10To11() {
        // Create database with version 10
        var db = helper.createDatabase(TEST_DB, 10)

        // Insert some data using SQL (Room version 10 schema)
        db.execSQL("INSERT INTO conversations (thread_id, snippet, date, read, title, photo_uri, is_group_conversation, phone_number, is_scheduled, uses_custom_title, archived, unread_count) " +
                "VALUES (1, 'Hello', 12345678, 1, 'Test User', '', 0, '123456', 0, 0, 0, 0)")
        
        db.execSQL("INSERT INTO messages (id, body, type, participants, date, read, thread_id, is_mms, sender_name, sender_photo_uri, subscription_id, status, is_scheduled, sender_phone_number) " +
                "VALUES (100, 'Test body', 1, '123456', 12345678, 1, 1, 0, 'Test User', '', 1, 1, 0, '123456')")

        db.close()

        // Re-open and migrate to 11
        db = helper.runMigrationsAndValidate(TEST_DB, 11, true)

        // Verify data exists and new columns have default values
        val cursor = db.query("SELECT * FROM messages WHERE id = 100")
        assertEquals(true, cursor.moveToFirst())
        
        // In migration 10->11, category_name and category_id were added
        val categoryNameIndex = cursor.getColumnIndex("category_name")
        val categoryIdIndex = cursor.getColumnIndex("category_id")
        
        assertEquals("", cursor.getString(categoryNameIndex))
        assertEquals(0, cursor.getInt(categoryIdIndex))
        assertEquals("Test body", cursor.getString(cursor.getColumnIndex("body")))
        
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        // Start at v10
        helper.createDatabase(TEST_DB, 10).apply {
            execSQL("INSERT INTO conversations (thread_id, snippet, date, read, title, photo_uri, is_group_conversation, phone_number, is_scheduled, uses_custom_title, archived, unread_count) VALUES (1, 'v10', 100, 1, 'v10', '', 0, '10', 0, 0, 0, 0)")
            close()
        }

        // Migrate to latest (v13)
        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true)
        
        // Check if v10 data persisted
        val cursor = db.query("SELECT * FROM conversations WHERE thread_id = 1")
        assertEquals(true, cursor.moveToFirst())
        assertEquals("v10", cursor.getString(cursor.getColumnIndex("snippet")))
        cursor.close()
    }
}
