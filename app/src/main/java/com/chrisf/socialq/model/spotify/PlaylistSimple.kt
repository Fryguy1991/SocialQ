package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class PlaylistSimple(
        val collaborative: Boolean,
        // external URLs
        // href
        val id: String,
        val images: List<Image>,
        val name: String,
        val owner: UserPublic,
        val public: Boolean?,
        // snapshot ID
        val tracks: PlaylistTracksInfo,
        val type: String,
        val uri: String
) : Parcelable