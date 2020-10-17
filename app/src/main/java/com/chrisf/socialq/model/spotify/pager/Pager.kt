package com.chrisf.socialq.model.spotify.pager

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Pager<Type : Parcelable>(
        val href: String,
        val items: List<Type>,
        val limit: Int,
        val next: String?,
        val offset: Int,
        val previous: String?,
        val total: Int
) : Parcelable