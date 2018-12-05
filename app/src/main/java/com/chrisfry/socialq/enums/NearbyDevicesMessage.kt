package com.chrisfry.socialq.enums

import com.chrisfry.socialq.business.AppConstants

enum class NearbyDevicesMessage (val regex : String) {
    QUEUE_UPDATE(AppConstants.UPDATE_QUEUE_REGEX),
    SONG_REQUEST(AppConstants.FULL_SONG_REQUEST_REGEX),
    INITIATE_CLIENT(AppConstants.INITIATE_CLIENT_REGEX),
    INVALID(AppConstants.INVALID)
}