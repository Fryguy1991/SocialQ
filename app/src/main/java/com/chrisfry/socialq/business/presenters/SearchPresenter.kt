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
import java.util.*
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
    private var fullArtistAlbums = mutableListOf<Album>()

    private var navStep = BASE
    private var cachedArtistPosition = 0;
    private var cachedAlbumPosition = 0
    private var cachedSearchTerm = ""
    private var selectedArtist: Artist? = null

    // Flags for detecting if search/retrieval is complete
    private var songSearchComplete = false
    private var artistSearchComplete = false
    private var albumSearchComplete = false
    private var artistFullAlbumRetrievalComplete = false
    private var artistTopTrackRetrievalComplete = false

    private var cachedAlbumCount = 0

    // Flags for detecting nav state
    private var artistSelectedFlag = false
    private var allArtistFlag = false
    private var allAlbumFlag = false
    private var allArtistAlbumFlag = false

    // Options to set limit for search results to 50 items
    val options = HashMap<String, Any>()

    init {
        options[SpotifyService.LIMIT] = AppConstants.SPOTIFY_SEARCH_LIMIT
        options[SpotifyService.OFFSET] = 0
    }

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
            ALL_SONGS -> {
                Log.d(TAG, "Returning to base view from view all songs")
                navStep = BASE

                getView()!!.showBaseResultsView()
            }
            ARTIST -> {
                selectedArtist = null
                artistSelectedFlag = false

                if (allArtistFlag) {
                    Log.d(TAG, "Returning to all artists from artist")
                    navStep = ALL_ARTISTS
                    allArtistFlag = false
                    getView()!!.showAllArtists(baseArtistResults, cachedArtistPosition)
                    cachedArtistPosition = 0
                } else {
                    Log.d(TAG, "Returning to base from artist")
                    navStep = BASE
                    getView()!!.showBaseResultsView()
                    getView()!!.showBaseSongResults(baseSongResults)
                    getView()!!.showBaseArtistResults(baseArtistResults)
                    getView()!!.showBaseAlbumResults(baseAlbumResults)
                }
            }
            ALL_ARTISTS -> {
                Log.d(TAG, "Returning to base view from view all artists")
                navStep = BASE
                allArtistFlag = false
                getView()!!.showBaseResultsView()
                getView()!!.showBaseSongResults(baseSongResults)
                getView()!!.showBaseArtistResults(baseArtistResults)
                getView()!!.showBaseAlbumResults(baseAlbumResults)
            }
            ALBUM -> {
                when {
                    allArtistAlbumFlag -> {
                        Log.d(TAG, "Returning to all artist albums from album")
                        navStep = ALL_ALBUMS
                        getView()!!.showAllArtistAlbums(selectedArtist!!, fullArtistAlbums, cachedAlbumPosition)
                        cachedAlbumPosition = 0
                    }
                    allAlbumFlag -> {
                        Log.d(TAG, "Returning to all albums from album")
                        navStep = ALL_ALBUMS
                        getView()!!.showAllAlbums(baseAlbumResults, cachedAlbumPosition)
                        cachedAlbumPosition = 0
                    }
                    artistSelectedFlag -> {
                        Log.d(TAG, "Returning to artist from album")
                        navStep = ARTIST
                        getView()!!.returnToArtist(selectedArtist!!)
                    }
                    else -> {
                        Log.d(TAG, "Returning to base from album")
                        navStep = BASE
                        getView()!!.showBaseResultsView()
                    }
                }
            }
            ALL_ALBUMS -> {
                if (allArtistAlbumFlag) {
                    Log.d(TAG, "Returning to artist from artist albums")
                    navStep = ARTIST
                    allArtistAlbumFlag = false
                    getView()!!.returnToArtist(selectedArtist!!)
                } else {
                    Log.d(TAG, "Returning to base from all albums")
                    navStep = BASE
                    allAlbumFlag = false
                    getView()!!.showBaseResultsView()
                }
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
        navStep = ALL_SONGS
        getView()!!.showAllSongs(baseSongResults)
    }

    override fun viewAllArtistsRequest() {
        navStep = ALL_ARTISTS
        allArtistFlag = true
        getView()!!.showAllArtists(baseArtistResults, 0)
    }

    override fun viewAllAlbumsRequest() {
        navStep = ALL_ALBUMS
        allAlbumFlag = true
        getView()!!.showAllAlbums(baseAlbumResults, 0)
    }

    override fun viewALlArtistAlbumsRequest() {
        navStep = ALL_ALBUMS
        allArtistAlbumFlag = true
        getView()!!.showAllArtistAlbums(selectedArtist!!, fullArtistAlbums, 0)
    }

    // START SPOTIFY CALLBACK ITEMS
    private val songsCallback = object : SpotifyCallback<TracksPager>() {
        override fun success(tracksPager: TracksPager?, response: Response?) {
            if (tracksPager != null && response != null) {
                if (doSearchTermsMatch(response, AppConstants.URL_TRACK_SEARCH)) {
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
                Log.e(TAG, spotifyError.errorDetails.message)
            }
            Log.e(TAG, "Error searching for songs")
        }
    }

    private val artistsCallback = object : SpotifyCallback<ArtistsPager>() {
        override fun success(artistsPager: ArtistsPager?, response: Response?) {
            if (artistsPager != null && response != null) {
                if (doSearchTermsMatch(response, AppConstants.URL_ARTIST_SEARCH)) {
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
                Log.e(TAG, spotifyError.errorDetails.message)
            }
            Log.e(TAG, "Error searching for artists")
        }
    }

    private val albumsCallback = object : SpotifyCallback<AlbumsPager>() {
        override fun success(albumsPager: AlbumsPager?, response: Response?) {
            if (albumsPager != null && response != null) {
                if (doSearchTermsMatch(response, AppConstants.URL_ALBUM_SEARCH)) {
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
                Log.e(TAG, spotifyError.errorDetails.message)
            }
            Log.e(TAG, "Error searching for albums")
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
                Log.e(TAG, spotifyError.errorDetails.message)
            }
            Log.e(TAG, "Error retrieving full albums")
        }
    }

    private val artistAlbumCallback = object : SpotifyCallback<Pager<Album>>() {
        override fun success(albumsPager: Pager<Album>?, response: Response?) {
            if (albumsPager != null) {
                if (albumsPager.offset == 0) {
                    // Fresh retrieval
                    artistAlbums.clear()
                    fullArtistAlbums.clear()
                    cachedAlbumCount = albumsPager.total
                }

                artistAlbums.addAll(albumsPager.items)

                if (albumsPager.total > artistAlbums.size) {
                    Log.d(TAG, "Retrieving more albums by ${selectedArtist!!.name}")
                    options[SpotifyService.OFFSET] = albumsPager.offset + albumsPager.items.size
                    spotifyService.getArtistAlbums(selectedArtist!!.id, options, this)
                } else {
                    Log.d(TAG, "Finished retrieving ${selectedArtist!!.name} albums")

                    retrieveFullAlbums(artistAlbums)

                    options[SpotifyService.OFFSET] = 0
                    options.remove("include_groups")
                }
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            if (spotifyError != null) {
                Log.e(TAG, spotifyError.errorDetails.message)
            }
            Log.e(TAG, "Error retrieving artist's albums")
            options[SpotifyService.OFFSET] = 0
        }
    }

    private val artistFullAlbumCallback = object : SpotifyCallback<Albums>() {
        override fun success(albums: Albums?, response: Response?) {
            if (albums != null) {
                fullArtistAlbums.addAll(albums.albums)

                if (fullArtistAlbums.size == cachedAlbumCount) {
                    Log.d(TAG, "Finished retrieving full albums for ${selectedArtist!!.name}")
                    if (artistTopTrackRetrievalComplete) {
                        getView()!!.showArtist(selectedArtist!!, artistTopTracks, fullArtistAlbums)
                    }
                } else {
                    Log.d(TAG, "Successfully retrieved some full albums for ${selectedArtist!!.name}")
                }
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            if (spotifyError != null) {
                Log.e(TAG, spotifyError.errorDetails.message)
            }
            Log.e(TAG, "Error retrieving full artist albums")
        }
    }

    private val artistTopTracksCallback = object : SpotifyCallback<Tracks>() {
        override fun success(tracks: Tracks?, response: Response?) {
            if (tracks != null) {
                Log.d(TAG, "Finished retrieving ${selectedArtist!!.name} top tracks")
                artistTopTrackRetrievalComplete = true
                artistTopTracks.clear()
                artistTopTracks = tracks.tracks

                if (artistFullAlbumRetrievalComplete) {
                    getView()!!.showArtist(selectedArtist!!, artistTopTracks, artistAlbums)
                }
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            if (spotifyError != null) {
                Log.e(TAG, spotifyError.errorDetails.message)
            }
            Log.e(TAG, "Error retrieving artist's top tracks")
        }
    }
    // END SPOTIFY CALLBACK ITEMS

    private fun retrieveFullAlbums(albums: List<Album>) {
        var i = 0
        var albumSearchString = ""
        while (i < albums.size) {

            albumSearchString += albums[i].id

            // Can only load 20 albums at a time (or size of list if it's smaller)
            if (i % 20 != 19 && i < albums.size - 1) {
                albumSearchString += ","
            }

            i++

            // If we're loaded up with 20 albums or at end of list send full album call
            if (i % 20 == 0 || i >= albums.size) {
                spotifyService.getAlbums(albumSearchString, artistFullAlbumCallback)
                albumSearchString = ""
            }
        }
    }

    private fun clearSearchResults() {
        baseSongResults.clear()
        baseArtistResults.clear()
        baseAlbumResults.clear()
    }

    private fun clearArtistResults() {
        artistAlbums.clear()
        fullArtistAlbums.clear()
        artistTopTracks.clear()
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
            ALL_SONGS,
            ARTIST,
            ALBUM -> {
                getView()!!.sendTrackToHost(uri)
            }
            ALL_ARTISTS,
            ALL_ALBUMS -> {
                Log.e(TAG, "Not expecting track to be selected here")
            }
        }
    }

    private fun handleArtistSelected(uri: String, position: Int) {
        when (navStep) {
            BASE -> {
                // Selected one of the first shown artists. Position is worthless.
                // Look in results list (currently only showing first 3 items)
                var subListLimit = baseArtistResults.size
                if (baseArtistResults.size >= 3) {
                    subListLimit = 3
                }
                for (artist in baseArtistResults.subList(0, subListLimit)) {
                    if (uri == artist.uri) {
                        Log.d(TAG, "Selected ${artist.name}")
                        navStep = ARTIST
                        artistSelectedFlag = true

                        // Retrieve artist's albums and top tracks (callbacks responsible for notifying view)
                        artistTopTrackRetrievalComplete = false
                        artistFullAlbumRetrievalComplete = false
                        selectedArtist = artist
                        options["include_groups"] = "album,single"
                        spotifyService.getArtistAlbums(artist.id, options, artistAlbumCallback)
                        spotifyService.getArtistTopTrack(artist.id, Locale.getDefault().country, artistTopTracksCallback)
                        return
                    }
                }
                Log.e(TAG, "Something went wrong. Lost artist information")
            }
            ALL_ARTISTS -> {
                // Selected an artist from view all list. Ensure position is valid and use for direct retrieval
                if (position >= 0 && position < baseArtistResults.size) {
                    Log.d(TAG, "Selected ${baseArtistResults[position].name} from all artists")
                    navStep = ARTIST
                    artistSelectedFlag = true
                    cachedArtistPosition = position
                    selectedArtist = baseArtistResults[position]

                    // Retrieve artist's albums and top tracks (callbacks responsible for notifying view)
                    artistTopTrackRetrievalComplete = false
                    artistFullAlbumRetrievalComplete = false
                    spotifyService.getArtistAlbums(baseArtistResults[position].id, options, artistAlbumCallback)
                    spotifyService.getArtistTopTrack(baseArtistResults[position].id, Locale.getDefault().country, artistTopTracksCallback)
                } else {
                    Log.e(TAG, "Invalid artist index")
                }
            }
            ALL_SONGS,
            ARTIST,
            ALBUM,
            ALL_ALBUMS -> {
                Log.e(TAG, "Not expecting artist to be selected here")
            }
        }
    }

    private fun handleAlbumSelected(uri: String, position: Int) {
        when (navStep) {
            BASE -> {
                // Selected one of the first shown albums. Position is worthless.
                // Look in results list (currently only showing first 3 items)
                var subListLimit = baseAlbumResults.size
                if (baseAlbumResults.size >= 3) {
                    subListLimit = 3
                }
                for (album in baseAlbumResults.subList(0, subListLimit)) {
                    if (uri == album.uri) {
                        Log.d(TAG, "Selected ${album.name}")
                        navStep = ALBUM
                        getView()!!.showAlbum(album)
                        return
                    }
                }
                Log.e(TAG, "Something went wrong. Lost album information")
            }
            ALL_ALBUMS -> {
                // Selected an album from view all list. Ensure position is valid and use for direct retrieval
                when {
                    artistSelectedFlag -> {
                        if (position >= 0 && position < fullArtistAlbums.size) {
                            Log.d(TAG, "Selected ${fullArtistAlbums[position].name} from artist albums")
                            navStep = ALBUM
                            cachedAlbumPosition = position
                            getView()!!.showAlbum(fullArtistAlbums[position])
                        } else {
                            Log.e(TAG, "Invalid album index")
                        }
                    }
                    allAlbumFlag -> {
                        if (position >= 0 && position < baseAlbumResults.size) {
                            Log.d(TAG, "Selected ${baseAlbumResults[position].name} from all albums")
                            navStep = ALBUM
                            cachedAlbumPosition = position
                            getView()!!.showAlbum(baseAlbumResults[position])
                        } else {
                            Log.e(TAG, "Invalid album index")
                        }
                    }
                    else -> {
                        Log.e(TAG, "Not expecting album selection for this case")
                    }
                }
            }
            ARTIST -> {
                // Selected an album from artist view (base artist) Position is worthless.
                // Look in artist album results list (currently only showing first 4 items)
                var subListLimit = fullArtistAlbums.size
                if (fullArtistAlbums.size >= 4) {
                    subListLimit = 4
                }
                for (album in fullArtistAlbums.subList(0, subListLimit)) {
                    if (uri == album.uri) {
                        Log.d(TAG, "Selected ${album.name} from artist")
                        navStep = ALBUM
                        getView()!!.showAlbum(album)
                        return
                    }
                }
                Log.e(TAG, "Something went wrong. Lost album information")
            }
            ALL_SONGS,
            ALL_ARTISTS,
            ALBUM -> {
                Log.e(TAG, "Not expecting album to be selected here")
            }
        }
    }
}