package com.chrisf.socialq.model

import android.os.Parcel
import android.os.Parcelable

data class JoinableQueueModel(val endpointId: String, val queueName: String, val ownerName: String, val isFairPlayActive: Boolean) : Parcelable {
    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<JoinableQueueModel> {
            override fun createFromParcel(parcel: Parcel): JoinableQueueModel {
                return JoinableQueueModel(parcel)
            }

            override fun newArray(size: Int): Array<JoinableQueueModel?> {
                return arrayOfNulls(size)
            }
        }
    }

    private constructor(parcel: Parcel) : this(
            endpointId = parcel.readString(),
            queueName = parcel.readString(),
            ownerName = parcel.readString(),
            isFairPlayActive = parcel.readByte() != 0.toByte())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(endpointId)
        parcel.writeString(queueName)
        parcel.writeString(ownerName)
        parcel.writeByte(if (isFairPlayActive) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }
}