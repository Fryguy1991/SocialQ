package com.chrisf.socialq.userinterface.fragments

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.FragmentComponent
import com.chrisf.socialq.model.spotify.AlbumSimple
import com.chrisf.socialq.model.spotify.Artist
import com.chrisf.socialq.model.spotify.Track
import com.chrisf.socialq.processor.SearchProcessor
import com.chrisf.socialq.processor.SearchProcessor.SearchAction
import com.chrisf.socialq.processor.SearchProcessor.SearchState
import com.chrisf.socialq.userinterface.activities.TitleActivity
import com.chrisf.socialq.userinterface.views.AlbumCardView
import com.jakewharton.rxbinding3.view.clicks
import io.reactivex.rxkotlin.addTo
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.fragment_search_artist.*
import java.lang.IllegalStateException
import java.util.concurrent.TimeUnit
import kotlin.math.min

class SearchSingleArtistFragment : BaseFragment<SearchState, SearchAction, SearchProcessor>() {
    private lateinit var artistInfo: ArtistInfo
    private lateinit var topTrackViews: List<TextView>
    private lateinit var albumViews: List<AlbumCardView>

    override fun handleState(state: SearchState) {
        // TODO: Don't think we need any state for artist view
    }

    override fun resolveDepencencies(component: FragmentComponent) {
        component.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search_artist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val info = arguments?.getParcelable<ArtistInfo>(ARTIST_INFO_KEY)

        if (info == null) {
            throw IllegalStateException("Need to provide arguments for SearchArtistFragment. " +
                    "Did you use the getInstance method?")
        } else {
            artistInfo = info
        }

        topTrackViews = listOf(topTrack1, topTrack2, topTrack3, topTrack4, topTrack5)
        albumViews = listOf(artistAlbum1, artistAlbum2, artistAlbum3, artistAlbum4)

        initViews()
    }

    override fun onResume() {
        super.onResume()
        (activity as TitleActivity).setTitle(artistInfo.artist.name)
    }

    private fun initViews() {
        val urlToLoad = if (artistInfo.artist.images.isEmpty()) {
            null
        } else {
            artistInfo.artist.images[0].url
        }
        Glide.with(this)
                .setDefaultRequestOptions(RequestOptions.placeholderOf(R.mipmap.black_blank_person))
                .load(urlToLoad)
                .into(artistImage)

        if (artistInfo.topTracks.isEmpty()) {
            topTrackHeader.visibility = View.GONE
        } else {
            val trackDisplayCount = min(artistInfo.topTracks.size, 5)
            for (i in 0 until trackDisplayCount) {
                val trackView = topTrackViews[i]
                trackView.visibility = View.VISIBLE
                trackView.text = artistInfo.topTracks[i].name
            }
        }

        topTrack1.clicks().map { 0 }
                .mergeWith(topTrack2.clicks().map { 1 })
                .mergeWith(topTrack3.clicks().map { 2 })
                .mergeWith(topTrack4.clicks().map { 3 })
                .mergeWith(topTrack5.clicks().map { 4 })
                .throttleFirst(300, TimeUnit.MILLISECONDS)
                .subscribe { actionStream.accept(SearchAction.TrackSelected(artistInfo.topTracks[it].uri)) }
                .addTo(subscriptions)

        if (artistInfo.initialArtistAlbums.isEmpty()) {
            artistAlbumsHeader.visibility = View.GONE
        }

        for (i in 0..3) {
            val albumView = albumViews[i]
            if (i < artistInfo.initialArtistAlbums.size) {
                albumView.visibility = View.VISIBLE
                albumView.isClickable = true
                albumView.bind(artistInfo.initialArtistAlbums[i])
                if (i % 2 == 0) {
                    albumViews[i + 1].visibility = View.INVISIBLE
                    albumViews[i + 1].isClickable = false
                }
            }
        }

        artistAlbum1.clicks
                .mergeWith(artistAlbum2.clicks)
                .mergeWith(artistAlbum3.clicks)
                .mergeWith(artistAlbum4.clicks)
                .throttleFirst(300, TimeUnit.MILLISECONDS)
                .subscribe { actionStream.accept(SearchAction.AlbumSelected(it.albumId)) }
                .addTo(subscriptions)

        if (artistInfo.initialArtistAlbums.size > 4) {
            allAlbumsHeader.visibility = View.VISIBLE
            allAlbumsHeader.clicks()
                    .throttleFirst(300, TimeUnit.MILLISECONDS)
                    .subscribe { actionStream.accept(SearchAction.ViewArtistAlbumsSelected(artistInfo.artist)) }
                    .addTo(subscriptions)
        }
    }
    companion object {
        private const val ARTIST_INFO_KEY = "artist_info"

        fun getInstance(
                artist: Artist,
                topTracks: List<Track>,
                initialArtistAlbums: List<AlbumSimple>
        ): SearchSingleArtistFragment {
            val fragment = SearchSingleArtistFragment()

            val artistInfo = ArtistInfo(artist, topTracks, initialArtistAlbums)

            val args = Bundle()
            args.putParcelable(ARTIST_INFO_KEY, artistInfo)
            fragment.arguments = args

            return fragment
        }
    }

    @Parcelize
    data class ArtistInfo(
            val artist: Artist,
            val topTracks: List<Track>,
            val initialArtistAlbums: List<AlbumSimple>
    ) : Parcelable
}