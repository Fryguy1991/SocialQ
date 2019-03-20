package com.chrisf.socialq.userinterface.interfaces

import com.chrisf.socialq.model.JoinableQueueModel

interface IQueueSelectionListener {
    fun queueSelected(queueModel: JoinableQueueModel)
}