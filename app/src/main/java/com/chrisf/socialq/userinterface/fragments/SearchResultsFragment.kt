package com.chrisf.socialq.userinterface.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.FragmentComponent
import com.chrisf.socialq.processor.SearchProcessor
import com.chrisf.socialq.processor.SearchProcessor.SearchAction
import com.chrisf.socialq.processor.SearchProcessor.SearchAction.*
import com.chrisf.socialq.processor.SearchProcessor.SearchState
import com.chrisf.socialq.processor.SearchProcessor.SearchState.*
import com.chrisf.socialq.userinterface.adapters.SearchResultsAdapter
import com.chrisf.socialq.userinterface.adapters.SearchResultsAdapter.SearchResultClick.*
import com.jakewharton.rxbinding3.widget.textChanges
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.fragment_search_results.*
import java.util.concurrent.TimeUnit

class SearchResultsFragment : BaseFragment<SearchState, SearchAction, SearchProcessor>() {
    private val baseResultsAdapter = SearchResultsAdapter()

    override fun handleState(state: SearchState) {
        when (state) {
            DisplayBaseView -> displayBaseView()
            is DisplayBaseResults -> displayBaseResults(state)
            is DisplayNoResults -> displayNoResults(state.searchTerm)
        }
    }

    override fun resolveDepencencies(component: FragmentComponent) {
        component.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search_results, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    private fun initViews() {

        @Suppress("CheckResult")
        searchTermField.textChanges()
                .skipInitialValue()
                .throttleLast(250L, TimeUnit.MILLISECONDS)
                .subscribeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { subscriptions.add(it) }
                .subscribe { actionStream.accept(SearchTermModified(searchTermField.text.toString())) }

        searchResultsRecyclerView.adapter = baseResultsAdapter
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(context)

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

    companion object {
        fun getInstance() : SearchResultsFragment {
            return SearchResultsFragment()
        }
    }
}