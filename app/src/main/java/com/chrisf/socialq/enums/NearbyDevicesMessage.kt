package com.chrisf.socialq.enums

import com.chrisf.socialq.AppConstants

enum class NearbyDevicesMessage (val regex: String, val messageFormat: String) {
    CURRENTLY_PLAYING_UPDATE(AppConstants.CURRENTLY_PLAYING_UPDATE_REGEX, AppConstants.CURRENTLY_PLAYING_UPDATE_MESSAGE_FORMAT),
    SONG_REQUEST(AppConstants.FULL_SONG_REQUEST_REGEX, AppConstants.SONG_REQUEST_MESSAGE_FORMAT),
    INITIATE_CLIENT(AppConstants.INITIATE_CLIENT_REGEX, AppConstants.INITIATE_CLIENT_MESSAGE_FORMAT),
    HOST_DISCONNECTING(AppConstants.HOST_DISCONNECT_MESSAGE, AppConstants.HOST_DISCONNECT_MESSAGE),
    NEW_TRACK_ADDED(AppConstants.NEW_SONG_ADDED_REGEX, AppConstants.NEW_SONG_ADDED_MESSAGE_FORMAT),
    INVALID(AppConstants.INVALID, AppConstants.INVALID)
}