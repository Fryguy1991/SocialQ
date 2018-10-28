package com.chrisfry.socialq.model

import android.os.Parcel
import android.os.Parcelable

class AuthorizationResponse() : Parcelable {

    lateinit var code : String
    lateinit var state : String

    constructor(parcel: Parcel) : this() {
        code = parcel.readString()
        state = parcel.readString()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if (dest != null) {
            dest.writeValue(this.code)
            dest.writeValue(this.state)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AuthorizationResponse> {
        override fun createFromParcel(parcel: Parcel): AuthorizationResponse {
            return AuthorizationResponse(parcel)
        }

        override fun newArray(size: Int): Array<AuthorizationResponse?> {
            return arrayOfNulls(size)
        }
    }
}