package com.chrisfry.socialq.userinterface.fragments

import android.content.Context
import android.os.Bundle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import butterknife.BindViews
import butterknife.ButterKnife
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.business.dagger.modules.SpotifyModule
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent
import com.chrisfry.socialq.model.AccessModel
import com.chrisfry.socialq.userinterface.adapters.SearchTrackListAdapter
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration
import com.chrisfry.socialq.userinterface.widgets.SearchArtistView
import com.chrisfry.socialq.utils.ApplicationUtils
import com.chrisfry.socialq.utils.DisplayUtils
import kaaes.spotify.webapi.android.SpotifyService
import kaaes.spotify.webapi.android.models.Artist
import kaaes.spotify.webapi.android.models.Track
import kaaes.spotify.webapi.android.models.TrackSimple
import java.util.ArrayList
import java.util.HashMap

class SearchFragment : SpotifyFragment(), SearchArtistView.SearchArtistViewPresenter, SearchTrackListAdapter.TrackSelectionListener {
    val TAG = SearchFragment::class.java.name

    // UI elements
    private lateinit var mMainLayout: ConstraintLayout
    private lateinit var mSearchButton: View
    private lateinit var mSearchArtistView: SearchArtistView
    private lateinit var mSongLayout: ViewGroup
    private lateinit var mAlbumLayout: ViewGroup
    private lateinit var mSearchText: EditText
    private lateinit var mSongText: TextView
    private lateinit var mAlbumText: TextView
    private lateinit var mSongResults: RecyclerView

    @BindViews(R.id.cv_song_result_layout, R.id.cv_album_result_layout)
    lateinit var mResultsBaseViews: MutableList<View>

    // Search result containers
    private lateinit var mResultTrackList: MutableList<Track>
    private lateinit var mResultArtistList: MutableList<Artist>
//    private lateinit var mResultAlbumList: MutableList<AlbumSimple>

    // Recycler view adapters
    private lateinit var mSongResultsAdapter: SearchTrackListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val accessToken = AccessModel.getAccessToken()
        if (accessToken == null || System.currentTimeMillis() > AccessModel.getAccessExpireTime()) {
            Log.d(TAG, "Invalid Access Token")
            Toast.makeText(context, "Invalid Access Token", Toast.LENGTH_LONG).show()
        } else {
            initSpotifySearchElements(accessToken)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val baseView = inflater.inflate(R.layout.search_screen, container, false)
        mMainLayout = baseView.findViewById(R.id.search_main)
        ButterKnife.bind(this, baseView)
        initUi()
        addListeners()
        return baseView
    }

    override fun handleOnBackPressed(): Boolean {
        // TODO: Handle album search closing when album view is implemented.
        if (mSongLayout.visibility == View.VISIBLE && mSongResults.visibility == View.VISIBLE) {
            // If track results are showing close them
            closeAnyRecyclerViews()
            return true
        } else if (mSearchArtistView.visibility == View.VISIBLE && mSearchArtistView.handleBackPressed()) {
            // mSearchArtistView.handleBackPressed will handle operation of back (if applicable)
            return true
        } else {
            return false
        }
    }

    private fun initSpotifySearchElements(accessToken: String) {
        // Setup service for searching Spotify library
        val componenet = DaggerSpotifyComponent.builder().spotifyModule(
                SpotifyModule(accessToken)).build()

        mSpotifyApi = componenet.api()
        mSpotifyService = componenet.service()
    }

