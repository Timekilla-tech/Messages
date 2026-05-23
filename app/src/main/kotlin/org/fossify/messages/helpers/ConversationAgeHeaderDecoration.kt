package org.fossify.messages.helpers

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.R as CommonsR
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.models.Conversation

class ConversationAgeHeaderDecoration(
    private val activity: SimpleActivity,
    private val conversationsProvider: () -> List<Conversation>,
) : RecyclerView.ItemDecoration() {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = activity.resources.getDimension(CommonsR.dimen.list_secondary_text_size)
        isFakeBoldText = true
    }

    private val horizontalPadding = activity.resources.getDimensionPixelSize(CommonsR.dimen.activity_margin)
    private val verticalPadding = activity.resources.getDimensionPixelSize(CommonsR.dimen.small_margin)
    private val headerHeight = (textPaint.textSize + verticalPadding * 2).toInt()

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position != RecyclerView.NO_POSITION && shouldShowHeader(position)) {
            outRect.top = headerHeight
        }
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(canvas, parent, state)
        backgroundPaint.color = activity.getProperBackgroundColor()
        textPaint.color = activity.getProperTextColor()

        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || !shouldShowHeader(position)) {
                continue
            }

            val bucket = getBucketForPosition(position) ?: continue
            val headerTop = child.top - headerHeight
            val headerBottom = child.top

            canvas.drawRect(
                parent.paddingLeft.toFloat(),
                headerTop.toFloat(),
                (parent.width - parent.paddingRight).toFloat(),
                headerBottom.toFloat(),
                backgroundPaint,
            )

            val baseline = headerTop + (headerHeight - textPaint.fontMetrics.bottom - textPaint.fontMetrics.top) / 2f
            canvas.drawText(
                activity.getString(bucket.titleRes),
                (parent.paddingLeft + horizontalPadding).toFloat(),
                baseline,
                textPaint,
            )
        }
    }

    private fun shouldShowHeader(position: Int): Boolean {
        val items = conversationsProvider()
        if (position !in items.indices) return false
        if (position == 0) return true

        val previousBucket = getBucket(items[position - 1].date)
        val currentBucket = getBucket(items[position].date)
        return previousBucket != currentBucket
    }

    private fun getBucketForPosition(position: Int): ConversationAgeBucket? {
        val items = conversationsProvider()
        return items.getOrNull(position)?.let { getBucket(it.date) }
    }

    private fun getBucket(timestampSeconds: Int): ConversationAgeBucket {
        return getConversationAgeBucket(timestampSeconds)
    }
}
