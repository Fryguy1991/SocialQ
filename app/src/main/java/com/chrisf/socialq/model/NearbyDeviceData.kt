package com.chrisf.socialq.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class NearbyDeviceData(val name: String, val endpointId: String) : Parcelable