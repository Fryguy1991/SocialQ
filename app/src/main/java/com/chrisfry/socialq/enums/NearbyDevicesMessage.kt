package com.chrisfry.socialq.enums

import com.chrisfry.socialq.business.AppConstants

enum class NearbyDevicesMessage (val regex: String, val messageFormat: String) {
    QUEUE_UPDATE(AppConstants.UPDATE_QUEUE_REGEX, AppConstants.QUEUE_UPDATE_MESSAGE_FORMAT),
    SONG_REQUEST(AppConstants.FULL_SONG_REQUEST_REGEX, AppConstants.SONG_REQUEST_MESSAGE_FORMAT),
    INITIATE_CLIENT(AppConstants.INITIATE_CLIENT_REGEX, AppConstants.INITIATE_CLIENT_MESSAGE_FORMAT),
    INVALID(AppConstants.INVALID, AppConstants.INVALID)
}