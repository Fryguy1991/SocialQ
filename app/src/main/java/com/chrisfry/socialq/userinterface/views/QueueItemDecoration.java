package com.chrisfry.socialq.userinterface.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

import com.chrisfry.socialq.R;

/**
 * Between item decoration for queue list view
 */
public class QueueItemDecoration extends RecyclerView.ItemDecoration {
    // Drawable for divider
    private Drawable mDividerDrawable;

    public QueueItemDecoration(Context context) {
        mDividerDrawable = ContextCompat.getDrawable(context, R.drawable.queue_divider);
        if(mDividerDrawable != null) {
            mDividerDrawable.setAlpha(255 / 4);
        }
    }

    @Override
    public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
        int dividerLeft = parent.getPaddingLeft();
        int dividerRight = parent.getWidth() - parent.getPaddingRight();

        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount - 1; i++) {
            View child = parent.getChildAt(i);

            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();

            int dividerTop = child.getBottom() + params.bottomMargin;
            int dividerBottom = dividerTop + mDividerDrawable.getIntrinsicHeight();

            mDividerDrawable.setBounds(dividerLeft, dividerTop, dividerRight, dividerBottom);
            mDividerDrawable.draw(canvas);
        }
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);

        outRect.top = mDividerDrawable.getIntrinsicHeight();
    }
}
