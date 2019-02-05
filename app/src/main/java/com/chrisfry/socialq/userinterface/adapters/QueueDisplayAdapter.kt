package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.model.JoinableQueueModel
import com.chrisfry.socialq.userinterface.adapters.holders.QueueDisplayHolder

class QueueDisplayAdapter : BaseRecyclerViewAdapter<QueueDisplayHolder, JoinableQueueModel>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueDisplayHolder {
        return QueueDisplayHolder(LayoutInflater.from(parent.context).inflate(R.layout.joinable_queue_holder, parent, false))
    }

    override fun onBindViewHolder(holder: QueueDisplayHolder, position: Int) {
        val queueToDisplay = itemList[position]

        holder.setOwnerName(queueToDisplay.ownerName)
        holder.setQueueName(queueToDisplay.queueName)
    }
}