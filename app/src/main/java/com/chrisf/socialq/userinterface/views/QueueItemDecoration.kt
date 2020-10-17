package com.chrisf.socialq.userinterface.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.chrisf.socialq.R
import com.chrisf.socialq.extensions.dip

/**
 * Between item decoration for queue list view
 */
class QueueItemDecoration(context: Context) : ItemDecoration() {
    // Drawable for divider
    private val mDividerDrawable = ContextCompat.getDrawable(context, R.drawable.queue_divider)

    init {
        mDividerDrawable?.alpha = 255 / 4
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val dividerLeft = parent.context.dip(16)
        val dividerRight = parent.width - parent.context.dip(16)
        val childCount = parent.childCount
        for (i in 0 until childCount - 1) {
            val child = parent.getChildAt(i)
            val params = child.layoutParams as RecyclerView.LayoutParams
            val dividerTop = child.bottom + params.bottomMargin
            val dividerBottom = dividerTop + (mDividerDrawable?.intrinsicHeight ?: 0)
            mDividerDrawable?.setBounds(dividerLeft, dividerTop, dividerRight, dividerBottom)
            mDividerDrawable?.draw(canvas)
        }
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)
        outRect.top = mDividerDrawable!!.intrinsicHeight
    }
}