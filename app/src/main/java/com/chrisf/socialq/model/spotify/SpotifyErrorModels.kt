package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class ErrorBody(
        val status: Int,
        val message: String
): Parcelable


@Parcelize
data class SpotifyError(val error: ErrorBody): Parcelable
