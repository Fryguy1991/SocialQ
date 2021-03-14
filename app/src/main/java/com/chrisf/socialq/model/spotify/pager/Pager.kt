package com.chrisf.socialq.model.spotify.pager

import android.os.Parcel
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

data class Pager<Type : Parcelable>(
    val href: String,
    val items: List<Type>,
    val limit: Int,
    val next: String?,
    val offset: Int,
    val previous: String?,
    val total: Int
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString().orEmpty(),
        mutableListOf<Type>().also {
            parcel.readList(it, Pager<Type>::items.javaClass.classLoader)
        },
        parcel.readInt(),
        parcel.readString(),
        parcel.readInt(),
        parcel.readString(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(href)
        parcel.writeTypedList(items)
        parcel.writeInt(limit)
        parcel.writeString(next)
        parcel.writeInt(offset)
        parcel.writeString(previous)
        parcel.writeInt(total)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Pager<Parcelable>> {
        override fun createFromParcel(parcel: Parcel): Pager<Parcelable> {
            return Pager(parcel)
        }

        override fun newArray(size: Int): Array<Pager<Parcelable>?> {
            return arrayOfNulls(size)
        }
    }
}