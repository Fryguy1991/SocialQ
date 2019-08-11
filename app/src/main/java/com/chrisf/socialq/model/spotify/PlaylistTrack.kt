package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class PlaylistTrack (
      val added_at: String,
      val added_by: UserPublic,
      val is_local: Boolean,
      val track: Track
) : Parcelable