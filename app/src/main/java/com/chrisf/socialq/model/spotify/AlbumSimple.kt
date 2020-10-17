package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class AlbumSimple(
        val album_group: String?,
        val album_type: String,
        val artists: List<ArtistSimple>,
        val available_markets: List<String>,
        // val external_urls: Map<String, String>,
        // val href: String,
        val id: String,
        val images: List<Image>,
        val name: String,
        val release_date: String,
        // val release_date_precision: String,
        // restrictions object
        val type: String,
        val uri: String
) : Parcelable