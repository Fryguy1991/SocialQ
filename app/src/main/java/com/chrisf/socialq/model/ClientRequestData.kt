package com.chrisf.socialq.model

import android.os.Parcelable
import com.chrisf.socialq.model.spotify.UserPublic
import kaaes.spotify.webapi.android.models.PlaylistTrack
import kotlinx.android.parcel.Parcelize

@Parcelize
data class ClientRequestData(val track: PlaylistTrack, val user: UserPublic) : Parcelable