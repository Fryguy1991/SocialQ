package com.chrisf.socialq.processor

import androidx.lifecycle.Lifecycle
import com.chrisf.socialq.extensions.addTo
import com.chrisf.socialq.model.spotify.*
import com.chrisf.socialq.model.spotify.pager.AlbumSimplePager
import com.chrisf.socialq.model.spotify.pager.ArtistPager
import com.chrisf.socialq.model.spotify.pager.Pager
import com.chrisf.socialq.model.spotify.pager.TrackPager
import com.chrisf.socialq.network.SpotifyApi
import com.chrisf.socialq.processor.SearchProcessor.SearchAction
import com.chrisf.socialq.processor.SearchProcessor.SearchAction.*
import com.chrisf.socialq.processor.SearchProcessor.SearchState
import com.chrisf.socialq.processor.SearchProcessor.SearchState.*
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Function3
import io.reactivex.schedulers.Schedulers
import retrofit2.Response
import javax.inject.Inject

class SearchProcessor @Inject constructor(
        private val spotifyService: SpotifyApi,
        lifecycle: Lifecycle,
        subscriptions: CompositeDisposable
) : BaseProcessor<SearchState, SearchAction>(lifecycle, subscriptions) {

    override fun handleAction(action: SearchAction) {
        when (action) {
            ViewCreated -> stateStream.accept(DisplayBaseView)
            is SearchTermModified -> searchForMusic(action.term)
            is TrackSelected -> stateStream.accept(ReportTrackResult(action.id))
            is ArtistSelected -> retrieveArtistDetails(action.id)
            is AlbumSelected -> retrieveAlbumDetails(action.id)
            is ViewAllTracksSelected -> stateStream.accept(NavigateToAllTracks(action.initialTrackList))
            is ViewAllAlbumsSelected -> stateStream.accept(NavigateToAllAlbums(action.initialAlbumList))
            is ViewAllArtistsSelected -> stateStream.accept(NavigateToAllArtists(action.initialArtistList))
            is ViewArtistAlbumsSelected -> retrieveArtistAlbums(action)
        }
    }

    private fun retrieveAlbumDetails(albumId: String) {
        spotifyService.getFullAlbum(albumId)
                .subscribeOn(Schedulers.io())
                .subscribe { response -> stateStream.accept(DisplayAlbum(response.body()!!)) }
                .addTo(subscriptions)
    }

    private fun retrieveArtistDetails(artistId: String) {

        Single.zip(
                spotifyService.getArtist(artistId),
                spotifyService.getArtistAlbums(artistId, 5),
                spotifyService.getArtistTopTracks(artistId),
                Function3<
                        Response<Artist>,
                        Response<Pager<AlbumSimple>>,
                        Response<TracksObject>,
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
                .subscribe { state -> stateStream.accept(state) }
                .addTo(subscriptions)
    }

    private fun retrieveArtistAlbums(state: ViewArtistAlbumsSelected) {
        spotifyService.getArtistAlbums(state.artist.id)
                .subscribeOn(Schedulers.io())
                .subscribe { response ->
                    // TODO: Ensure our response is good
                    stateStream.accept(NavigateToArtistAlbums(state.artist, response.body()!!.items))
                }
                .addTo(subscriptions)
    }

    private fun searchForMusic(term: String) {
        if (term.isEmpty()) {
            stateStream.accept(DisplayBaseView)
            return
        }

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
                .doOnSubscribe { subscriptions.add(it) }
                .subscribe { state ->
                    stateStream.accept(state)
                }
                .addTo(subscriptions)
    }

    sealed class SearchState {
        object DisplayBaseView : SearchState()
        data class ReportTrackResult(val trackUri: String) : SearchState()
        data class DisplayNoResults(val searchTerm: String) : SearchState()
        data class DisplayBaseResults(
                val searchTerm: String,
                val trackList: List<Track>,
                val artistList: List<Artist>,
                val albumList: List<AlbumSimple>
        ) : SearchState()

        data class DisplayArtist(
                val artist: Artist,
                val artistTopTracks: List<Track>,
                val artistAlbums: List<AlbumSimple>
        ) : SearchState()

        data class DisplayAlbum(val album: Album): SearchState()
        data class NavigateToAllTracks(val initialTrackList: List<Track>): SearchState()
        data class NavigateToAllAlbums(val initialAlbumList: List<AlbumSimple>): SearchState()
        data class NavigateToAllArtists(val initialArtistList: List<Artist>): SearchState()
        data class NavigateToArtistAlbums(
                val artist: Artist,
                val initialAlbumList: List<AlbumSimple>
        ) : SearchState()
    }

    sealed class SearchAction {
        object ViewCreated: SearchAction()
        object ViewResumed: SearchAction()
        object BackPressed: SearchAction()
        object NavUpPressed: SearchAction()
        data class SearchTermModified(val term: String): SearchAction()
        data class TrackSelected(val id: String): SearchAction()
        data class AlbumSelected(val id: String): SearchAction()
        data class ArtistSelected(val id: String): SearchAction()
        data class ViewAllTracksSelected(val initialTrackList: List<Track>): SearchAction()
        data class ViewArtistAlbumsSelected(val artist: Artist) : SearchAction()
        data class ViewAllArtistsSelected(val initialArtistList: List<Artist>): SearchAction()
        data class ViewAllAlbumsSelected(val initialAlbumList: List<AlbumSimple>): SearchAction()
    }
}