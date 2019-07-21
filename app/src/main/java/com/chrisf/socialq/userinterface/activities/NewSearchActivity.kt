package com.chrisf.socialq.userinterface.activities

import android.os.Bundle
import android.view.WindowManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.ActivityComponent
import com.chrisf.socialq.processor.SearchProcessor
import com.chrisf.socialq.processor.SearchProcessor.SearchAction
import com.chrisf.socialq.processor.SearchProcessor.SearchAction.*
import com.chrisf.socialq.processor.SearchProcessor.SearchState
import com.chrisf.socialq.processor.SearchProcessor.SearchState.*
import com.chrisf.socialq.userinterface.adapters.SearchResultsAdapter
import com.jakewharton.rxbinding3.widget.textChanges
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_search.*
import java.util.concurrent.TimeUnit

class NewSearchActivity : BaseActivity<SearchState, SearchAction, SearchProcessor>() {
    private val baseResultsAdapter = SearchResultsAdapter()

    override fun resolveDependencies(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        initViews()
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
                .throttleWithTimeout(250L, TimeUnit.MILLISECONDS)
                .subscribeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { subscriptions.add(it) }
                .subscribe { actionStream.accept(SearchTermModified(searchTermField.text.toString())) }

        searchResultsRecyclerView.adapter = baseResultsAdapter
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    override fun handleState(state: SearchState) {
        when (state) {
            is DisplayBaseResults -> displayBaseResults(state)
        }
    }

    private fun displayBaseResults(state: DisplayBaseResults) {
        baseResultsAdapter.updateData(state.trackList, state.artistList, state.albumList)
    }
}