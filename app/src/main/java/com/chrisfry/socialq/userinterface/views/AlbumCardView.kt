package com.chrisfry.socialq.userinterface.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.interfaces.ISpotifySelectionListener

class AlbumCardView : CardView, View.OnClickListener {
    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet, defStyle: Int) : super(context, attributeSet, defStyle)


    private val image: ImageView
    private val name: TextView
    private val artistName: TextView

    lateinit var listener: ISpotifySelectionListener
    lateinit var uri: String


    init {
        LayoutInflater.from(context).inflate(R.layout.album_card_view, this, true)

        image = this.findViewById(R.id.iv_album_image)
        name = this.findViewById(R.id.tv_album_name)
        artistName = this.findViewById(R.id.tv_artist_name)

        setOnClickListener(this)
    }

    fun setName(name: String) {
        this.name.text = name
    }

    fun setArtistName(name: String) {
        artistName.text = name
        artistName.visibility = View.VISIBLE
    }

    fun setImageUrl(url: String) {
        if (url.isEmpty()) {
            Glide.with(image).load(R.mipmap.black_record).into(image)
        } else {
            Glide.with(image).load(url).apply(RequestOptions().placeholder(R.mipmap.black_record)).into(image)
        }
    }

    override fun onClick(v: View?) {
        listener.onSelection(uri)
    }

}