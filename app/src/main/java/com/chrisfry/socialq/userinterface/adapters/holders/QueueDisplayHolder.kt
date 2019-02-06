package com.chrisfry.socialq.userinterface.adapters.holders

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chrisfry.socialq.R
import com.chrisfry.socialq.model.JoinableQueueModel
import com.chrisfry.socialq.userinterface.interfaces.IQueueSelectionListener

class QueueDisplayHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val ownerNameText = itemView.findViewById<TextView>(R.id.tv_queue_owner)
    private val queueNameText = itemView.findViewById<TextView>(R.id.tv_queue_name)
    private lateinit var queueModel: JoinableQueueModel
    lateinit var listener: IQueueSelectionListener

    init{
        itemView.setOnClickListener { listener.queueSelected(queueModel) }
    }

    fun setModel(model: JoinableQueueModel) {
        queueModel = model

        ownerNameText.text = model.ownerName
        queueNameText.text = model.queueName
    }
}