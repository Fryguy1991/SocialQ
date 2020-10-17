package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class Artist (
        // val external_urls : Map<String, String>,
        // followers
        // genres
        // val href : String,
        val id : String,
        val images: List<Image>,
        val name : String,
        // popularity
        val type : String,
        val uri : String
) : Parcelable