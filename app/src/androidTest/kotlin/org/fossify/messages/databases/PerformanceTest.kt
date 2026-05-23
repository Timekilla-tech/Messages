package org.fossify.messages.databases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.models.Message
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random

@RunWith(AndroidJUnit4::class)
class PerformanceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun benchmark1kMessagesInsertion() {
        val count = 1000
        val random = Random()
        val messages = ArrayList<Message>()
        
        for (i in 1..count) {
            val sender = "Sender $i"
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
            messages.add(
                Message(
                    id = random.nextLong().coerceAtLeast(1000000) + i,
                    body = "Message body $i",
                    type = 1,
                    status = 1,
                    participants = contacts,
                    date = (System.currentTimeMillis() / 1000).toInt(),
                    read = false,
                    threadId = (i / 10).toLong() + 1,
                    isMMS = false,
                    attachment = null,
                    senderPhoneNumber = sender,
                    senderName = sender,
                    senderPhotoUri = "",
                    subscriptionId = 1
                )
            )
        }

        val startTime = System.currentTimeMillis()
        context.messagesDB.insertMessages(*messages.toTypedArray())
        val endTime = System.currentTimeMillis()
        
        val duration = endTime - startTime
        android.util.Log.d("PerformanceTest", "Inserted $count messages in $duration ms")
        
        assertTrue("Insertion took too long: $duration ms", duration < 5000) // Expect < 5s for 1k on modern devices
    }
}
