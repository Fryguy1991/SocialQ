package com.chrisfry.socialq.business.presenters

interface ISpotifyAccessPresenter : IBasePresenter {
    fun receiveNewAccessToken(accessToken: String)
}