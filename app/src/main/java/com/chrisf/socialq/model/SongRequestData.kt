package com.chrisf.socialq.model

import kaaes.spotify.webapi.android.models.UserPublic

data class SongRequestData (val uri: String, val user: UserPublic)