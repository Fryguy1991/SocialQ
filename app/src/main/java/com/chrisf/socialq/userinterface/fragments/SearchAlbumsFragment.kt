package com.chrisf.socialq.userinterface.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.FragmentComponent
import com.chrisf.socialq.model.spotify.AlbumSimple
import com.chrisf.socialq.processor.SearchProcessor
import com.chrisf.socialq.userinterface.AlbumGridDecorator
import com.chrisf.socialq.userinterface.adapters.BaseRecyclerViewAdapter
import com.chrisf.socialq.utils.DisplayUtils
import kotlinx.android.synthetic.main.fragment_search_albums.*
import kotlinx.android.synthetic.main.holder_album.view.*

class SearchAlbumsFragment : BaseFragment<SearchProcessor.SearchState, SearchProcessor.SearchAction, SearchProcessor>() {
    private val albumsAdapter = SearchAlbumAdapter()

    override fun resolveDepencencies(component: FragmentComponent) {
        component.inject(this)
    }

    override fun handleState(state: SearchProcessor.SearchState) {
        // TODO: Should page through albums
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search_albums, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews()
    }

    private fun initViews() {
        val albumArray = arguments?.getParcelableArrayList<AlbumSimple>(INITIAL_ALBUM_KEY)

        if (albumArray == null) {
            albumsAdapter.updateAdapter(emptyList())
        } else {
            albumsAdapter.updateAdapter(albumArray.toList())
        }

        albumsRecyclerView.adapter = albumsAdapter
        albumsRecyclerView.addItemDecoration(AlbumGridDecorator())
        albumsRecyclerView.layoutManager = GridLayoutManager(context, AlbumGridDecorator.SPAN_COUNT)
    }

    companion object {
        private const val INITIAL_ALBUM_KEY = "initial_albums"

        fun getInstance(initialAlbumList: List<AlbumSimple>) : SearchAlbumsFragment {
            val fragment = SearchAlbumsFragment()

            val arrayList = ArrayList<AlbumSimple>()
            arrayList.addAll(initialAlbumList)

            val args = Bundle()
            args.putParcelableArrayList(INITIAL_ALBUM_KEY, arrayList)
            fragment.arguments = args

            return fragment
        }
    }
}

private class SearchAlbumAdapter : BaseRecyclerViewAdapter<AlbumViewholder, AlbumSimple>() {
    // TODO: Handling clicking an album
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewholder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.holder_album, parent, false)
        return AlbumViewholder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewholder, position: Int) {
        holder.bind(itemList[position])
    }

}

private class AlbumViewholder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    lateinit var albumId: String

    fun bind(album: AlbumSimple) {
        albumId = album.id

        if (album.images.isNotEmpty()) {
            Glide.with(itemView).load(album.images[0].url).into(itemView.albumArt)
        }

        itemView.albumName.text = album.name
        itemView.artistName.text = DisplayUtils.getAlbumArtistString(album)
    }
}