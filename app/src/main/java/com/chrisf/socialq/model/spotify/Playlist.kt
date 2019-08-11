package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import com.chrisf.socialq.model.spotify.pager.Pager
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Playlist(
        val collaborative: Boolean,
        val description: String?,
        // external URLs
        // followers
        // href
        val id: String,
        val images: List<Image>,
        val name: String,
        val owner: UserPublic,
        val public: Boolean?,
        // snapshot ID
        val tracks: Pager<PlaylistTrack>,
        val type: String,
        val uri: String
) : Parcelable