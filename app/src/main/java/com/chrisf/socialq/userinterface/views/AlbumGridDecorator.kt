package com.chrisf.socialq.userinterface.views

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.extensions.dip

class AlbumGridDecorator : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val index = parent.getChildAdapterPosition(view)

        val maxFirstRowIndex = SPAN_COUNT - 1
        var minLastRowIndex = parent.adapter!!.itemCount / SPAN_COUNT
        if (parent.adapter!!.itemCount % SPAN_COUNT == 0) {
            minLastRowIndex--
        }
        minLastRowIndex *= SPAN_COUNT

        when  {
            index <= maxFirstRowIndex -> {
                outRect.top = parent.context.dip(16)
                outRect.bottom = parent.context.dip(8)
            }
            index >= minLastRowIndex -> {
                outRect.top = parent.context.dip(8)
                outRect.bottom = parent.context.dip(16)
            }
            else -> {
                outRect.top = parent.context.dip(8)
                outRect.bottom = parent.context.dip(8)
            }
        }

        when (index % SPAN_COUNT) {
            0 -> {
                outRect.left = parent.context.dip(16)
                outRect.right = parent.context.dip(8)
            }
            SPAN_COUNT - 1 -> {
                outRect.left = parent.context.dip(8)
                outRect.right = parent.context.dip(16)
            }
            else -> {
                outRect.left = parent.context.dip(8)
                outRect.right = parent.context.dip(8)
            }
        }
    }

    companion object{
        const val SPAN_COUNT = 2
    }
}