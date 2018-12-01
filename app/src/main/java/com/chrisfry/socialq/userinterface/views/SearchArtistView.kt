package com.chrisfry.socialq.userinterface.views

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.chrisfry.socialq.R
import com.chrisfry.socialq.enums.ArtistSearchStep
import com.chrisfry.socialq.userinterface.adapters.SearchTrackListAdapter
import com.chrisfry.socialq.userinterface.adapters.SelectableAlbumListAdapter
import com.chrisfry.socialq.userinterface.adapters.SelectableArtistListAdapter
import com.chrisfry.socialq.userinterface.adapters.SelectableTrackListAdapter
import kaaes.spotify.webapi.android.models.Album
import kaaes.spotify.webapi.android.models.Artist
import kaaes.spotify.webapi.android.models.Track
import kaaes.spotify.webapi.android.models.TrackSimple

class SearchArtistView : ConstraintLayout, SelectableArtistListAdapter.ArtistSelectListener,
        View.OnClickListener, SelectableAlbumListAdapter.AlbumSelectListener, SearchTrackListAdapter.TrackSelectionListener {

    // View presenter
    private lateinit var presenter: SearchArtistViewPresenter
    // State of view
    private var artistSearchState = ArtistSearchStep.NONE

    // Cached values for display
    private lateinit var artistAdapter: SelectableArtistListAdapter
    private lateinit var albumAdapter: SelectableAlbumListAdapter
    private lateinit var cachedArtistName: String
    private lateinit var cachedArtistId: String

    // View elements
    private val baseView = LayoutInflater.from(context).inflate(R.layout.search_artist_layout, this, true)
    // Artist header view elements
    private lateinit var artistHeader : View
    private lateinit var backArrow : View
    private lateinit var artistHeaderText : TextView
    private lateinit var artistHeaderExpandArrow : View
    // Top track header view elements
    private lateinit var topTrackHeader : View
    private lateinit var topTrackText : TextView
    private lateinit var topTrackheaderExpandArrow : View
    // Recycler view for displaying artists/albums/tracks
    private lateinit var resultsRecyclerView: RecyclerView

    // Match super constructors
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    init {
        if (baseView != null) {
            // Get artist header view references
            artistHeader = baseView.findViewById(R.id.cl_artist_header_view)
            backArrow = baseView.findViewById(R.id.iv_left_arrow)
            artistHeaderText = baseView.findViewById(R.id.tv_result_text)
            artistHeaderExpandArrow = baseView.findViewById(R.id.iv_result_arrow)

            // Get top track header view references
            topTrackHeader = baseView.findViewById(R.id.cl_top_track_header)
            topTrackText = baseView.findViewById(R.id.tv_top_track_text)
            topTrackheaderExpandArrow = baseView.findViewById(R.id.iv_top_track_result_arrow)

            // Get recycler view for displaying artists/albums/tracks
            resultsRecyclerView = baseView.findViewById(R.id.rv_result_recycler_view)

            // Add adapter decoration
            val artistsLayoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            resultsRecyclerView.layoutManager = artistsLayoutManager
            resultsRecyclerView.addItemDecoration(QueueItemDecoration(context))

            artistHeader.setOnClickListener(this)
            topTrackHeader.setOnClickListener(this)
        }
    }

    fun setPresenter(presenter: SearchArtistViewPresenter) {
        this.presenter = presenter
    }

    // START METHODS TELLING VIEW WHAT TO DO
    // TODO: Consider pulling these out into a interface?
    /**
     * Sets up view to display artists after a search
     */
    fun loadArtistSearchResults(artistResultList: List<Artist>) {
        if (artistSearchState != ArtistSearchStep.NONE) {
            resetSearchArtistView()
        }
        if (artistResultList.isNotEmpty()) {
            // Show number of artists found
            artistHeaderText.text = String.format(resources.getString(R.string.number_of_artists), artistResultList.size)
            // Set adapter for artists to recycler view
            artistAdapter = SelectableArtistListAdapter(this)
            resultsRecyclerView.adapter = artistAdapter
            artistAdapter.updateAdapter(artistResultList)
        }
    }

    /**
     * Sets up view to display an artist's albums
     */
    fun showArtistAlbums(artistName: String, albumResultList: List<Album>) {
        artistSearchState = ArtistSearchStep.ARTIST_SELECTED

        // Show top tracks option
        topTrackHeader.visibility = View.VISIBLE

        // Change artist header to look like a back option
        cachedArtistName = artistName
        artistHeaderText.text = artistName
        artistHeaderExpandArrow.visibility = View.GONE
        backArrow.visibility = View.VISIBLE

        // Display album results
        albumAdapter = SelectableAlbumListAdapter(this)
        resultsRecyclerView.adapter = albumAdapter
        albumAdapter.updateAdapter(albumResultList)
    }

    /**
     * Sets up view to display an album's tracks
     */
    fun showAlbumTracks(albumImageUrl: String, albumName: String, albumTrackList: List<TrackSimple>) {
        artistSearchState = ArtistSearchStep.ALBUM_SELECTED

        // Display album name in header
        artistHeaderText.text = albumName

        // Hide top track header
        topTrackHeader.visibility = View.GONE

        // TODO: Display album image

        // Display album tracks
        val trackAdapter = SelectableTrackListAdapter(albumTrackList)
        trackAdapter.setTrackSelectionListener(this)
        resultsRecyclerView.adapter = trackAdapter
    }

    /**
     * Sets up view to display an artist's top tracks
     */
    fun showArtistTopTracks(artistName: String, topTrackList: List<Track>) {
        artistSearchState = ArtistSearchStep.TOP_TRACKS

        // Display "X Top Tracks" in header
        artistHeaderText.text = String.format(resources.getString(R.string.artist_top_tracks), artistName)

        // Hide top tracks header
        topTrackHeader.visibility = View.GONE

        // Display artist top tracks
        val trackAdapter = SelectableTrackListAdapter(topTrackList)
        trackAdapter.setTrackSelectionListener(this)
        resultsRecyclerView.adapter = trackAdapter
    }

    /**
     * Returns view to only showing result count header
     */
    fun resetSearchArtistView() {
        artistSearchState = ArtistSearchStep.NONE

        // Reset cached values
        cachedArtistName = ""
        cachedArtistId = ""

        // Reset to beginning header view
        backArrow.visibility = View.GONE
        artistHeaderExpandArrow.visibility = View.VISIBLE
        topTrackHeader.visibility = View.GONE
        resultsRecyclerView.visibility = View.GONE
        artistHeaderExpandArrow.rotation = 0F
    }
    // END METHODS TELLING VIEW WHAT TO DO

    override fun onClick(v: View?) {
        if (v != null) {
            when (v.id) {
                artistHeader.id -> handleArtistHeaderClick()
                topTrackHeader.id -> handleTopTrackClick()
            }
        }
    }

    private fun handleArtistHeaderClick() {
        when (artistSearchState) {
            ArtistSearchStep.NONE -> {
                artistSearchState = ArtistSearchStep.LISTED_ARTISTS

                // Show artists list and request expansion
                resultsRecyclerView.visibility = View.VISIBLE
                artistHeaderExpandArrow.rotation = 180F
                presenter.requestArtistResultsExpansion()
            }
            ArtistSearchStep.LISTED_ARTISTS -> {
                artistSearchState = ArtistSearchStep.NONE

                // Hide artists list and request closure
                resultsRecyclerView.visibility = View.GONE
                resultsRecyclerView.scrollToPosition(0)
                artistHeaderExpandArrow.rotation = 0F
                presenter.requestArtistResultsClosure()
            }
            ArtistSearchStep.ARTIST_SELECTED -> {
                artistSearchState = ArtistSearchStep.LISTED_ARTISTS

                // Hide top tracks and back arrow
                topTrackHeader.visibility = View.GONE
                backArrow.visibility = View.GONE

                // Reshow artist count and expand arrow
                artistHeaderText.text = String.format(resources.getString(R.string.number_of_artists), artistAdapter.itemCount)
                artistHeaderExpandArrow.visibility = View.VISIBLE

                // Reload recyclerview with artist search results
                resultsRecyclerView.adapter = artistAdapter
                resultsRecyclerView.scrollToPosition(0)
            }
            ArtistSearchStep.TOP_TRACKS,
            ArtistSearchStep.ALBUM_SELECTED ->{
                artistSearchState = ArtistSearchStep.ARTIST_SELECTED

                // Show top tracks header
                topTrackHeader.visibility = View.VISIBLE

                // Change artist header text back to artist name
                artistHeaderText.text = cachedArtistName

                // Reload recyclerview with album search results
                resultsRecyclerView.adapter = albumAdapter
                resultsRecyclerView.scrollToPosition(0)
            }
        }
    }

    /**
     * In terms of this custom view, pressing back is the same as touching the artist header.
     * If view is completely closed (ArtistSearchStep.NONE) have nothing to handle.
     */
    fun handleBackPressed() : Boolean {
        return if (artistSearchState == ArtistSearchStep.NONE) {
            false
        } else {
            handleArtistHeaderClick()
            true
        }
    }

    private fun handleTopTrackClick() {
        presenter.notifyTopTracksSelected(cachedArtistId)
    }

    override fun onArtistSelected(artistId: String) {
        cachedArtistId = artistId
        presenter.notifyArtistSelected(artistId)
    }

    override fun onAlbumSelected(albumId: String) {
        presenter.notifyAlbumSelected(albumId)
    }

    override fun onTrackSelection(track: TrackSimple?) {
        if (track != null) {
            presenter.notifyTrackSelected(track.uri)
        }
    }

    interface SearchArtistViewPresenter {
        /**
         * Request view expansion from presenter (hide all other results)
         */
        fun requestArtistResultsExpansion()

        /**
         * Request view closure from presenter (show all results)
         */
        fun requestArtistResultsClosure()

        /**
         * Lets presenter know we've selected an artist
         *
         * @param artistId - Spotify ID of the artist that was selected
         */
        fun notifyArtistSelected(artistId: String)

        /**
         * Lets presenter know we've selected an album
         *
         * @param albumId - Spotify ID of the album that was selected
         */
        fun notifyAlbumSelected(albumId: String)

        /**
         * Lets presenter know we've selected to see the top tracks of an artist
         *
         * @param artistId - Spotify IF of the artist who's top tracks we'd like to see
         */
        fun notifyTopTracksSelected(artistId: String)

        /**
         * Lets presenter know we've selected a track to be added to the queue
         *
         * @param uri - Spotify URI of the track we've selected to add
         */
        fun notifyTrackSelected(uri: String)
    }

}