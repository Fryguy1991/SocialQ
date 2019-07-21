package com.chrisf.socialq.userinterface.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.ActivityComponent
import com.chrisf.socialq.processor.SearchProcessor
import com.chrisf.socialq.processor.SearchProcessor.SearchAction
import com.chrisf.socialq.processor.SearchProcessor.SearchAction.*
import com.chrisf.socialq.processor.SearchProcessor.SearchState
import com.chrisf.socialq.processor.SearchProcessor.SearchState.*
import com.chrisf.socialq.userinterface.adapters.SearchResultsAdapter
import com.chrisf.socialq.userinterface.adapters.SearchResultsAdapter.SearchResultClick.*
import com.jakewharton.rxbinding3.widget.textChanges
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_search.*
import java.util.concurrent.TimeUnit

class NewSearchActivity : BaseActivity<SearchState, SearchAction, SearchProcessor>() {
    private val baseResultsAdapter = SearchResultsAdapter()

    override fun resolveDependencies(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    override fun handleState(state: SearchState) {
        when (state) {
            DisplayBaseView -> displayBaseView()
            is DisplayBaseResults -> displayBaseResults(state)
            is DisplayNoResults -> displayNoResults(state.searchTerm)
            is ReportTrackResult -> sendTrackResult(state.trackUri)
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

        // Stop soft keyboard from pushing UI up
        // TODO: Do I actually want to do this? Probably not
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        @Suppress("CheckResult")
        searchTermField.textChanges()
                .skipInitialValue()
                .throttleLast(250L, TimeUnit.MILLISECONDS)
                .subscribeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { subscriptions.add(it) }
                .subscribe { actionStream.accept(SearchTermModified(searchTermField.text.toString())) }

        searchResultsRecyclerView.adapter = baseResultsAdapter
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)

        @Suppress("CheckResult")
        baseResultsAdapter.clickObservable
                .subscribeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { subscriptions.add(it) }
                .subscribe {
                    when (it) {
                        is TrackClick -> actionStream.accept(TrackSelected(it.uri))
                        is ArtistClick -> actionStream.accept(ArtistSelected(it.id))
                        is AlbumClick -> actionStream.accept(AlbumSelected(it.id))
                    }
                }
    }

    private fun displayBaseView() {
        baseResultsAdapter.updateData(emptyList(), emptyList(), emptyList())
        searchResultsRecyclerView.visibility = View.GONE
        noResultsText.visibility = View.GONE
    }

    private fun displayBaseResults(state: DisplayBaseResults) {
        // If search term doesn't match what's in edittext eat the result
        if (searchTermField.text.toString() != state.searchTerm) {
            return
        }

        baseResultsAdapter.updateData(state.trackList, state.artistList, state.albumList)
        searchResultsRecyclerView.visibility = View.VISIBLE
        noResultsText.visibility = View.GONE
    }

    private fun displayNoResults(term: String) {
        searchResultsRecyclerView.visibility = View.GONE
        noResultsText.text = String.format(getString(R.string.no_results_found), term)
        noResultsText.visibility = View.VISIBLE
    }

    private fun sendTrackResult(uri: String) {
        val resultIntent = Intent()
        resultIntent.putExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY, uri)
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}