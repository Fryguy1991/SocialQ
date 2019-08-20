package com.chrisf.socialq.userinterface.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.FragmentComponent
import com.chrisf.socialq.extensions.addTo
import com.chrisf.socialq.model.spotify.Artist
import com.chrisf.socialq.processor.SearchProcessor
import com.chrisf.socialq.processor.SearchProcessor.SearchAction
import com.chrisf.socialq.processor.SearchProcessor.SearchState
import com.chrisf.socialq.userinterface.AlbumGridDecorator
import com.chrisf.socialq.userinterface.activities.TitleActivity
import com.chrisf.socialq.userinterface.adapters.BaseRecyclerViewAdapter
import com.chrisf.socialq.userinterface.fragments.SearchArtistsAdapter.ArtistViewHolder
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import kotlinx.android.synthetic.main.fragment_search_artists.*
import kotlinx.android.synthetic.main.holder_artist.view.*
import java.util.concurrent.TimeUnit

class SearchArtistsFragment : BaseFragment<SearchState, SearchAction, SearchProcessor>() {
    private val artistsAdapter = SearchArtistsAdapter()

    override fun resolveDepencencies(component: FragmentComponent) {
        component.inject(this)
    }

    override fun handleState(state: SearchState) {
        // TODO: Should page through artists
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search_artists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews()
    }

    override fun onResume() {
        super.onResume()
        (activity as TitleActivity).setTitle(getString(R.string.artists))
    }

    private fun initViews() {
        val artistArray = arguments?.getParcelableArrayList<Artist>(INITIAL_ARTIST_KEY)

        if (artistArray == null) {
            artistsAdapter.updateAdapter(emptyList())
        } else {
            artistsAdapter.updateAdapter(artistArray.toList())
        }

        artistsAdapter.clickObservable
                .subscribe { actionStream.accept(SearchAction.ArtistSelected(it.artistId)) }
                .addTo(subscriptions)

        artistsRecyclerView.adapter = artistsAdapter
        artistsRecyclerView.addItemDecoration(AlbumGridDecorator())
        artistsRecyclerView.layoutManager = GridLayoutManager(context, AlbumGridDecorator.SPAN_COUNT)
    }

    companion object {
        private const val INITIAL_ARTIST_KEY = "initial_artists"

        fun getInstance(initialArtistList: List<Artist>): SearchArtistsFragment {
            val fragment = SearchArtistsFragment()

            val arrayList = ArrayList<Artist>()
            arrayList.addAll(initialArtistList)

            val args = Bundle().apply {
                putParcelableArrayList(INITIAL_ARTIST_KEY, arrayList)
            }
            fragment.arguments = args

            return fragment
        }
    }
}

private class SearchArtistsAdapter : BaseRecyclerViewAdapter<ArtistViewHolder, Artist>() {
    private val clickRelay: PublishRelay<ArtistClick> = PublishRelay.create()
    val clickObservable: Observable<ArtistClick>
        get() {
            return clickRelay.hide().throttleFirst(300, TimeUnit.MILLISECONDS)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.holder_artist, parent, false)
        return ArtistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        holder.bind(itemList[position])
    }


    private inner class ArtistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(artist: Artist) {
            Glide.with(itemView)
                    .setDefaultRequestOptions(RequestOptions.placeholderOf(R.mipmap.black_blank_person))
                    .load(if (artist.images.isEmpty()) null else artist.images[0].url)
                    .into(itemView.artistImage)

            itemView.artistName.text = artist.name

            itemView.setOnClickListener { clickRelay.accept(ArtistClick(artist.id)) }
        }
    }
}

private data class ArtistClick(val artistId: String)
