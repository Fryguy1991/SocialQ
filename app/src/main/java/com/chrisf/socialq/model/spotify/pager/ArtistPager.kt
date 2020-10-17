package com.chrisf.socialq.model.spotify.pager

import android.os.Parcelable
import com.chrisf.socialq.model.spotify.Artist
import kotlinx.android.parcel.Parcelize

@Parcelize
class ArtistPager(
        val artists: Pager<Artist>
): Parcelable