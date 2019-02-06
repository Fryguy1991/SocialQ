package com.chrisfry.socialq.userinterface.interfaces

import com.chrisfry.socialq.model.JoinableQueueModel

interface IQueueSelectionListener {
    fun queueSelected(queueModel: JoinableQueueModel)
}