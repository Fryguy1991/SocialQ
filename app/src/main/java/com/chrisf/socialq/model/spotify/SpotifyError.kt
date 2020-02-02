package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

@Parcelize
data class SpotifyError(
        @SerializedName("error") val errorName: String,
        @SerializedName("error_description") val errorDescription: String
): Parcelable