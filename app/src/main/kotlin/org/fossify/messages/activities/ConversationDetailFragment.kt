package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.adapters.ThreadAdapter
import org.fossify.messages.databinding.FragmentConversationDetailBinding
import org.fossify.messages.extensions.*
import org.fossify.messages.messaging.sendMessageCompat
import org.fossify.messages.models.Message
import org.fossify.messages.models.ThreadItem
import org.fossify.messages.models.ThreadItem.ThreadDateTime

class ConversationDetailFragment : Fragment() {
    private var threadId: Long = 0L
    private var threadTitle: String = ""
    private var detailBaseBottomPadding = 0
    private var _binding: FragmentConversationDetailBinding? = null
    private val binding get() = _binding!!
    private var threadAdapter: ThreadAdapter? = null
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            threadId = args.getLong(ARG_THREAD_ID, 0L)
            threadTitle = args.getString(ARG_THREAD_TITLE, "") ?: ""
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConversationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.detailTitle.text = threadTitle
        detailBaseBottomPadding = binding.detailMessagesList.paddingBottom
        setupMessagesAdapter()
        setupMessageInput()
        setupWindowInsets()
        loadThreadMessages()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.detailRootLayout) { view, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            
            val bottomBar = activity?.findViewById<View>(R.id.saved_views_bottom_bar)
            val selectionBar = activity?.findViewById<View>(R.id.selection_bottom_bar)
            
            val barHeight = if (selectionBar?.visibility == View.VISIBLE) {
                selectionBar.height
            } else if (bottomBar?.visibility == View.VISIBLE) {
                bottomBar.height
            } else {
                0
            }

            // If keyboard is visible, the bottom bars are hidden in MainActivity.
            // We only need to lift by the keyboard height itself.
            val finalBottomPadding = if (imeHeight > 0) {
                imeHeight
            } else {
                maxOf(systemBars, barHeight)
            }

            view.updatePadding(bottom = finalBottomPadding)
            insets
        }
    }

    private fun setupMessagesAdapter() {
        val activity = requireActivity() as? SimpleActivity ?: return
        val recyclerView = binding.detailMessagesList

        threadAdapter = ThreadAdapter(
            activity = activity,
            recyclerView = recyclerView,
            itemClick = { },
            isRecycleBin = false,
            deleteMessages = { _, _, _ -> }
        )
        recyclerView.adapter = threadAdapter
        (recyclerView.layoutManager as? LinearLayoutManager)?.apply {
            // Keep showing latest messages
            stackFromEnd = true
        }
    }

    private fun setupMessageInput() {
        val activity = requireActivity() as? SimpleActivity ?: return
        val messageHolder = binding.messageHolder
        val textColor = activity.getProperTextColor()

        activity.updateTextColors(messageHolder.root)
        messageHolder.root.setBackgroundColor(activity.getProperBackgroundColor())
        messageHolder.threadTypeMessage.apply {
            setHintTextColor(textColor.adjustAlpha(LOWER_ALPHA))
            setTextColor(textColor)
        }

        messageHolder.threadSendMessage.apply {
            setTextColor(textColor)
            compoundDrawables.forEach {
                it?.applyColorFilter(textColor)
            }
            setOnClickListener {
                sendMessage()
            }
        }

        messageHolder.threadAddAttachment.applyColorFilter(textColor)

        messageHolder.threadTypeMessage.onTextChangeListener {
            val isNotEmpty = it.isNotEmpty()
            messageHolder.threadSendMessage.apply {
                alpha = if (isNotEmpty) 1f else 0.4f
                isClickable = isNotEmpty
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendMessage() {
        val activity = requireActivity() as? SimpleActivity ?: return
        val text = binding.messageHolder.threadTypeMessage.value
        if (text.isEmpty()) return

        ensureBackgroundThread {
            val participants = activity.getThreadParticipants(threadId, null)
            val addresses = participants.getAddresses()
            val subscriptionId = activity.subscriptionManagerCompat().activeSubscriptionInfoList?.firstOrNull()?.subscriptionId ?: -1
            
            activity.sendMessageCompat(text, addresses, subscriptionId, emptyList(), null)
            activity.runOnUiThread {
                binding.messageHolder.threadTypeMessage.text?.clear()
                loadThreadMessages()
                binding.detailMessagesList.postDelayed({
                    val lastIndex = threadAdapter?.currentList?.lastIndex ?: 0
                    if (lastIndex >= 0) {
                        binding.detailMessagesList.smoothScrollToPosition(lastIndex)
                    }
                }, 300)
            }
        }
    }

    private fun loadThreadMessages() {
        loadJob?.cancel()
        val context = context ?: return
        
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val threadItems = withContext(Dispatchers.IO) {
                    val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                    val privateContacts = MyContactsContentProvider.getSimpleContacts(context, privateCursor)
                    val messages: ArrayList<Message> = context.getMessages(threadId)
                    val participants = context.getThreadParticipants(threadId, null)

                    if (privateContacts.isNotEmpty()) {
                        val senderNumbersToReplace = HashMap<String, String>()
                        val senderPhotosToReplace = HashMap<String, String>()

                        participants.forEach { participant ->
                            val normalizedNumber = participant.phoneNumbers.firstOrNull()?.normalizedNumber ?: ""
                            if (normalizedNumber.isNotEmpty()) {
                                privateContacts.firstOrNull { it.doesHavePhoneNumber(normalizedNumber) }?.apply {
                                    senderNumbersToReplace[normalizedNumber] = name
                                    senderPhotosToReplace[normalizedNumber] = photoUri
                                }
                            }
                        }

                        messages.forEach { message ->
                            senderNumbersToReplace[message.senderPhoneNumber]?.let { message.senderName = it }
                            senderPhotosToReplace[message.senderPhoneNumber]?.let { message.senderPhotoUri = it }
                        }
                    }
                    getThreadItems(messages)
                }

                if (threadItems.isEmpty()) {
                    binding.detailMessagesList.visibility = View.GONE
                    binding.detailNoMessages.visibility = View.VISIBLE
                } else {
                    binding.detailMessagesList.visibility = View.VISIBLE
                    binding.detailNoMessages.visibility = View.GONE
                    threadAdapter?.submitList(threadItems)
                    binding.detailMessagesList.post {
                        val lastIndex = threadAdapter?.currentList?.lastIndex ?: 0
                        if (lastIndex >= 0) {
                            binding.detailMessagesList.scrollToPosition(lastIndex)
                        }
                    }
                }
            } catch (_: Exception) {
                binding.detailMessagesList.visibility = View.GONE
                binding.detailNoMessages.visibility = View.VISIBLE
            }
        }
    }

    private fun getThreadItems(messages: List<Message>): List<ThreadItem> {
        val items = ArrayList<ThreadItem>()
        val sortedMessages = messages.sortedBy { it.date }

        var prevDateTime = 0
        var prevSIMId = -2
        
        sortedMessages.forEach { message ->
            val isSentFromDifferentKnownSIM = prevSIMId != -1 && message.subscriptionId != -1 && prevSIMId != message.subscriptionId
            if (message.date - prevDateTime > MIN_DATE_TIME_DIFF_SECS || isSentFromDifferentKnownSIM) {
                // In 2-pane mode we skip the SIM ID string for simplicity unless explicitly needed
                items.add(ThreadDateTime(message.date, ""))
                prevDateTime = message.date
            }
            items.add(message)
            prevSIMId = message.subscriptionId
        }
        return items
    }

    /**
     * Update the fragment to show a different thread/conversation
     * Called from MainActivity when user selects a different conversation in two-pane mode
     */
    fun updateThread(newThreadId: Long, newThreadTitle: String) {
        threadId = newThreadId
        threadTitle = newThreadTitle
        if (isAdded && view != null) {
            binding.detailTitle.text = threadTitle
            loadThreadMessages()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_THREAD_ID = "arg_thread_id"
        private const val ARG_THREAD_TITLE = "arg_thread_title"
        private const val MIN_DATE_TIME_DIFF_SECS = 300

        @JvmStatic
        fun newInstance(threadId: Long, title: String) = ConversationDetailFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_THREAD_ID, threadId)
                putString(ARG_THREAD_TITLE, title)
            }
        }
    }
}



