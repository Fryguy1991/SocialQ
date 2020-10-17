package com.chrisf.socialq.userinterface.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.FragmentComponent
import com.chrisf.socialq.model.spotify.Album
import com.chrisf.socialq.model.spotify.Track
import com.chrisf.socialq.processor.SearchProcessor
import com.chrisf.socialq.userinterface.activities.TitleActivity
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import kotlinx.android.synthetic.main.fragment_search_album.*
import kotlinx.android.synthetic.main.holder_album_art.view.*
import kotlinx.android.synthetic.main.holder_album_track.view.*
import java.lang.IllegalStateException
import java.util.concurrent.TimeUnit

class SearchSingleAlbumFragment : BaseFragment<SearchProcessor.SearchState, SearchProcessor.SearchAction, SearchProcessor>() {

    private lateinit var albumAdapter: SearchAlbumAdapter

    private lateinit var fragmentTitle: String

    override fun resolveDepencencies(component: FragmentComponent) {
        component.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search_album, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val album: Album? = arguments?.getParcelable(ALBUM_KEY)

        if (album == null) {
            throw IllegalStateException("SearchSingleAlbumFragment needs an album to display")
        } else {
            fragmentTitle = album.name

            albumAdapter = SearchAlbumAdapter(album)
            albumRecyclerView.layoutManager = LinearLayoutManager(context)
            albumRecyclerView.adapter = albumAdapter

            albumAdapter.clickObservable
                    .doOnSubscribe { subscriptions.add(it) }
                    .subscribe {
                        if (!it.isNullOrEmpty()) {
                            actionStream.accept(SearchProcessor.SearchAction.TrackSelected(it))
                        }
                    }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as TitleActivity).setTitle(fragmentTitle)
    }

    override fun handleState(state: SearchProcessor.SearchState) {
        // TODO: Should page through album tracks if there are too many
    }

    companion object {
        private const val ALBUM_KEY = "album"

        fun getInstance(album: Album): SearchSingleAlbumFragment {
            val fragment = SearchSingleAlbumFragment()

            val args = Bundle()
            args.putParcelable(ALBUM_KEY, album)
            fragment.arguments = args

            return fragment
        }
    }

    private class SearchAlbumAdapter(private val album: Album) : RecyclerView.Adapter<SearchAlbumAdapter.AlbumViewHolder>() {
        private val clickRelay: PublishRelay<String> = PublishRelay.create()
        val clickObservable: Observable<String>
            get() {
                return clickRelay.hide().throttleFirst(1, TimeUnit.SECONDS)
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
            val layoutResource = when (AlbumItemType.values()[viewType]) {
                AlbumItemType.ALBUM_ART -> R.layout.holder_album_art
                AlbumItemType.TRACK -> R.layout.holder_album_track
            }

            return AlbumViewHolder(LayoutInflater.from(parent.context).inflate(layoutResource, parent, false))
        }

        override fun getItemCount(): Int {
            val numberOfTracks = album.tracks?.items?.size ?: 0
            return numberOfTracks + 1
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) {
                AlbumItemType.ALBUM_ART.ordinal
            } else {
                AlbumItemType.TRACK.ordinal
            }
        }

        override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
            if (position == 0) {
                holder.bind(if (album.images.isEmpty()) null else album.images[0].url)
            } else {
                val trackIndex = position - 1
                holder.bind(album.tracks.items[trackIndex], trackIndex + 1)
            }
        }

        private inner class AlbumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            fun bind(albumArtUrl: String?) {
                Glide.with(itemView)
                        .load(albumArtUrl)
                        .apply(RequestOptions().placeholder(R.mipmap.black_record))
                        .into(itemView.albumArt)
            }

            @SuppressLint("SetTextI18n")
            fun bind(track: Track, position: Int) {
                itemView.albumTrackName.text = track.name
                itemView.albumTrackNumber.text = "$position."
                itemView.setOnClickListener { clickRelay.accept(track.uri) }
            }
        }

        private enum class AlbumItemType() {
            ALBUM_ART,
            TRACK
        }
    }
}