package com.chrisfry.socialq.userinterface.interfaces

import kaaes.spotify.webapi.android.models.Album
import kaaes.spotify.webapi.android.models.Artist
import kaaes.spotify.webapi.android.models.Track

interface ISearchView : ISpotifyAccessView{

    fun showBaseResultsView()

    fun showBaseSongResults(songList: List<Track>)

    fun showBaseArtistResults(artistList: List<Artist>)

    fun showBaseAlbumResults(albumList: List<Album>)

    fun showNoResultsView(searchTerm: String)

    fun showEmptyBaseView()

    fun showAllSongs(songList: List<Track>)

    fun showAllArtists(artistList: List<Artist>, position: Int)

    fun showAllAlbums(albumList: List<Album>, position: Int)
    fun showAllArtistAlbums(artist: Artist, albumList: List<Album>, position: Int)

    fun showAlbum(album: Album)

    fun showArtist(artist: Artist, topTracks: List<Track>, albums: List<Album>)
    fun returnToArtist(artist: Artist)

    fun sendTrackToHost(uri: String)

    fun closeSearchView()
}