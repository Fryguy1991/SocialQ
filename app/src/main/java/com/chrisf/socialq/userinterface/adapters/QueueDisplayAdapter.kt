package com.chrisf.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.R
import com.chrisf.socialq.model.QueueModel
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import kotlinx.android.synthetic.main.holder_joinable_queue.view.*
import java.util.concurrent.TimeUnit

class QueueDisplayAdapter : BaseRecyclerViewAdapter<QueueDisplayHolder, QueueModel>(), QueueClickHandler{

    private val queueSelectionRelay : PublishRelay<QueueModel> = PublishRelay.create()
    val queueSelection: Observable<QueueModel> = queueSelectionRelay.hide()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueDisplayHolder {
        return QueueDisplayHolder(LayoutInflater.from(parent.context).inflate(R.layout.holder_joinable_queue, parent, false))
    }

    override fun onBindViewHolder(holder: QueueDisplayHolder, position: Int) {
        val queueToDisplay = itemList[position]

        holder.bind(queueToDisplay, this)
    }

    override fun handleQueueClick(queue: QueueModel) {
        queueSelectionRelay.accept(queue)
    }
}

class QueueDisplayHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun bind(model: QueueModel, clickHandler: QueueClickHandler) {
        itemView.tv_queue_owner.text = model.ownerName
        itemView.tv_queue_name.text = model.queueName

        itemView.clicks()
                .throttleFirst(300, TimeUnit.MILLISECONDS)
                .subscribe { clickHandler.handleQueueClick(model) }
    }
}

interface QueueClickHandler {
    fun handleQueueClick(queue: QueueModel)
}