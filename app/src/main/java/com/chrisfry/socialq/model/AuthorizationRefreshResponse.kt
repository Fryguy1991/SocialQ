package com.chrisfry.socialq.model

import android.os.Parcel
import android.os.Parcelable

class AuthorizationRefreshResponse() : Parcelable {

    lateinit var access_token : String
    lateinit var token_type : String
    lateinit var scope : String
    lateinit var expires_in : Number
    lateinit var refresh_token : String

    constructor(parcel: Parcel) : this() {
        access_token = parcel.readString()
        token_type = parcel.readString()
        scope = parcel.readString()
        expires_in = parcel.readInt()
        refresh_token = parcel.readString()
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        if (dest != null) {
            dest.writeValue(this.access_token)
            dest.writeValue(this.token_type)
            dest.writeValue(this.scope)
            dest.writeValue(this.expires_in)
            dest.writeValue(this.refresh_token)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AuthorizationRefreshResponse> {
        override fun createFromParcel(parcel: Parcel): AuthorizationRefreshResponse {
            return AuthorizationRefreshResponse(parcel)
        }

        override fun newArray(size: Int): Array<AuthorizationRefreshResponse?> {
            return arrayOfNulls(size)
        }
    }

}