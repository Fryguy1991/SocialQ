package com.chrisf.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisf.socialq.R
import com.chrisf.socialq.model.QueueModel
import com.chrisf.socialq.userinterface.adapters.holders.QueueDisplayHolder
import com.chrisf.socialq.userinterface.interfaces.IQueueSelectionListener

class QueueDisplayAdapter : BaseRecyclerViewAdapter<QueueDisplayHolder, QueueModel>(), IQueueSelectionListener{
    lateinit var listener: IQueueSelectionListener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueDisplayHolder {
        return QueueDisplayHolder(LayoutInflater.from(parent.context).inflate(R.layout.holder_joinable_queue, parent, false))
    }

    override fun onBindViewHolder(holder: QueueDisplayHolder, position: Int) {
        val queueToDisplay = itemList[position]

        holder.listener = this
        holder.setModel(queueToDisplay)
    }

    override fun queueSelected(queueModel: QueueModel) {
        listener.queueSelected(queueModel)
    }
}