package com.chrisf.socialq.model

import android.os.Parcelable
import com.chrisf.socialq.model.spotify.UserPublic
import kotlinx.android.parcel.Parcelize

@Parcelize
data class SongRequestData(val uri: String, val user: UserPublic) : Parcelable