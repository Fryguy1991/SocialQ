package com.chrisf.socialq.userinterface.activities

import android.content.Intent
import android.os.Bundle
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.ActivityComponent
import com.chrisf.socialq.processor.SearchProcessor
import com.chrisf.socialq.processor.SearchProcessor.SearchAction
import com.chrisf.socialq.processor.SearchProcessor.SearchAction.*
import com.chrisf.socialq.processor.SearchProcessor.SearchState
import com.chrisf.socialq.processor.SearchProcessor.SearchState.*
import com.chrisf.socialq.userinterface.fragments.SearchAlbumFragment
import com.chrisf.socialq.userinterface.fragments.SearchResultsFragment
import com.chrisf.socialq.userinterface.fragments.SearchTracksFragment
import kaaes.spotify.webapi.android.models.Album
import kaaes.spotify.webapi.android.models.Track
import kotlinx.android.synthetic.main.activity_search.*

class NewSearchActivity : BaseActivity<SearchState, SearchAction, SearchProcessor>() {

    override val FRAGMENT_HOLDER_ID = R.id.appFragment

    override fun resolveDependencies(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    override fun handleState(state: SearchState) {
        when (state) {
            is ReportTrackResult -> sendTrackResult(state.trackUri)
            is DisplayAlbum -> navigateToAlbum(state.album)
            is NavigateToAllTracks -> navigateToViewAllTracks(state.initialTrackList)
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

    private fun initViews() {
        // Setup the app toolbar
        setSupportActionBar(appToolbar)
        appToolbar.setTitle(R.string.search_activity_name)
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

    private fun navigateToAlbum(album: Album) {
        appToolbar.title = album.name
        val fragment = SearchAlbumFragment.getInstance(album)
        addFragmentToBackstack(fragment)
    }

    private fun navigateToViewAllTracks(initialTracks: List<Track>) {
        appToolbar.title = getString(R.string.songs)
        val fragment = SearchTracksFragment.getInstance(initialTracks)
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