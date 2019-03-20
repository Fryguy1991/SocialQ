package com.chrisf.socialq.business.presenters

interface ISearchPresenter : IBasePresenter {
    fun searchByText(searchTerm: String)

    fun backOrUpNavigation()

    fun itemSelected(uri: String)
    fun itemSelected(uri: String, position: Int)

    fun viewAllSongsRequest()

    fun viewAllArtistsRequest()

    fun viewAllAlbumsRequest()

    fun viewAllArtistAlbumsRequest()
}