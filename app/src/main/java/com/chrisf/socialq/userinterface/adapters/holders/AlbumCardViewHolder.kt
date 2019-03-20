package com.chrisf.socialq.userinterface.adapters.holders

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.chrisf.socialq.R

open class AlbumCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
    private val albumImage = itemView.findViewById<ImageView>(R.id.iv_album_image)
    private val albumNameText = itemView.findViewById<TextView>(R.id.tv_album_name)
    private val artistNameText = itemView.findViewById<TextView>(R.id.tv_artist_name)

    fun setImageUrl(url: String) {
        if (url.isEmpty()) {
            Glide.with(albumImage).load(R.mipmap.black_record).into(albumImage)
        } else {
            Glide.with(albumImage).load(url).apply(RequestOptions().placeholder(R.mipmap.black_record)).into(albumImage)
        }
    }

    fun setAlbumName(name: String) {
        albumNameText.text = name
    }

    fun setArtistName(name: String) {
        artistNameText.text = name
        artistNameText.visibility = View.VISIBLE
    }
}