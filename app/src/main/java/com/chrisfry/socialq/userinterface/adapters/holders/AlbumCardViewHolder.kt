package com.chrisfry.socialq.userinterface.adapters.holders

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chrisfry.socialq.R

class AlbumCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
    private val albumImage = itemView.findViewById<ImageView>(R.id.iv_album_image)
    private val albumNameText = itemView.findViewById<TextView>(R.id.tv_album_name)
    private val artistNameText = itemView.findViewById<TextView>(R.id.tv_artist_name)

    fun setImageUrl(url: String) {
        Glide.with(albumImage).load(url).into(albumImage)
    }

    fun setAlbumName(name: String) {
        albumNameText.text = name
    }

    fun setArtistName(name: String) {
        artistNameText.text = name
    }
}