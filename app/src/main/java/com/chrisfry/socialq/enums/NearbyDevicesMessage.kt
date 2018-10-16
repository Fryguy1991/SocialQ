package com.chrisfry.socialq.enums

import com.chrisfry.socialq.business.AppConstants

enum class NearbyDevicesMessage (val payloadPrefix : String) {
    QUEUE_UPDATE(AppConstants.UPDATE_QUEUE_MESSAGE),
    SONG_ADDED(AppConstants.SONG_ADDED_MESSAGE),
    RECEIVE_PLAYLIST_ID(AppConstants.PLAYLIST_ID_MESSAGE),
    RECEIVE_HOST_USER_ID(AppConstants.HOST_USER_ID_MESSAGE),
    INVALID(AppConstants.INVALID)
}