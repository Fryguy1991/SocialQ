package com.chrisf.socialq.userinterface.adapters.holders

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.R

open class SimpleTextHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    protected val textView = itemView.findViewById<TextView>(R.id.tv_holder_text)

    fun setText(displayText: String) {
        textView.text = displayText
    }
}