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
import com.chrisf.socialq.model.spotify.AlbumSimple
import com.chrisf.socialq.model.spotify.Artist
import com.chrisf.socialq.processor.SearchProcessor
import com.chrisf.socialq.processor.SearchProcessor.SearchAction
import com.chrisf.socialq.processor.SearchProcessor.SearchState
import com.chrisf.socialq.userinterface.views.AlbumGridDecorator
import com.chrisf.socialq.userinterface.activities.TitleActivity
import com.chrisf.socialq.userinterface.adapters.BaseRecyclerViewAdapter
import com.chrisf.socialq.userinterface.fragments.SearchAlbumAdapter.AlbumViewholder
import com.chrisf.socialq.utils.DisplayUtils
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import kotlinx.android.synthetic.main.fragment_search_albums.*
import kotlinx.android.synthetic.main.holder_album.view.*
import java.util.concurrent.TimeUnit

class SearchAlbumsFragment : BaseFragment<SearchState, SearchAction, SearchProcessor>() {
    private val albumsAdapter = SearchAlbumAdapter()

    override fun resolveDepencencies(component: FragmentComponent) {
        component.inject(this)
    }

    override fun handleState(state: SearchState) {
        // TODO: Should page through albums
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search_albums, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews()
    }

    override fun onResume() {
        super.onResume()
        val artist = arguments?.getParcelable<Artist>(ARTIST_KEY)
        if (artist != null) {
            (activity as TitleActivity).setTitle(getString(R.string.artist_albums, artist.name))
        } else {
            (activity as TitleActivity).setTitle(getString(R.string.albums))
        }
    }

    private fun initViews() {
        val albumArray = arguments?.getParcelableArrayList<AlbumSimple>(INITIAL_ALBUM_KEY)

        if (albumArray == null) {
            albumsAdapter.updateAdapter(emptyList())
        } else {
            albumsAdapter.updateAdapter(albumArray.toList())
        }

        albumsAdapter.clickObservable
                .subscribe { actionStream.accept(SearchAction.AlbumSelected(it.albumId)) }
                .addTo(subscriptions)

        albumsRecyclerView.adapter = albumsAdapter
        albumsRecyclerView.addItemDecoration(AlbumGridDecorator())
        albumsRecyclerView.layoutManager = GridLayoutManager(context, AlbumGridDecorator.SPAN_COUNT)
    }

    companion object {
        private const val INITIAL_ALBUM_KEY = "initial_albums"
        private const val ARTIST_KEY = "albums_artist"

        fun getInstance(initialAlbumList: List<AlbumSimple>): SearchAlbumsFragment {
            return getInstance(null, initialAlbumList)
        }

        fun getInstance(artist: Artist?, initialAlbumList: List<AlbumSimple>): SearchAlbumsFragment {
            val fragment = SearchAlbumsFragment()

            val arrayList = ArrayList<AlbumSimple>()
            arrayList.addAll(initialAlbumList)

            val args = Bundle().apply {
                putParcelableArrayList(INITIAL_ALBUM_KEY, arrayList)
                if (artist != null) {
                    putParcelable(ARTIST_KEY, artist)
                }
            }
            fragment.arguments = args

            return fragment
        }
    }
}

private class SearchAlbumAdapter : BaseRecyclerViewAdapter<AlbumViewholder, AlbumSimple>() {
    private val clickRelay: PublishRelay<AlbumClick> = PublishRelay.create()
    val clickObservable: Observable<AlbumClick>
        get() {
            return clickRelay.hide().throttleFirst(1, TimeUnit.SECONDS)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewholder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.holder_album, parent, false)
        return AlbumViewholder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewholder, position: Int) {
        holder.bind(itemList[position])
    }


    private inner class AlbumViewholder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(album: AlbumSimple) {
            Glide.with(itemView)
                    .setDefaultRequestOptions(RequestOptions.placeholderOf(R.mipmap.black_record))
                    .load(if (album.images.isEmpty()) null else album.images[0].url)
                    .into(itemView.albumArt)

            itemView.albumName.text = album.name
            itemView.artistName.text = DisplayUtils.getAlbumArtistString(album)

            itemView.setOnClickListener { clickRelay.accept(AlbumClick(album.id)) }
        }
    }
}

private data class AlbumClick(val albumId: String)