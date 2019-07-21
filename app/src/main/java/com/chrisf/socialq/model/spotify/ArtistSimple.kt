package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class ArtistSimple (
        val external_urls : Map<String, String>,
        val href : String,
        val id : String,
        val name : String,
        val type : String,
        val uri : String
) : Parcelable