    private fun initUi() {
        // Initialize UI elements
        mSearchButton = mMainLayout.findViewById(R.id.btn_search)
        mSongLayout = mMainLayout.findViewById(R.id.cv_song_result_layout)
        mAlbumLayout = mMainLayout.findViewById(R.id.cv_album_result_layout)
        mSearchText = mMainLayout.findViewById(R.id.et_search_edit_text)
        mSongText = mSongLayout.findViewById(R.id.tv_result_text)
        mAlbumText = mAlbumLayout.findViewById(R.id.tv_result_text)

        mSongResults = mSongLayout.findViewById(R.id.rv_result_recycler_view)
        mSongResultsAdapter = SearchTrackListAdapter(ArrayList())
        mSongResults.setAdapter(mSongResultsAdapter)
        val songsLayoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        mSongResults.setLayoutManager(songsLayoutManager)
        mSongResults.addItemDecoration(QueueItemDecoration(context))

        mSearchArtistView = mMainLayout.findViewById(R.id.cv_artist_result_layout)
        mSearchArtistView.setPresenter(this)
    }

    private fun addListeners() {
        mMainLayout.setOnTouchListener { view, motionEvent ->
            view.requestFocus()
            hideKeyboard()
            true
        }

        mSearchButton.setOnClickListener {
            // Don't search if search text is empty
            if (!mSearchText.text.toString().isEmpty()) {
                mSearchText.clearFocus()
                searchByText(mSearchText.text.toString())
                mSongResults.visibility = View.GONE
            }
        }
        val showResultsClickListener = View.OnClickListener { view ->
            val resultsView = view.findViewById<View>(R.id.rv_result_recycler_view)
            val isCurrentlyVisible = resultsView.visibility == View.VISIBLE

            // Hide recycler view and rotate arrow
            resultsView.visibility = if (isCurrentlyVisible) View.GONE else View.VISIBLE
            view.findViewById<View>(R.id.iv_result_arrow).rotation = (if (isCurrentlyVisible) 0 else 180).toFloat()

            if (isCurrentlyVisible) {
                // If closing category show hidden categories (with results)
                closeResultLayout(view)
            } else {
                // If clicking on a category hide other categories
                expandResultsView(view)
            }
        }

        // Add above listener to all base layouts
        for (baseView in mResultsBaseViews) {
            baseView.setOnClickListener(showResultsClickListener)
        }

        mSongResultsAdapter.setTrackSelectionListener(this)
    }


    private fun expandResultsView(view: View) {
        val constraintSet = ConstraintSet()
        Log.d(TAG, "Hiding all but touched layout")
        for (baseView in mResultsBaseViews) {
            if (baseView.getId() != view.id) {
                baseView.setVisibility(View.GONE)
            }
        }

        // Bottom of view should be constrained to bottom of parent
        // Height should match constraint
        constraintSet.clone(mMainLayout)
        constraintSet.connect(
                view.id,
                ConstraintSet.BOTTOM, mMainLayout.id,
                ConstraintSet.BOTTOM,
                DisplayUtils.convertDpToPixels(context, 8))
        constraintSet.constrainHeight(view.id, ConstraintSet.MATCH_CONSTRAINT)
        constraintSet.applyTo(mMainLayout)
    }

    private fun closeResultLayout(view: View) {
        val constraintSet = ConstraintSet()
        updateVisibilityBasedOnResults()
        Log.d(TAG, "Showing all result layouts")

        // Bottom of view should no longer be constrained
        // Height should return to wrap content
        constraintSet.clone(mMainLayout)
        constraintSet.clear(view.id, ConstraintSet.BOTTOM)
        constraintSet.constrainHeight(view.id, ConstraintSet.WRAP_CONTENT)
        constraintSet.applyTo(mMainLayout)
    }

    private fun hideKeyboard() {
        val keyboardActivity = activity
        if (keyboardActivity != null) {
            val inputManager = keyboardActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (keyboardActivity.currentFocus != null) {
                inputManager.hideSoftInputFromWindow(keyboardActivity.currentFocus.windowToken, 0)
            }
        }
    }

