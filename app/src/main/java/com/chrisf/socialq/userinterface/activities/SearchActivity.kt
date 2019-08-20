package com.chrisf.socialq.userinterface.activities

import android.content.Intent
import android.os.Bundle
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.ActivityComponent
import com.chrisf.socialq.model.spotify.Album
import com.chrisf.socialq.model.spotify.AlbumSimple
import com.chrisf.socialq.model.spotify.Track
import com.chrisf.socialq.processor.SearchProcessor
import com.chrisf.socialq.processor.SearchProcessor.SearchAction
import com.chrisf.socialq.processor.SearchProcessor.SearchAction.*
import com.chrisf.socialq.processor.SearchProcessor.SearchState
import com.chrisf.socialq.processor.SearchProcessor.SearchState.*
import com.chrisf.socialq.userinterface.fragments.*
import kotlinx.android.synthetic.main.activity_search.*

class SearchActivity : BaseActivity<SearchState, SearchAction, SearchProcessor>(), TitleActivity {

    override val FRAGMENT_HOLDER_ID = R.id.appFragment

    override fun resolveDependencies(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    override fun handleState(state: SearchState) {
        when (state) {
            is ReportTrackResult -> sendTrackResult(state.trackUri)
            is DisplayAlbum -> navigateToAlbum(state.album)
            is NavigateToAllTracks -> navigateToViewAllTracks(state.initialTrackList)
            is NavigateToAllAlbums -> navigateToViewAllAlbums(state.initialAlbumList)
            is NavigateToAllArtists -> navigateToViewAllArtists(state)
            is NavigateToArtistAlbums -> navigateToArtistAlbums(state)
            is DisplayArtist -> navigateToArtist(state)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        initViews()
        actionStream.accept(ViewCreated)
    }

    override fun onResume() {
        super.onResume()

        actionStream.accept(ViewResumed)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_to_bottom)
    }

    private fun initViews() {
        // Setup the app toolbar
        setSupportActionBar(appToolbar)
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        }

        val fragment = SearchResultsFragment.getInstance()
        addFragment(fragment, RESULTS_ID)

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                appToolbar.title = getString(R.string.search_activity_name)
            }
        }
    }

    override fun setTitle(title: String) {
        appToolbar.title = title
    }

    private fun navigateToAlbum(album: Album) {
        val fragment = SearchSingleAlbumFragment.getInstance(album)
        addFragmentToBackstack(fragment)
    }

    private fun navigateToArtist(state: DisplayArtist) {
        val fragment = SearchSingleArtistFragment.getInstance(
                state.artist,
                state.artistTopTracks,
                state.artistAlbums
        )
        addFragmentToBackstack(fragment)
    }

    private fun navigateToViewAllTracks(initialTracks: List<Track>) {
        val fragment = SearchTracksFragment.getInstance(initialTracks)
        addFragmentToBackstack(fragment)
    }

    private fun navigateToViewAllAlbums(initialAlbums: List<AlbumSimple>) {
        val fragment = SearchAlbumsFragment.getInstance(initialAlbums)
        addFragmentToBackstack(fragment)
    }

    private fun navigateToViewAllArtists(state: NavigateToAllArtists) {
        val fragment = SearchArtistsFragment.getInstance(state.initialArtistList)
        addFragmentToBackstack(fragment)
    }

    private fun navigateToArtistAlbums(state: NavigateToArtistAlbums) {
        val fragment = SearchAlbumsFragment.getInstance(state.artist, state.initialAlbumList)
        addFragmentToBackstack(fragment)
    }

    private fun sendTrackResult(uri: String) {
        val resultIntent = Intent()
        resultIntent.putExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY, uri)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        private const val RESULTS_ID = "results"
    }
}

interface TitleActivity {
    fun setTitle(title: String)
}