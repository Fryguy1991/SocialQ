package com.chrisfry.socialq.business.presenters

import android.util.Log
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.SearchNavStep.*
import com.chrisfry.socialq.userinterface.interfaces.ISearchView
import kaaes.spotify.webapi.android.SpotifyCallback
import kaaes.spotify.webapi.android.SpotifyError
import kaaes.spotify.webapi.android.SpotifyService
import kaaes.spotify.webapi.android.models.*
import retrofit.client.Response
import java.util.HashMap
import java.util.regex.Pattern

class SearchPresenter : SpotifyAccessPresenter(), ISearchPresenter {
    companion object {
        val TAG = SearchPresenter::class.java.name
    }

    // Lists for base results (songs, artists, albums)
    private var baseSongResults = mutableListOf<Track>()
    private var baseArtistResults = mutableListOf<Artist>()
    private var baseAlbumResults = mutableListOf<Album>()

    // Lists for artists results (top tracks, albums)
    private var artistTopTracks = mutableListOf<Track>()
    private var artistAlbums = mutableListOf<Album>()

    private var navStep = BASE
    private var cachedPosition = 0
    private var cachedSearchTerm = ""

    private var songSearchComplete = false
    private var artistSearchComplete = false
    private var albumSearchComplete = false

    override fun getView(): ISearchView? {
        if (presenterView is ISearchView) {
            return presenterView as ISearchView
        } else {
            Log.e(TAG, "Error, we have the wrong view type")
            return null
        }
    }

    override fun searchByText(searchTerm: String) {
        Log.d(TAG, "Searching for: $searchTerm")

        cachedSearchTerm = searchTerm

        // Reset flags for detecting search complete
        songSearchComplete = false
        artistSearchComplete = false
        albumSearchComplete = false

        if (searchTerm.isEmpty()) {
            clearSearchResults()
            getView()!!.showEmptyBaseView()
        } else {
            // Create options to set limit for search results to 50 items
            val options = HashMap<String, Any>()
            options[SpotifyService.LIMIT] = AppConstants.SPOTIFY_SEARCH_LIMIT

            spotifyService.searchAlbums(searchTerm, options, albumsCallback)
            spotifyService.searchTracks(searchTerm, options, songsCallback)
            spotifyService.searchArtists(searchTerm, options, artistsCallback)
        }
    }

    override fun backOrUpNavigation() {
        when (navStep) {
            BASE -> {
                Log.d(TAG, "Close search view")

                getView()!!.closeSearchView()
            }
            VIEW_ALL_SONGS -> {
                Log.d(TAG, "Returning to base view from view all songs")
                navStep = BASE

                getView()!!.showBaseResultsView()
            }
            ARTIST_SELECTED -> {
                Log.d(TAG, "Returning to base view from artist")
                navStep = BASE

                getView()!!.showBaseResultsView()
            }
            ARTIST_ALBUM_SELECTED -> {
                Log.d(TAG, "Returning to artist from album")
                navStep = ARTIST_SELECTED

                // TODO: Return to artist
            }
            VIEW_ALL_ARTISTS -> {
                Log.d(TAG, "Returning to base view from view all artists")
                navStep = BASE

                getView()!!.showBaseResultsView()
            }
            VIEW_ALL_ARTIST_SELECTED -> {
                Log.d(TAG, "Returning to view all artists")
                navStep = VIEW_ALL_ARTISTS

                getView()!!.showAllArtists(baseArtistResults, cachedPosition)
                cachedPosition = 0
            }
            VIEW_ALL_ARTIST_ALBUM_SELECTED -> {
                Log.d(TAG, "Returning to artist (all) from album")
                navStep = VIEW_ALL_ARTIST_SELECTED
            }
            ALBUM_SELECTED -> {
                Log.d(TAG, "Returning to base view from album")
                navStep = BASE

                getView()!!.showBaseResultsView()
            }
            VIEW_ALL_ALBUMS -> {
                Log.d(TAG, "Returning to base view from all albums")
                navStep = BASE

                getView()!!.showBaseResultsView()
            }
            VIEW_ALL_ALBUM_SELECTED -> {
                Log.d(TAG, "Returning to view all albums")
                navStep = VIEW_ALL_ALBUMS

                getView()!!.showAllAlbums(baseAlbumResults, cachedPosition)
                cachedPosition = 0
            }
        }
    }

