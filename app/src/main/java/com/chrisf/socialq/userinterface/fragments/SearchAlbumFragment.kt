package com.chrisf.socialq.userinterface.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.FragmentComponent
import com.chrisf.socialq.processor.SearchProcessor
import kaaes.spotify.webapi.android.models.Album
import java.lang.IllegalStateException

class SearchAlbumFragment : BaseFragment<SearchProcessor.SearchState, SearchProcessor.SearchAction, SearchProcessor>() {

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
            throw IllegalStateException("SearchAlbumFragment needs an album to display")
        } else {
            // TODO: init views
        }
    }

    override fun handleState(state: SearchProcessor.SearchState) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object {
        private const val ALBUM_KEY = "album"

        fun getInstance(album: Album) : SearchAlbumFragment {
            val fragment = SearchAlbumFragment()

            val args = Bundle()
            args.putParcelable(ALBUM_KEY, album)
            fragment.arguments = args

            return fragment
        }
    }
}