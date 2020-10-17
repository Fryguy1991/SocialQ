package com.chrisf.socialq.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

@Parcelize
data class AuthCodeServerResponse(@SerializedName("body") val authTokens: AuthTokens) : Parcelable

@Parcelize
data class AuthTokens(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String
) : Parcelable

@Parcelize
data class AuthRefreshTokenServerResponse(@SerializedName("body") val accessToken: AccessToken) : Parcelable

@Parcelize
data class AccessToken(
    @SerializedName("access_token") val accessToken: String
) : Parcelable