    override fun itemSelected(uri: String) {
        itemSelected(uri, -1)
    }

    override fun itemSelected(uri: String, position: Int) {
        when {
            uri.startsWith(AppConstants.SPOTIFY_TRACK_PREFIX) -> handleTrackSelected(uri, position)
            uri.startsWith(AppConstants.SPOTIFY_ARTIST_PREFIX) -> handleArtistSelected(uri, position)
            uri.startsWith(AppConstants.SPOTIFY_ALBUM_PREFIX) -> handleAlbumSelected(uri, position)
            else -> {
                Log.e(TAG, "Unexpected URI received")
            }
        }
    }

    override fun viewAllSongsRequest() {
        navStep = VIEW_ALL_SONGS
        getView()!!.showAllSongs(baseSongResults)
    }

    override fun viewAllArtistsRequest() {
        navStep = VIEW_ALL_ARTISTS
        getView()!!.showAllArtists(baseArtistResults, 0)
    }

    override fun viewAllAlbumsRequest() {
        navStep = VIEW_ALL_ALBUMS
        getView()!!.showAllAlbums(baseAlbumResults, 0)
    }

    private val songsCallback = object : SpotifyCallback<TracksPager>() {
        override fun success(tracksPager: TracksPager?, response: Response?) {
            if (tracksPager != null && response != null) {
                if (doSearchTermsMatch(response, AppConstants.URL_FULL_TRACK_SEARCH)) {
                    Log.d(TAG, "Track search terms match")

                    songSearchComplete = true
                    baseSongResults = tracksPager.tracks.items

                    if (!checkIfNoResults()) {
                        getView()!!.showBaseSongResults(baseSongResults)
                    }
                }
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            if (spotifyError != null) {
                Log.e(TAG, "Error searching for songs: " + spotifyError.errorDetails)
            }
        }
    }

    private val artistsCallback = object : SpotifyCallback<ArtistsPager>() {
        override fun success(artistsPager: ArtistsPager?, response: Response?) {
            if (artistsPager != null && response != null) {
                if (doSearchTermsMatch(response, AppConstants.URL_FULL_ARTIST_SEARCH)) {
                    Log.d(TAG, "Artist search terms match")

                    artistSearchComplete = true
                    baseArtistResults = artistsPager.artists.items

                    if (!checkIfNoResults()) {
                        getView()!!.showBaseArtistResults(baseArtistResults)
                    }
                }
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            if (spotifyError != null) {
                Log.e(TAG, "Error searching for artists: " + spotifyError.errorDetails)
            }
        }
    }

    private val albumsCallback = object : SpotifyCallback<AlbumsPager>() {
        override fun success(albumsPager: AlbumsPager?, response: Response?) {
            if (albumsPager != null && response != null) {
                if (doSearchTermsMatch(response, AppConstants.URL_FULL_ALBUM_SEARCH)) {
                    Log.d(TAG, "Album search terms match")

                    albumSearchComplete = true

                    if (albumsPager.albums.items.size > 0) {
                        var albumSearchString = ""
                        var i = 0

                        while (i < 20 && i < albumsPager.albums.items.size) {
                            albumSearchString += albumsPager.albums.items[i].id

                            if (i != 19 && i < albumsPager.albums.items.size - 1) {
                                albumSearchString += ","
                            }
                            i++
                        }

                        spotifyService.getAlbums(albumSearchString, fullAlbumsCallback)

                    } else {
                        baseAlbumResults.clear()
                        if (!checkIfNoResults()) {
                            getView()!!.showBaseAlbumResults(baseAlbumResults)
                        }
                    }
                }
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            if (spotifyError != null) {
                Log.e(TAG, "Error searching for albums: " + spotifyError.errorDetails)
            }
        }
    }

    private val fullAlbumsCallback = object : SpotifyCallback<Albums>() {
        override fun success(albums: Albums?, response: Response?) {
            if (albums != null) {
                Log.d(TAG, "Successfully retrieved full albums")

                baseAlbumResults = albums.albums

                if (!checkIfNoResults()) {
                    getView()!!.showBaseAlbumResults(baseAlbumResults)
                }
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            if (spotifyError != null) {
                Log.e(TAG, "Error retrieveing full albums: " + spotifyError.errorDetails)
            }
        }
    }

    private fun clearSearchResults() {
        baseSongResults.clear()
        baseArtistResults.clear()
        baseAlbumResults.clear()
    }


    private fun doSearchTermsMatch(response: Response, regex: String): Boolean {
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(response.url)

        if (matcher.find()) {
            val searchTerm = matcher.group(1)
            // URL search term replaces spaces with '+'
            return searchTerm == cachedSearchTerm.replace(' ', '+')
        }
        return false
    }

    private fun checkIfNoResults(): Boolean {
        if (songSearchComplete && artistSearchComplete && albumSearchComplete) {
            if (baseSongResults.isEmpty() && baseArtistResults.isEmpty() && baseAlbumResults.isEmpty()) {
                getView()!!.showNoResultsView(cachedSearchTerm)
                return true
            }
        }
        return false
    }

    private fun handleTrackSelected(uri: String, position: Int) {
        when (navStep) {
            BASE,
            VIEW_ALL_SONGS,
            ARTIST_SELECTED,
            ARTIST_ALBUM_SELECTED,
            VIEW_ALL_ARTIST_SELECTED,
            VIEW_ALL_ARTIST_ALBUM_SELECTED,
            ALBUM_SELECTED,
            VIEW_ALL_ALBUM_SELECTED -> {
                getView()!!.sendTrackToHost(uri)
            }
            VIEW_ALL_ARTISTS,
            VIEW_ALL_ALBUMS -> {
                Log.e(TAG, "Not expecting track to be selected here")
            }
        }
    }

    private fun handleArtistSelected(uri: String, position: Int) {
        when (navStep) {
            BASE -> {
                // Selected one of the first shown artists. Position is worthless.
                // Look in results list (currently only showing first 3 items)
                for (artist in baseArtistResults.subList(0, 4)) {
                    if (uri == artist.uri) {
                        navStep = ARTIST_SELECTED
                        getView()!!.showArtist(artist)
                        return
                    }
                }
                Log.e(TAG, "Something went wrong. Lost artist information")
            }
            VIEW_ALL_ARTISTS -> {
                // Selected an artist from view all list. Ensure position is valid and use for direct retrieval
                if (position >= 0 && position < baseArtistResults.size) {
                    navStep = VIEW_ALL_ARTIST_SELECTED
                    cachedPosition = position
                    getView()!!.showArtist(baseArtistResults[position])
                } else {
                    Log.e(TAG, "Invalid artist index")
                }
            }
            VIEW_ALL_SONGS,
            ARTIST_SELECTED,
            ARTIST_ALBUM_SELECTED,
            VIEW_ALL_ARTIST_SELECTED,
            VIEW_ALL_ARTIST_ALBUM_SELECTED,
            ALBUM_SELECTED,
            VIEW_ALL_ALBUMS,
            VIEW_ALL_ALBUM_SELECTED -> {
                Log.e(TAG, "Not expecting artist to be selected here")
            }
        }
    }

    private fun handleAlbumSelected(uri: String, position: Int) {
        when (navStep) {
            BASE -> {
                // Selected one of the first shown albums. Position is worthless.
                // Look in results list (currently only showing first 3 items)
                for (album in baseAlbumResults.subList(0, 4)) {
                    if (uri == album.uri) {
                        navStep = ALBUM_SELECTED
                        getView()!!.showAlbum(album)
                        return
                    }
                }
                Log.e(TAG, "Something went wrong. Lost album information")
            }
            VIEW_ALL_ALBUMS -> {
                // Selected an album from view all list. Ensure position is valid and use for direct retrieval
                if (position >= 0 && position < baseAlbumResults.size) {
                    navStep = VIEW_ALL_ALBUM_SELECTED
                    cachedPosition = position
                    getView()!!.showAlbum(baseAlbumResults[position])
                } else {
                    Log.e(TAG, "Invalid album index")
                }
            }
            ARTIST_SELECTED -> {
                TODO()
            }
            VIEW_ALL_SONGS,
            ARTIST_ALBUM_SELECTED,
            VIEW_ALL_ARTISTS,
            VIEW_ALL_ARTIST_SELECTED,
            VIEW_ALL_ARTIST_ALBUM_SELECTED,
            ALBUM_SELECTED,
            VIEW_ALL_ALBUM_SELECTED -> {
                Log.e(TAG, "Not expecting album to be selected here")
            }
        }
    }
}