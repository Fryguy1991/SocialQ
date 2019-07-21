package com.chrisf.socialq.processor

import androidx.lifecycle.Lifecycle
import com.chrisf.socialq.model.spotify.AlbumSimple
import com.chrisf.socialq.model.spotify.pager.AlbumSimplePager
import com.chrisf.socialq.model.spotify.pager.ArtistPager
import com.chrisf.socialq.model.spotify.pager.TrackPager
import com.chrisf.socialq.network.FrySpotifyService
import com.chrisf.socialq.processor.SearchProcessor.SearchAction
import com.chrisf.socialq.processor.SearchProcessor.SearchAction.*
import com.chrisf.socialq.processor.SearchProcessor.SearchState
import com.chrisf.socialq.processor.SearchProcessor.SearchState.*
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Function3
import io.reactivex.schedulers.Schedulers
import kaaes.spotify.webapi.android.models.*
import retrofit2.Response
import javax.inject.Inject

class SearchProcessor @Inject constructor(
        private val spotifyService: FrySpotifyService,
        lifecycle: Lifecycle,
        subscriptions: CompositeDisposable
): BaseProcessor<SearchState, SearchAction>(lifecycle, subscriptions) {

    private var seachNavStep = SearchNavStep.BASE

    override fun handleAction(action: SearchAction) {
        when (action) {
            ViewCreated -> stateStream.accept(DisplayBaseView)
//            ViewResumed -> pushSearchResultsToView()
            is SearchTermModified -> searchForMusic(action.term)
            is ArtistSelected -> retrieveArtistDetails(action.uri)
        }
    }

    private fun pushSearchResultsToView() {

    }

    private fun retrieveArtistDetails(artistId: String) {
        seachNavStep = SearchNavStep.ARTIST

        @Suppress("CheckResult")
        Single.zip(
                spotifyService.getArtist(artistId),
                spotifyService.getArtistAlbums(artistId),
                spotifyService.getArtistTopTracks(artistId),
                Function3<
                        Response<Artist>,
                        Response<Pager<AlbumSimple>>,
                        Response<Tracks>,
                        SearchState> { artist, albums, topTracks ->
                    // TODO: Ensure our response is good
//                    if (artist.isSuccessful && albums.isSuccessful && topTracks.isSuccessful) {
                        DisplayArtist(
                                artist.body()!!,
                                topTracks.body()!!.tracks,
                                albums.body()!!.items!!
                        )
//                    }
                })
                .subscribeOn(Schedulers.io())
                .doOnSubscribe{ subscriptions.add(it) }
                .subscribe { state ->
                    stateStream.accept(state)
                }
    }

    private fun searchForMusic(term: String) {
        if (term.isEmpty()) {
            stateStream.accept(DisplayBaseView)
            return
        }

        @Suppress("CheckResult")
        Single.zip(
                spotifyService.searchTracks(term),
                spotifyService.searchArtists(term),
                spotifyService.searchAlbums(term),
                Function3<
                        Response<TrackPager>,
                        Response<ArtistPager>,
                        Response<AlbumSimplePager>,
                        SearchState> { trackResponse, artistResponse, albumResponse ->
                    // TODO: Ensure our response is good
                    if (trackResponse.body()!!.tracks.items.isEmpty()
                            && artistResponse.body()!!.artists.items.isEmpty()
                            && albumResponse.body()!!.albums.items.isEmpty()) {
                        DisplayNoResults(term)
                    } else {
                        DisplayBaseResults(
                                term,
                                trackResponse.body()!!.tracks.items,
                                artistResponse.body()!!.artists.items,
                                albumResponse.body()!!.albums.items
                        )
                    }
                })
                .subscribeOn(Schedulers.io())
                .doOnSubscribe{ subscriptions.add(it) }
                .subscribe { state ->
                    stateStream.accept(state)
                }
    }

    private enum class SearchNavStep {
        BASE,
        ALL_TRACKS,
        ALL_ARTIST, ARTIST,
        ALL_ALBUMS, ALBUM
    }

    sealed class SearchState {
        object DisplayBaseView: SearchState()
        data class ReportTrackResult(val trackUri: String): SearchState()
        data class DisplayNoResults(val searchTerm: String): SearchState()
        data class DisplayBaseResults(
                val searchTerm: String,
                val trackList: List<Track>,
                val artistList: List<Artist>,
                val albumList: List<AlbumSimple>
        ): SearchState()
        data class DisplayTracks(val trackList: List<Track>): SearchState()
        data class DisplayArtists(val artistList: List<Artist>): SearchState()
        data class DisplayAlbums(val albumList: List<AlbumSimple>): SearchState()
        data class DisplayArtist(
                val artist: Artist,
                val artistTopTracks: List<Track>,
                val artistAlbums: List<AlbumSimple>
        ): SearchState()
        data class DisplayArtistAlbums(val artistAlbumList: List<AlbumSimple>): SearchState()
        data class DisplayAlbum(val trackList: List<Track>): SearchState()
    }

    sealed class SearchAction {
        object ViewCreated: SearchAction()
        object ViewResumed: SearchAction()
        object BackPressed: SearchAction()
        object NavUpPressed: SearchAction()
        data class SearchTermModified(val term: String): SearchAction()
        data class TrackSelected(val uri: String): SearchAction()
        data class AlbumSelected(val uri: String): SearchAction()
        data class ArtistSelected(val uri: String): SearchAction()
    }
}