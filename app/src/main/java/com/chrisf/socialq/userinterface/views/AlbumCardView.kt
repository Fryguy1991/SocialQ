package com.chrisf.socialq.userinterface.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.chrisf.socialq.R
import com.chrisf.socialq.model.spotify.AlbumSimple
import com.chrisf.socialq.userinterface.interfaces.ISpotifySelectionListener
import kotlinx.android.synthetic.main.view_album_card_view.view.*

class AlbumCardView : CardView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet, defStyle: Int) : super(context, attributeSet, defStyle)

    lateinit var listener: ISpotifySelectionListener
    lateinit var uri: String


    init {
        LayoutInflater.from(context).inflate(R.layout.view_album_card_view, this, true)
    }

    fun bind(album: AlbumSimple) {
            Glide.with(this)
                    .setDefaultRequestOptions(RequestOptions.placeholderOf(R.mipmap.black_record))
                    .load(album.images[0].url)
                    .into(albumArt)

    }

}