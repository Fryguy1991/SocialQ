package com.chrisfry.socialq.userinterface.adapters.holders

import android.support.v7.widget.AppCompatImageView
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.TextView
import com.bumptech.glide.Glide
import com.chrisfry.socialq.R

class BasicArtistHolder(view : View) : RecyclerView.ViewHolder(view) {
    private val artistName = view.findViewById<TextView>(R.id.tv_artist_name)
    private val artistImage = view.findViewById<AppCompatImageView>(R.id.iv_artist_image)

    fun setArtistName(name : String) {
        artistName.text = name
    }

    fun setArtistImage(imageUrl : String) {
        Glide.with(itemView).load(imageUrl).into(artistImage)
    }
}