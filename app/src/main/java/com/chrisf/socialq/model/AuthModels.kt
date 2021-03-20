package com.chrisf.socialq.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

@Parcelize
data class AuthTokenRequest(@SerializedName("code") val authCode: String) : Parcelable

@Parcelize
data class AuthRefreshRequest(@SerializedName("refreshToken") val refreshToken: String) : Parcelable

@Parcelize
data class AuthTokens(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String
) : Parcelable

@Parcelize
data class AccessToken(
    @SerializedName("access_token") val accessToken: String
) : Parcelable
