package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class Image(
        val height : Int?,
        val url: String,
        val width: Int?
) : Parcelable