package com.chrisf.socialq.model.spotify

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class SnapshotId (
        val snapshot_id: String
): Parcelable