package org.fossify.messages.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.adapters.ThreadAdapter
import org.fossify.messages.databinding.FragmentConversationDetailBinding
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.models.Message
import org.fossify.messages.models.ThreadItem

class ConversationDetailFragment : Fragment() {
    private var threadId: Long = 0L
    private var threadTitle: String = ""
    private var detailBaseBottomPadding = 0
    private var _binding: FragmentConversationDetailBinding? = null
    private val binding get() = _binding!!
    private var threadAdapter: ThreadAdapter? = null

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
        updateBottomInsetPadding()
        loadThreadMessages()
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

    private fun loadThreadMessages() {
        ensureBackgroundThread {
            try {
                val messages: ArrayList<Message> = requireContext().getMessages(threadId)
                if (messages.isEmpty()) {
                    activity?.runOnUiThread {
                        binding.detailMessagesList.visibility = View.GONE
                        binding.detailNoMessages.visibility = View.VISIBLE
                    }
                } else {
                    // Message already extends ThreadItem, so we can cast directly
                    val threadItems: List<ThreadItem> = messages.map { it }
                    activity?.runOnUiThread {
                        binding.detailMessagesList.visibility = View.VISIBLE
                        binding.detailNoMessages.visibility = View.GONE
                        threadAdapter?.submitList(threadItems)
                        // Auto-scroll to latest message
                        binding.detailMessagesList.post {
                            if (threadItems.isNotEmpty()) {
                                val lastIndex = threadAdapter?.currentList?.lastIndex ?: 0
                                binding.detailMessagesList.scrollToPosition(lastIndex)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                activity?.runOnUiThread {
                    binding.detailMessagesList.visibility = View.GONE
                    binding.detailNoMessages.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateBottomInsetPadding() {
        val bottomBar = activity?.findViewById<View>(R.id.saved_views_bottom_bar)
        val bottomInset = bottomBar?.height ?: 0
        if (bottomInset > 0) {
            binding.detailMessagesList.updatePadding(bottom = detailBaseBottomPadding + bottomInset)
            binding.detailNoMessages.updatePadding(bottom = detailBaseBottomPadding + bottomInset)
        } else {
            binding.detailMessagesList.post { updateBottomInsetPadding() }
        }
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

        @JvmStatic
        fun newInstance(threadId: Long, title: String) = ConversationDetailFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_THREAD_ID, threadId)
                putString(ARG_THREAD_TITLE, title)
            }
        }
    }
}



