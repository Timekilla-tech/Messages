package org.fossify.messages.helpers

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.extensions.categoryDB
import org.fossify.messages.extensions.withAutoCategory
import org.fossify.messages.models.Category
import org.fossify.messages.models.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilteringTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun testAutoCategorizationByKeyword() {
        // 1. Create a "Bank" category
        val bankCategory = Category(
            name = "BankTest",
            color = 0xFF2196F3.toInt(),
            plainKeywords = "khan,golomt,transfer"
        )
        val categoryId = context.categoryDB.insert(bankCategory)
        bankCategory.id = categoryId

        // 2. Create a mock message that should match
        val body = "Khan Bank: You received a transfer of 50,000 MNT"
        val sender = "131917"
        val message = createMockMessage(body, sender)

        // 3. Test auto-categorization
        val categorizedMessage = context.withAutoCategory(message)

        assertEquals("BankTest", categorizedMessage.categoryName)
        assertEquals(categoryId, categorizedMessage.categoryId)

        // Cleanup
        context.categoryDB.deleteCategory(bankCategory)
    }

    @Test
    fun testAutoCategorizationByRegex() {
        // 1. Create an "OTP" category
        val otpCategory = Category(
            name = "OTPTest",
            color = 0xFFFF9800.toInt(),
            regexPatterns = "code|otp|[0-9]{4,6}"
        )
        val categoryId = context.categoryDB.insert(otpCategory)
        otpCategory.id = categoryId

        // 2. Create a mock message that should match regex
        val body = "Your login code is 123456"
        val sender = "Verify"
        val message = createMockMessage(body, sender)

        // 3. Test auto-categorization
        val categorizedMessage = context.withAutoCategory(message)

        assertEquals("OTPTest", categorizedMessage.categoryName)
        assertEquals(categoryId, categorizedMessage.categoryId)

        // Cleanup
        context.categoryDB.deleteCategory(otpCategory)
    }

    private fun createMockMessage(body: String, sender: String): Message {
        val contacts = arrayListOf(
            SimpleContact(
                rawId = 0,
                contactId = 0,
                name = sender,
                photoUri = "",
                phoneNumbers = arrayListOf(PhoneNumber(sender, 0, "", sender)),
                birthdays = ArrayList(),
                anniversaries = ArrayList()
            )
        )
        return Message(
            id = 12345L,
            body = body,
            type = 1, // Inbox
            status = 1,
            participants = contacts,
            date = (System.currentTimeMillis() / 1000).toInt(),
            read = false,
            threadId = 1L,
            isMMS = false,
            attachment = null,
            senderPhoneNumber = sender,
            senderName = sender,
            senderPhotoUri = "",
            subscriptionId = 1
        )
    }
}
