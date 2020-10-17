package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class PlaylistTracksInfo (
        // href
        val total: Int
) : Parcelable