package com.chrisf.socialq.model.spotify.pager

import android.os.Parcelable
import kaaes.spotify.webapi.android.models.Track
import kotlinx.android.parcel.Parcelize

@Parcelize
class TrackPager(
        val tracks: Pager<Track>
): Parcelable