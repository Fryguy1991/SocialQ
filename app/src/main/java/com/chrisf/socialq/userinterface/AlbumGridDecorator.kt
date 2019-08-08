package com.chrisf.socialq.userinterface

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.jetbrains.anko.dip

class AlbumGridDecorator : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {

        val index = parent.indexOfChild(view)
        val childCount = parent.childCount

        // TODO: Larger margins for first and last row
        outRect.top = parent.context.dip(8)
        outRect.bottom = parent.context.dip(8)

        when (index % SPAN_COUNT) {
            0 -> {
                outRect.left = parent.context.dip(16)
                outRect.right = parent.context.dip(8)
            }
            SPAN_COUNT - 1 -> {
                outRect.right = parent.context.dip(16)
                outRect.left = parent.context.dip(8)
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