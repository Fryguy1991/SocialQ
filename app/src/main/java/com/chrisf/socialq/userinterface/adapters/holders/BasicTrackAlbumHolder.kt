package com.chrisf.socialq.userinterface.adapters.holders

import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.chrisf.socialq.R

open class BasicTrackAlbumHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val trackNameView = view.findViewById<TextView>(R.id.tv_name)
    private val artistNameView = view.findViewById<TextView>(R.id.tv_artist_name)
    private val albumImageView = view.findViewById<ImageView>(R.id.iv_album_image)

    fun setArtistName(name: String) {
        artistNameView.text = name
    }

    fun setName(name: String) {
        trackNameView.text = name
    }

    fun setAlbumImage(url: String) {
        if (url.isEmpty()) {
            Glide.with(itemView).load(R.color.Transparent).into(albumImageView)
        } else {
            Glide.with(itemView).load(url).into(albumImageView)
        }
    }
}