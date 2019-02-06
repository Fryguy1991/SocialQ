package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.model.JoinableQueueModel
import com.chrisfry.socialq.userinterface.adapters.holders.QueueDisplayHolder
import com.chrisfry.socialq.userinterface.interfaces.IQueueSelectionListener

class QueueDisplayAdapter : BaseRecyclerViewAdapter<QueueDisplayHolder, JoinableQueueModel>(), IQueueSelectionListener{
    lateinit var listener: IQueueSelectionListener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueDisplayHolder {
        return QueueDisplayHolder(LayoutInflater.from(parent.context).inflate(R.layout.joinable_queue_holder, parent, false))
    }

    override fun onBindViewHolder(holder: QueueDisplayHolder, position: Int) {
        val queueToDisplay = itemList[position]

        holder.listener = this
        holder.setModel(queueToDisplay)
    }

    override fun queueSelected(queueModel: JoinableQueueModel) {
        listener.queueSelected(queueModel)
    }
}