package com.chrisf.socialq.model

import kaaes.spotify.webapi.android.models.PlaylistTrack
import kaaes.spotify.webapi.android.models.UserPublic

class ClientRequestData(val track: PlaylistTrack, val user: UserPublic)