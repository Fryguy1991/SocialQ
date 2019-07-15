package com.chrisf.socialq.userinterface.adapters.holders

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.R
import com.chrisf.socialq.model.QueueModel
import com.chrisf.socialq.userinterface.interfaces.IQueueSelectionListener

class QueueDisplayHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val ownerNameText = itemView.findViewById<TextView>(R.id.tv_queue_owner)
    private val queueNameText = itemView.findViewById<TextView>(R.id.tv_queue_name)
    private lateinit var queueModel: QueueModel
    lateinit var listener: IQueueSelectionListener

    init{
        itemView.setOnClickListener { listener.queueSelected(queueModel) }
    }

    fun setModel(model: QueueModel) {
        queueModel = model

        ownerNameText.text = model.ownerName
        queueNameText.text = model.queueName
    }
}