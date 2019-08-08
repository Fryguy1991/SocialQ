package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class UserPublic(
        val display_name: String?,
        // external URLs
        // followers
        // href
        val id: String,
        val images: List<Image>,
        val type: String,
        val uri: String
) : Parcelable