package com.chrisf.socialq.model.spotify.pager

import android.os.Parcelable
import com.chrisf.socialq.model.spotify.Track
import kotlinx.android.parcel.Parcelize

@Parcelize
class TrackPager(
        val tracks: Pager<Track>
): Parcelable