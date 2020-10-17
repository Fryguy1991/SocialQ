package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import com.chrisf.socialq.model.spotify.pager.Pager
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Album (
        val album_type: String,
        val artists: List<ArtistSimple>,
        val available_markets: List<String>,
        // copyrights
        // external IDs
        // val external_urls: Map<String, String>,
        // genres
        // val href: String,
        val id: String,
        val images: List<Image>,
        val name: String,
        // popularity
        // val release_date: String,
        // val release_date_precision: String,
        // restrictions object
        val tracks: Pager<Track>,
        val type: String,
        val uri: String
) : Parcelable