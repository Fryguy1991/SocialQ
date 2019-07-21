package com.chrisf.socialq.userinterface.activities

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.ActivityComponent
import com.chrisf.socialq.processor.SearchProcessor
import com.chrisf.socialq.processor.SearchProcessor.SearchAction
import com.chrisf.socialq.processor.SearchProcessor.SearchAction.*
import com.chrisf.socialq.processor.SearchProcessor.SearchState
import com.chrisf.socialq.processor.SearchProcessor.SearchState.*
import com.chrisf.socialq.userinterface.fragments.SearchResultsFragment
import kotlinx.android.synthetic.main.activity_search.*

class NewSearchActivity : BaseActivity<SearchState, SearchAction, SearchProcessor>() {

    private lateinit var searchResultsFragment: SearchResultsFragment

    override fun resolveDependencies(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    override fun handleState(state: SearchState) {
        when (state) {
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

        searchResultsFragment = SearchResultsFragment.getInstance()

        supportFragmentManager
                .beginTransaction()
                .add(appFragment.id, searchResultsFragment)
                .commit()
    }

    private fun sendTrackResult(uri: String) {
        val resultIntent = Intent()
        resultIntent.putExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY, uri)
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}