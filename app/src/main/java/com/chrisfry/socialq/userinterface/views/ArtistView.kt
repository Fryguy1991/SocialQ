package com.chrisfry.socialq.userinterface.views

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

class ArtistView : ConstraintLayout, View.OnClickListener {
    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet, defStyle: Int) : super(context, attributeSet, defStyle)

    private var artistImage: ImageView
    private var artistName: TextView

    lateinit var artistUri: String
    lateinit var listener: ISpotifySelectionListener

    init {
        LayoutInflater.from(context).inflate(R.layout.base_artist_layout, this)

        artistImage = findViewById(R.id.iv_artist_image)
        artistName = findViewById(R.id.tv_name)

        setOnClickListener(this)
    }

    fun setArtistImage(imageUrl: String) {
        Glide.with(context).load(imageUrl).into(artistImage)
    }

    fun setArtistName(name: String) {
        artistName.text = name
    }

    override fun onClick(v: View?) {
        listener.onSelection(artistUri)
    }
}