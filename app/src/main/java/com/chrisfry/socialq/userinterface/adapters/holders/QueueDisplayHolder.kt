package com.chrisfry.socialq.userinterface.adapters.holders

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chrisfry.socialq.R

class QueueDisplayHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val ownerNameText = itemView.findViewById<TextView>(R.id.tv_queue_owner)
    private val queueNameText = itemView.findViewById<TextView>(R.id.tv_queue_name)

    fun setOwnerName(name: String) {
        ownerNameText.text = name
    }

    fun setQueueName(name: String) {
        queueNameText.text = name
    }
}