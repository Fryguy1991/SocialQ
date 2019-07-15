package com.chrisf.socialq.model

import android.os.Parcelable
import kaaes.spotify.webapi.android.models.PlaylistTrack
import kaaes.spotify.webapi.android.models.UserPublic
import kotlinx.android.parcel.Parcelize

@Parcelize
data class ClientRequestData(val track: PlaylistTrack, val user: UserPublic) : Parcelable