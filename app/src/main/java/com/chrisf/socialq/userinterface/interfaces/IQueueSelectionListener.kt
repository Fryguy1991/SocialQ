package com.chrisf.socialq.userinterface.interfaces

import com.chrisf.socialq.model.QueueModel

interface IQueueSelectionListener {
    fun queueSelected(queueModel: QueueModel)
}