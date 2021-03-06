package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class UserPrivate(
        val country: String,
        val display_name: String?,
        // val email: String
        // external URLs
        // followers
        // href
        val id: String,
        val images: List<Image>,
        val product: String,
        val type: String,
        val uri: String
) : Parcelable