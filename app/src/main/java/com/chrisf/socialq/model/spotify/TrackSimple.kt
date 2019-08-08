package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class TrackSimple(
        val artists: List<ArtistSimple>,
        val available_markets: List<String>,
        // disc_number
        // duration_ms
        val explicit: Boolean,
        // external IDs
        // val external_urls: Map<String, String>,
        // href
        val id: String,
        val is_playable: Boolean,
        // linked_from
        // restrictions
        val name: String,
        // preview_url
        val type: String,
        val uri: String,
        val is_local: Boolean
) : Parcelable