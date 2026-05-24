package org.fossify.messages.helpers

import android.content.Context
import android.os.Build
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.extensions.categoryDB
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.models.Category
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Message
import java.util.Random

object MockDataHelper {

    // Move to 2-billion range to ensure absolute isolation from system IDs
    private const val MOCK_ID_START = 2_000_000_000L

    fun injectMockData(context: Context) {
        ensureBackgroundThread {
            val startTime = System.currentTimeMillis()
            val categoryDao = context.categoryDB
            val conversationsDao = context.conversationsDB
            val messagesDao = context.messagesDB

            // Detection for your spare test device (Galaxy Z Fold 3)
            val currentModel = Build.MODEL
            val isSpareDevice = currentModel.contains("SM-F926N", ignoreCase = true) || 
                               currentModel.contains("Galaxy Z Fold3", ignoreCase = true)

            // 1. Create/Ensure Categories
            val categories = listOf(
                Category(name = "Bank", color = 0xFF2196F3.toInt(), plainKeywords = "bank,transfer,payment,khan,golomt"),
                Category(name = "OTP", color = 0xFFFF9800.toInt(), regexPatterns = "code|otp|[0-9]{4,6}"),
                Category(name = "Work", color = 0xFF4CAF50.toInt(), plainKeywords = "meeting,deadline,project,email"),
                Category(name = "Spam", color = 0xFFF44336.toInt(), plainKeywords = "win,prize,lottery,free")
            )

            val existingCategories = categoryDao.getAllCategories()
            categories.forEach { category ->
                if (existingCategories.none { it.name == category.name }) {
                    categoryDao.insert(category)
                }
            }

            val updatedCategories = categoryDao.getAllCategories()
            val random = Random()
            val threadCount = 1000
            val messagesPerThread = 10
            val totalMessages = threadCount * messagesPerThread
            
            val messagesToInsert = mutableListOf<Message>()
            val conversationsToInsert = mutableListOf<Conversation>()

            // 2. Generate Bulk Mock Data as a single batch
            for (i in 1..threadCount) {
                val threadId = MOCK_ID_START + i
                val senderPrefix = when (random.nextInt(4)) {
                    0 -> "Khan Bank"
                    1 -> "Verify"
                    2 -> "Manager"
                    else -> "WinPrize"
                }
                
                // Add unique sender identifiers to avoid any possible system lookup
                val sender = "$senderPrefix (Mock)"
                val mockPhoneNumber = "TEST-$i"
                
                val categoryName = when (senderPrefix) {
                    "Khan Bank" -> "Bank"
                    "Verify" -> "OTP"
                    "Manager" -> "Work"
                    else -> "Spam"
                }
                val categoryId = updatedCategories.find { it.name == categoryName }?.id ?: 0L
                val baseDate = (System.currentTimeMillis() / 1000).toInt() - (random.nextInt(604800))
                
                var lastBody = ""
                for (j in 1..messagesPerThread) {
                    val msgDate = baseDate + (j * 60)
                    val body = when (categoryName) {
                        "Bank" -> "Transaction of ${random.nextInt(100000)} MNT successful."
                        "OTP" -> "Your security code is ${1000 + random.nextInt(9000)}"
                        "Work" -> "Please update the task #$j for the project."
                        else -> "Congratulations! You won ${random.nextInt(1000000)}$! Click here."
                    }
                    lastBody = body

                    val contacts = arrayListOf(
                        SimpleContact(
                            rawId = 0,
                            contactId = 0,
                            name = sender,
                            photoUri = "",
                            phoneNumbers = arrayListOf(PhoneNumber(mockPhoneNumber, 0, "", mockPhoneNumber)),
                            birthdays = ArrayList(),
                            anniversaries = ArrayList()
                        )
                    )

                    messagesToInsert.add(Message(
                        id = MOCK_ID_START + (i * 1000L) + j,
                        body = body,
                        type = 1,
                        status = 1,
                        participants = contacts,
                        date = msgDate,
                        read = random.nextBoolean(),
                        threadId = threadId,
                        isMMS = false,
                        attachment = null,
                        senderPhoneNumber = mockPhoneNumber,
                        senderName = sender,
                        senderPhotoUri = "",
                        subscriptionId = 1,
                        categoryName = categoryName,
                        categoryId = categoryId
                    ))
                }

                conversationsToInsert.add(Conversation(
                    threadId = threadId,
                    snippet = lastBody,
                    date = baseDate + (messagesPerThread * 60),
                    read = false,
                    title = sender,
                    photoUri = "",
                    isGroupConversation = false,
                    phoneNumber = mockPhoneNumber,
                    category = categoryName
                ))
            }

            // Perform Batch Insertion in chunks to handle the massive 40,000 message volume
            messagesToInsert.chunked(1000).forEach { chunk ->
                messagesDao.insertMessages(*chunk.toTypedArray())
            }
            conversationsToInsert.chunked(100).forEach { chunk ->
                chunk.forEach { conversationsDao.insertOrUpdate(it) }
            }

            val duration = System.currentTimeMillis() - startTime
            context.toast("Injected $totalMessages messages in $duration ms")
            
            refreshConversations()

            // 3. Vanishing Logic (only on non-spare devices)
            if (!isSpareDevice) {
                Thread.sleep(5000) // Stay visible for 5 seconds
                messagesToInsert.forEach { messagesDao.delete(it.id) }
                conversationsToInsert.forEach { conversationsDao.deleteThreadId(it.threadId) }
                context.toast("Mock data vanished (Auto-cleanup)")
                refreshConversations()
            } else {
                android.util.Log.d("MockDebug", "Device $currentModel detected - Data is now Persistent")
                context.toast("Data persisted on $currentModel")
            }
        }
    }

    fun injectStressTestRegex(context: Context) {
        ensureBackgroundThread {
            val categoryDao = context.categoryDB
            val startTime = System.currentTimeMillis()

            // Generate 100 random but valid regex patterns
            val regexList = mutableListOf<String>()
            for (i in 1..1000) {
                val pattern = when (i % 3) {
                    0 -> "[a-z]{3}-$i"
                    1 -> "\\bstress$i\\b"
                    else -> "^msg$i.*[0-9]{2}"
                }
                regexList.add(pattern)
            }

            val stressCategory = Category(
                name = "StressTest",
                color = 0xFF9C27B0.toInt(), // Purple
                regexPatterns = regexList.joinToString("\n")
            )

            // Insert or update the category
            val existing = categoryDao.getAllCategories().find { it.name == "StressTest" }
            if (existing != null) {
                categoryDao.update(stressCategory.copy(id = existing.id))
            } else {
                categoryDao.insert(stressCategory)
            }

            val duration = System.currentTimeMillis() - startTime
            context.toast("Injected 100 Regex patterns in $duration ms")
            
            refreshConversations()
        }
    }
}
