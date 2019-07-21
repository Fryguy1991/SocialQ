package com.chrisf.socialq.model.spotify.pager

import android.os.Parcelable
import com.chrisf.socialq.model.spotify.AlbumSimple
import kotlinx.android.parcel.Parcelize

@Parcelize
class AlbumSimplePager(
        val albums: Pager<AlbumSimple>
): Parcelable