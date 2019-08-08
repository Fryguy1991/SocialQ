package com.chrisf.socialq.userinterface.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.FragmentComponent
import com.chrisf.socialq.processor.SearchProcessor
import com.chrisf.socialq.processor.SearchProcessor.SearchAction
import com.chrisf.socialq.processor.SearchProcessor.SearchState
import com.chrisf.socialq.userinterface.adapters.BaseRecyclerViewAdapter
import com.chrisf.socialq.utils.DisplayUtils
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import kaaes.spotify.webapi.android.models.Track
import kotlinx.android.synthetic.main.fragment_search_tracks.*
import kotlinx.android.synthetic.main.holder_search_track.view.*
import java.util.concurrent.TimeUnit

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

        tracksAdapter.clickObservable
                .doOnSubscribe { subscriptions.add(it) }
                .subscribe { actionStream.accept(SearchAction.TrackSelected(it.uri)) }
    }

    companion object {
        private const val INITIAL_TRACK_KEY = "initial_track_key"

        fun getInstance(initialTrackList: List<Track>): SearchTracksFragment {
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

private class SearchTrackAdapter : BaseRecyclerViewAdapter<SearchTrackAdapter.TrackViewHolder, Track>() {
    private val clickRelay: PublishRelay<TrackClick> = PublishRelay.create()
    val clickObservable: Observable<TrackClick>
        get() {
            return clickRelay.hide().throttleFirst(1, TimeUnit.SECONDS)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
                R.layout.holder_search_track,
                parent,
                false
        )
        return TrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(itemList[position])
    }

    inner class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(track: Track) {
            itemView.trackName.text = track.name
            itemView.artistName.text = DisplayUtils.getTrackArtistString(track)

            val url = track.album?.images?.get(0)?.url
            if (url.isNullOrEmpty()) {
                Glide.with(itemView).load(R.color.Transparent).into(itemView.trackArt)
            } else {
                Glide.with(itemView).load(url).into(itemView.trackArt)
            }

            itemView.setOnClickListener {
                if (!track.uri.isNullOrEmpty()) {
                    clickRelay.accept(TrackClick(track.uri))
                }
            }
        }
    }

    data class TrackClick(val uri: String)
}