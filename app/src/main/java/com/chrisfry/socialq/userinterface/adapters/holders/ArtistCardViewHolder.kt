package com.chrisfry.socialq.userinterface.adapters.holders

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chrisfry.socialq.R

class ArtistCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val image = itemView.findViewById<ImageView>(R.id.iv_image)
    private val name = itemView.findViewById<TextView>(R.id.tv_name)

    fun setName(name: String) {
        this.name.text = name
    }

    fun setImageUrl(url: String) {
        Glide.with(image).load(url).into(image)
    }
}