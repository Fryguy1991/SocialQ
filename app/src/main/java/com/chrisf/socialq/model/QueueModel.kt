package com.chrisf.socialq.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class QueueModel(
        val endpointId: String,
        val queueName: String,
        val ownerName: String,
        val isFairPlayActive: Boolean
) : Parcelable