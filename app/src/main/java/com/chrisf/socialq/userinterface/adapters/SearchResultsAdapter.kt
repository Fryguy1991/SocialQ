package com.chrisf.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.chrisf.socialq.R
import com.chrisf.socialq.model.spotify.AlbumSimple
import com.chrisf.socialq.userinterface.adapters.SearchResultsAdapter.SearchViewType.*
import com.chrisf.socialq.utils.DisplayUtils
import kaaes.spotify.webapi.android.models.Artist
import kaaes.spotify.webapi.android.models.Track
import kotlinx.android.synthetic.main.holder_search_album.view.*
import kotlinx.android.synthetic.main.holder_search_artist.view.*
import kotlinx.android.synthetic.main.holder_search_header.view.*
import kotlinx.android.synthetic.main.holder_search_track.view.*
import kotlinx.android.synthetic.main.holder_search_track.view.artistName
import kotlinx.android.synthetic.main.holder_search_track.view.trackName
import kotlinx.android.synthetic.main.holder_view_all_header.view.*
import kotlin.math.min

class SearchResultsAdapter(private val headerChildCount: Int = 3)
    : RecyclerView.Adapter<SearchResultsViewHolder>() {
    private val trackList = mutableListOf<Track>()
    private val artistList = mutableListOf<Artist>()
    private val albumList = mutableListOf<AlbumSimple>()

    fun updateData(
            tracks: List<Track>,
            artists: List<Artist>,
            albums: List<AlbumSimple>
    ) {
        trackList.clear()
        trackList.addAll(tracks)
        artistList.clear()
        artistList.addAll(artists)
        albumList.clear()
        albumList.addAll(albums)
        // TODO: Replace below
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultsViewHolder {
        val layoutId = when (SearchViewType.values()[viewType]) {
            TRACK_HEADER,
            ARTIST_HEADER,
            ALBUM_HEADER -> R.layout.holder_search_header
            ALL_TRACKS_HEADER,
            ALL_ARTIST_HEADER,
            ALL_ALBUM_HEADER -> R.layout.holder_view_all_header
            TRACK -> R.layout.holder_search_track
            ARTIST -> R.layout.holder_search_artist
            ALBUM -> R.layout.holder_search_album
        }
        val view =  LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return SearchResultsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchResultsViewHolder, position: Int) {
        val viewType = SearchViewType.values()[getItemViewType(position)]
        when (viewType) {
            TRACK_HEADER -> holder.bindHeader("Songs")
            ARTIST_HEADER -> holder.bindHeader("Artists")
            ALBUM_HEADER -> holder.bindHeader("Albums")
            ALL_TRACKS_HEADER -> holder.bindViewAllButton("See All Songs")
            ALL_ARTIST_HEADER -> holder.bindViewAllButton("See All Artists")
            ALL_ALBUM_HEADER -> holder.bindViewAllButton("See All Albums")
            TRACK -> holder.bindTrack(trackList[getTrackIndexByPosition(position)])
            ARTIST -> holder.bindArtist(artistList[getArtistIndexByPosition(position)])
            ALBUM -> holder.bindAlbum(albumList[getAlbumIndexByPosition(position)])
        }
    }

    override fun getItemCount(): Int {
        var headerCount = 0
        if (trackList.size > 0) {
            headerCount += 1
        }
        if (trackList.size > headerChildCount) {
            headerCount += 1
        }
        if (artistList.size > 0) {
            headerCount += 1
        }
        if (artistList.size > headerChildCount) {
            headerCount += 1
        }
        if (albumList.size > 0) {
            headerCount += 1
        }
        if (albumList.size > headerChildCount) {
            headerCount += 1
        }

        return headerCount +
                min(trackList.size, headerChildCount) +
                min(artistList.size, headerChildCount) +
                min(albumList.size, headerChildCount)
    }

    override fun getItemViewType(position: Int): Int {
        val itemTypeList = getCurrentViewTypeList()

        return itemTypeList[position].ordinal
    }

    private fun getCurrentViewTypeList(): List<SearchViewType> {
        val numberOfTracks = min(trackList.size, headerChildCount)
        val numberOfArtists = min(artistList.size, headerChildCount)
        val numberOfAlbums = min(albumList.size, headerChildCount)

        val isTrackHeaderVisible = trackList.size > 0
        val isArtistHeaderVisible = artistList.size > 0
        val isAlbumHeaderVisible = albumList.size > 0

        val isAllTrackHeaderVisible = trackList.size > headerChildCount
        val isAllArtistHeaderVisible = artistList.size > headerChildCount
        val isAllAlbumHeaderVisible = albumList.size > headerChildCount

        val itemTypeList = mutableListOf<SearchViewType>()
        if (isTrackHeaderVisible) {
            itemTypeList.add(TRACK_HEADER)
            for (i in 0 until numberOfTracks) {
                itemTypeList.add(TRACK)
            }
            if (isAllTrackHeaderVisible) {
                itemTypeList.add(ALL_TRACKS_HEADER)
            }
        }
        if (isArtistHeaderVisible) {
            itemTypeList.add(ARTIST_HEADER)
            for (i in 0 until numberOfArtists) {
                itemTypeList.add(ARTIST)
            }
            if (isAllArtistHeaderVisible) {
                itemTypeList.add(ALL_ARTIST_HEADER)
            }
        }
        if (isAlbumHeaderVisible) {
            itemTypeList.add(ALBUM_HEADER)
            for (i in 0 until numberOfAlbums) {
                itemTypeList.add(ALBUM)
            }
            if (isAllAlbumHeaderVisible) {
                itemTypeList.add(ALL_ALBUM_HEADER)
            }
        }
        return itemTypeList.toList()
    }

    private fun getTrackIndexByPosition(position: Int) : Int {
        val numberOfTracks = min(trackList.size, headerChildCount)

        val isTrackHeaderVisible = trackList.size > 0

        return if (isTrackHeaderVisible && position > 0 && position <= numberOfTracks) {
            position - 1
        } else {
            -1
        }
    }

    private fun getArtistIndexByPosition(position: Int) : Int {
        val numberOfTracks = min(trackList.size, headerChildCount)
        val numberOfArtists = min(artistList.size, headerChildCount)

        val isTrackHeaderVisible = trackList.size > 0
        val isArtistHeaderVisible = artistList.size > 0

        val isAllTrackHeaderVisible = trackList.size > headerChildCount

        var countBeforeArtists = numberOfTracks
        if (isTrackHeaderVisible) countBeforeArtists += 1
        if (isAllTrackHeaderVisible) countBeforeArtists += 1
        if (isArtistHeaderVisible) countBeforeArtists += 1

        return if (isArtistHeaderVisible && position >= countBeforeArtists && position < countBeforeArtists + numberOfArtists ) {
            position - countBeforeArtists
        } else {
            -1
        }
    }

    private fun getAlbumIndexByPosition(position: Int) : Int {
        val numberOfTracks = min(trackList.size, headerChildCount)
        val numberOfArtists = min(artistList.size, headerChildCount)
        val numberOfAlbums = min(albumList.size, headerChildCount)

        val isTrackHeaderVisible = trackList.size > 0
        val isArtistHeaderVisible = artistList.size > 0
        val isAlbumHeaderVisible = albumList.size > 0

        val isAllTrackHeaderVisible = trackList.size > headerChildCount
        val isAllArtistHeaderVisible = artistList.size > headerChildCount

        var countBeforeAlbums = numberOfTracks + numberOfArtists
        if (isTrackHeaderVisible) countBeforeAlbums += 1
        if (isAllTrackHeaderVisible) countBeforeAlbums += 1
        if (isArtistHeaderVisible) countBeforeAlbums += 1
        if (isAllArtistHeaderVisible) countBeforeAlbums += 1
        if (isAlbumHeaderVisible) countBeforeAlbums += 1

        return if (isAlbumHeaderVisible && position >= countBeforeAlbums && position < countBeforeAlbums + numberOfAlbums ) {
            position - countBeforeAlbums
        } else {
            -1
        }
    }

    private enum class SearchViewType {
        TRACK_HEADER,
        ARTIST_HEADER,
        ALBUM_HEADER,
        ALL_TRACKS_HEADER,
        ALL_ARTIST_HEADER,
        ALL_ALBUM_HEADER,
        TRACK,
        ARTIST,
        ALBUM
    }
}

class SearchResultsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    lateinit var uri: String

    fun bindTrack(track: Track) {
        uri = track.uri
        itemView.trackName.text = track.name
        itemView.artistName.text = DisplayUtils.getTrackArtistString(track)

        val imageUrl = if (track.album.images.isNotEmpty()) track.album.images[0].url else ""
        if (imageUrl.isEmpty()) {
            Glide.with(itemView).load(R.mipmap.black_record).into(itemView.trackArt)
        } else {
            Glide.with(itemView).load(imageUrl).apply(RequestOptions().placeholder(R.mipmap.black_record)).into(itemView.trackArt)
        }
    }

    fun bindArtist(artist: Artist) {
        uri = artist.uri
        itemView.artistName.text = artist.name

        val imageUrl = if (artist.images.isNotEmpty()) artist.images[0].url else ""
        if (imageUrl.isEmpty()) {
            Glide.with(itemView).load(R.mipmap.black_blank_person).into(itemView.artistImage)
        } else {
            Glide.with(itemView).load(imageUrl).apply(RequestOptions().placeholder(R.mipmap.black_blank_person)).into(itemView.artistImage)
        }
    }

    fun bindAlbum(album: AlbumSimple) {
        uri = album.uri
        itemView.albumName.text = album.name
        itemView.artistName.text = DisplayUtils.getAlbumArtistString(album)

        val imageUrl = if (album.images.isNotEmpty()) album.images[0].url else ""
        if (imageUrl.isEmpty()) {
            Glide.with(itemView).load(R.mipmap.black_record).into(itemView.albumArt)
        } else {
            Glide.with(itemView).load(imageUrl).apply(RequestOptions().placeholder(R.mipmap.black_record)).into(itemView.albumArt)
        }
    }

    fun bindHeader(headerText: String) {
        uri = headerText
        itemView.headerText.text = headerText
    }

    fun bindViewAllButton(viewAllText: String) {
        uri = viewAllText
        itemView.viewAllButton.text = viewAllText
    }
}