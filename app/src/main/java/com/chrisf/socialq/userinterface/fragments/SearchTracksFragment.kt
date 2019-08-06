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
import com.chrisf.socialq.processor.SearchProcessor.SearchState
import com.chrisf.socialq.userinterface.adapters.SearchTrackAdapter
import kaaes.spotify.webapi.android.models.Track
import kotlinx.android.synthetic.main.fragment_search_tracks.*
import timber.log.Timber

class SearchTracksFragment : BaseFragment<SearchState, SearchAction, SearchProcessor>() {
    private val tracksAdapter = SearchTrackAdapter()

    override fun handleState(state: SearchState) {
        // TODO: Request more tracks for "infinite" scrolling
    }

    override fun resolveDepencencies(component: FragmentComponent) {
        component.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search_tracks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews()
    }

    private fun initViews() {
        val trackArray = arguments?.getParcelableArrayList<Track>(INITIAL_TRACK_KEY)

        if (trackArray == null) {
            tracksAdapter.updateAdapter(emptyList())
        } else {
            tracksAdapter.updateAdapter(trackArray.toList())
        }

        tracksRecyclerView.layoutManager = LinearLayoutManager(context)
        tracksRecyclerView.adapter = tracksAdapter
    }

    companion object {
        private const val INITIAL_TRACK_KEY = "initial_track_key"

        fun getInstance(initialTrackList: List<Track>) : SearchTracksFragment {
            val fragment = SearchTracksFragment()

            val arrayList = ArrayList<Track>()
            arrayList.addAll(initialTrackList)

            val args = Bundle()
            args.putParcelableArrayList(INITIAL_TRACK_KEY, arrayList)
            fragment.arguments = args

            return fragment
        }
    }
}