package com.chrisfry.socialq.userinterface.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.interfaces.ISpotifySelectionListener

class TrackAlbumView : ConstraintLayout, View.OnClickListener {
    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet, defStyle: Int) : super(context, attributeSet, defStyle)

    private var albumImage: ImageView
    private var name: TextView
    private var artistName: TextView

    lateinit var listener: ISpotifySelectionListener
    lateinit var uri: String

    init {
        LayoutInflater.from(context).inflate(R.layout.base_track_album_layout, this)

        albumImage = findViewById(R.id.iv_album_image)
        name = findViewById(R.id.tv_name)
        artistName = findViewById(R.id.tv_artist)

        setOnClickListener(this)
    }

    fun setArtistImage(imageUrl: String) {
        Glide.with(context).load(imageUrl).into(albumImage)
    }

    fun setName(name: String) {
        this.name.text = name
    }

    fun setArtistName(name: String) {
        this.artistName.text = name
    }

    override fun onClick(v: View?) {
        listener.onSelection(uri)
    }
}