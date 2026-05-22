package org.fossify.messages.databases

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MessagesDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate10ToLatestKeepsDataAndAddsNewColumns() {
        val latestVersion = MessagesDatabase::class.java
            .getAnnotation(androidx.room.Database::class.java)
            ?.version
            ?: 13

        var db = helper.createDatabase(testDbName, 10)

        // Seed data using the Room v10 schema.
        db.execSQL(
            "INSERT INTO conversations (thread_id, snippet, date, read, title, photo_uri, is_group_conversation, phone_number, is_scheduled, uses_custom_title, archived, unread_count) " +
                "VALUES (1, 'Hello', 12345678, 1, 'Test User', '', 0, '123456', 0, 0, 0, 5)"
        )

        db.execSQL(
            "INSERT INTO messages (id, body, type, participants, date, read, thread_id, is_mms, sender_name, sender_photo_uri, subscription_id, status, is_scheduled, sender_phone_number) " +
                "VALUES (100, 'Test body', 1, '123456', 12345678, 1, 1, 0, 'Test User', '', 1, 1, 0, '123456')"
        )

        db.close()

        db = helper.runMigrationsAndValidate(testDbName, latestVersion, true)

        db.query("SELECT * FROM messages WHERE id = 100").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Test body", cursor.getString(cursor.getColumnIndexOrThrow("body")))
            assertEquals("Test User", cursor.getString(cursor.getColumnIndexOrThrow("sender_name")))
            assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("category_name")))
            assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("category_id")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("is_scheduled")))
        }

        db.query("SELECT * FROM conversations WHERE thread_id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Hello", cursor.getString(cursor.getColumnIndexOrThrow("snippet")))
            assertEquals(5, cursor.getInt(cursor.getColumnIndexOrThrow("unread_count")))
            assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("category")))
        }

        db.query("SELECT * FROM categories").use { cursor ->
            assertEquals(0, cursor.count)
            val expectedColumns = listOf(
                "id",
                "name",
                "color",
                "icon",
                "description",
                "is_default",
                "keywords",
                "keywords_is_regex",
                "plain_keywords",
                "regex_patterns",
            )
            expectedColumns.forEach { column ->
                assertTrue(cursor.columnNames.contains(column))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11KeepsDataAndAddsCategorySchema() {
        var db = helper.createDatabase(testDbName, 10)

        db.execSQL(
            "INSERT INTO conversations (thread_id, snippet, date, read, title, photo_uri, is_group_conversation, phone_number, is_scheduled, uses_custom_title, archived, unread_count) " +
                "VALUES (2, 'Old snippet', 2222, 0, 'Another User', '', 1, '987654', 0, 0, 0, 0)"
        )

        db.execSQL(
            "INSERT INTO messages (id, body, type, participants, date, read, thread_id, is_mms, sender_name, sender_photo_uri, subscription_id, status, is_scheduled, sender_phone_number) " +
                "VALUES (200, 'Old body', 2, '987654', 2222, 0, 2, 0, 'Another User', '', 2, -1, 0, '987654')"
        )

        db.close()

        db = helper.runMigrationsAndValidate(testDbName, 11, true)

        db.query("SELECT * FROM messages WHERE id = 200").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Old body", cursor.getString(cursor.getColumnIndexOrThrow("body")))
            assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("category_name")))
            assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("category_id")))
        }

        db.query("SELECT * FROM conversations WHERE thread_id = 2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Old snippet", cursor.getString(cursor.getColumnIndexOrThrow("snippet")))
            assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("category")))
        }

        db.query("SELECT * FROM categories").use { cursor ->
            assertEquals(0, cursor.count)
            val expectedColumns = listOf(
                "id",
                "name",
                "color",
                "icon",
                "description",
                "is_default",
                "keywords",
            )
            expectedColumns.forEach { column ->
                assertTrue(cursor.columnNames.contains(column))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate11To12KeepsDataAndAddsRegexFlag() {
        var db = helper.createDatabase(testDbName, 11)

        db.execSQL(
            "INSERT INTO categories (id, name, color, icon, description, is_default, keywords) " +
                "VALUES (1, 'Promotions', 16711680, 'ic_filter_list_vector', 'Promo folder', 0, 'sale,offer')"
        )

        db.execSQL(
            "INSERT INTO conversations (thread_id, snippet, date, read, title, photo_uri, is_group_conversation, phone_number, is_scheduled, uses_custom_title, archived, unread_count, category) " +
                "VALUES (3, 'Regex test', 3333, 1, 'Regex User', '', 0, '555000', 0, 0, 0, 2, 'Promotions')"
        )

        db.execSQL(
            "INSERT INTO messages (id, body, type, participants, date, read, thread_id, is_mms, sender_name, sender_photo_uri, subscription_id, status, is_scheduled, category_name, category_id, sender_phone_number) " +
                "VALUES (300, 'Regex body', 1, '555000', 3333, 1, 3, 0, 'Regex User', '', 3, 1, 0, 'Promotions', 1, '555000')"
        )

        db.close()

        db = helper.runMigrationsAndValidate(testDbName, 12, true)

        db.query("SELECT * FROM categories WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Promotions", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("keywords_is_regex")))
        }

        db.query("SELECT * FROM messages WHERE id = 300").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Regex body", cursor.getString(cursor.getColumnIndexOrThrow("body")))
            assertEquals("Promotions", cursor.getString(cursor.getColumnIndexOrThrow("category_name")))
            assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("category_id")))
        }

        db.query("SELECT * FROM conversations WHERE thread_id = 3").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Regex test", cursor.getString(cursor.getColumnIndexOrThrow("snippet")))
            assertEquals("Promotions", cursor.getString(cursor.getColumnIndexOrThrow("category")))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate12To13KeepsDataAndAddsKeywordColumns() {
        var db = helper.createDatabase(testDbName, 12)

        db.execSQL(
            "INSERT INTO categories (id, name, color, icon, description, is_default, keywords, keywords_is_regex) " +
                "VALUES (2, 'Alerts', 65280, 'ic_filter_list_vector', 'Alert folder', 1, 'alert', 1)"
        )

        db.execSQL(
            "INSERT INTO conversations (thread_id, snippet, date, read, title, photo_uri, is_group_conversation, phone_number, is_scheduled, uses_custom_title, archived, unread_count, category) " +
                "VALUES (4, 'Keyword test', 4444, 0, 'Keyword User', '', 0, '444000', 0, 0, 0, 1, 'Alerts')"
        )

        db.execSQL(
            "INSERT INTO messages (id, body, type, participants, date, read, thread_id, is_mms, sender_name, sender_photo_uri, subscription_id, status, is_scheduled, category_name, category_id, sender_phone_number) " +
                "VALUES (400, 'Keyword body', 1, '444000', 4444, 0, 4, 0, 'Keyword User', '', 4, 1, 0, 'Alerts', 2, '444000')"
        )

        db.close()

        db = helper.runMigrationsAndValidate(testDbName, 13, true)

        db.query("SELECT * FROM categories WHERE id = 2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Alerts", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("keywords_is_regex")))
            assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("plain_keywords")))
            assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("regex_patterns")))
        }

        db.query("SELECT * FROM messages WHERE id = 400").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Keyword body", cursor.getString(cursor.getColumnIndexOrThrow("body")))
            assertEquals("Alerts", cursor.getString(cursor.getColumnIndexOrThrow("category_name")))
            assertEquals(2L, cursor.getLong(cursor.getColumnIndexOrThrow("category_id")))
        }

        db.query("SELECT * FROM conversations WHERE thread_id = 4").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Keyword test", cursor.getString(cursor.getColumnIndexOrThrow("snippet")))
            assertEquals("Alerts", cursor.getString(cursor.getColumnIndexOrThrow("category")))
        }
    }
}
