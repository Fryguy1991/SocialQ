package com.chrisf.socialq.userinterface.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.cardview.widget.CardView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.chrisf.socialq.R
import com.chrisf.socialq.model.spotify.AlbumSimple
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import kotlinx.android.synthetic.main.view_album_card_view.view.*
import org.jetbrains.anko.dip

class AlbumCardView : CardView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet, defStyle: Int) : super(context, attributeSet, defStyle)

    lateinit var uri: String

    private val clickRelay: PublishRelay<AlbumClick> = PublishRelay.create()
    val clicks: Observable<AlbumClick> = clickRelay.hide()

    init {
        LayoutInflater.from(context).inflate(R.layout.view_album_card_view, this, true)
        radius = dip(6).toFloat()
    }

    fun bind(album: AlbumSimple) {
        Glide.with(this)
                .setDefaultRequestOptions(RequestOptions.placeholderOf(R.mipmap.black_record))
                .load(album.images[0].url)
                .into(albumArt)

        albumName.text = album.name

        this.setOnClickListener { clickRelay.accept(AlbumClick(album.id)) }
    }

    data class AlbumClick(val albumId: String)
}