    private fun searchByText(searchText: String) {
        Log.d(TAG, "Searching for: $searchText")

        // Create options to set limit for search results to 50 items
        val options = HashMap<String, Any>()
        options[SpotifyService.LIMIT] = AppConstants.SPOTIFY_SEARCH_LIMIT

        // Get results from spotify
        mResultArtistList = mSpotifyService.searchArtists(searchText, options).artists.items
        //        mResultAlbumList = mSpotifyService.searchAlbums(searchText, options).albums.items;
        mResultTrackList = mSpotifyService.searchTracks(searchText, options).tracks.items

        if (mResultArtistList.isEmpty() && mResultTrackList.isEmpty()) {
            // Didn't find anything.  Toast to let user know
            Toast.makeText(context, String.format(getString(R.string.no_results_found), searchText), Toast.LENGTH_LONG).show()
        } else {
            // Don't hide keyboard if no results are found
            hideKeyboard()
        }

        // Update result views
        closeAnyRecyclerViews()
        mSearchArtistView.resetSearchArtistView()
        closeResultLayout(mSearchArtistView)

        updateVisibilityBasedOnResults()

        updateBaseLayoutResultCount()
        updateAdapters()

        // Load custom artist search view with results
        mSearchArtistView.loadArtistSearchResults(mResultArtistList)
    }

    private fun updateVisibilityBasedOnResults() {
        // Hide expandable views based on results
//        mAlbumLayout.visibility = if (mResultAlbumList.size == 0) View.GONE else View.VISIBLE
        mSearchArtistView.visibility = if (mResultArtistList.size == 0) View.GONE else View.VISIBLE
        mSongLayout.visibility = if (mResultTrackList.size == 0) View.GONE else View.VISIBLE
    }

    private fun updateBaseLayoutResultCount() {
        //Update count display on base layouts
        mSongText.text = String.format(getString(R.string.number_of_songs), mResultTrackList.size)
//        mAlbumText.text = String.format(getString(R.string.number_of_albums), mResultAlbumList.size)
    }

    private fun updateAdapters() {
        mSongResultsAdapter.updateQueueList(mResultTrackList)

        // TODO: Update album adapter once we have them
    }


    private fun closeAnyRecyclerViews() {
        for (currentBaseView in mResultsBaseViews) {
            // Hide recycler view and rotate arrow (if needed)
            currentBaseView.findViewById<RecyclerView>(R.id.rv_result_recycler_view).visibility = View.GONE
            currentBaseView.findViewById<View>(R.id.iv_result_arrow).rotation = 0f
            // Bottom of view should be constrained to bottom of parent
            // Height should match constraint
            val constraintSet = ConstraintSet()
            constraintSet.clone(mMainLayout)
            constraintSet.clear(currentBaseView.getId(), ConstraintSet.BOTTOM)
            constraintSet.constrainHeight(currentBaseView.getId(), ConstraintSet.WRAP_CONTENT)
            constraintSet.applyTo(mMainLayout)
        }
    }

    override fun onTrackSelection(track: TrackSimple?) {
        ApplicationUtils.setSearchResults(listOf(track!!.uri))
        findNavController().navigateUp()
    }

    override fun requestArtistResultsExpansion() {
        expandResultsView(mSearchArtistView)
    }

    override fun requestArtistResultsClosure() {
        closeResultLayout(mSearchArtistView)
    }

    override fun notifyArtistSelected(artistId: String) {
        val artistName = mSpotifyService.getArtist(artistId).name
        val albums = mSpotifyService.getArtistAlbums(artistId)
        mSearchArtistView.showArtistAlbums(artistName, albums.items)
    }

    override fun notifyAlbumSelected(albumId: String) {
        val albumToShow = mSpotifyService.getAlbum(albumId)
        mSearchArtistView.showAlbumTracks("not_implemented", albumToShow.name, albumToShow.tracks.items)
    }

    override fun notifyTopTracksSelected(artistId: String) {
        val artistToSHow = mSpotifyService.getArtist(artistId)
        val artistTopTracks = mSpotifyService.getArtistTopTrack(artistId, "US")
        mSearchArtistView.showArtistTopTracks(artistToSHow.name, artistTopTracks.tracks)
    }

    override fun notifyTrackSelected(uri: String) {
        ApplicationUtils.setSearchResults(listOf(uri))
        findNavController().navigateUp()
    }


}