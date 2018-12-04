package com.chrisfry.socialq.business.presenters

interface ISearchPresenter : ISpotifyAccessPresenter {
    fun searchByText(searchTerm: String)

    fun backOrUpNavigation()

    fun itemSelected(uri: String)
    fun itemSelected(uri: String, position: Int)

    fun viewAllSongsRequest()

    fun viewAllArtistsRequest()

    fun viewAllAlbumsRequest()

    fun viewALlArtistAlbumsRequest()
}