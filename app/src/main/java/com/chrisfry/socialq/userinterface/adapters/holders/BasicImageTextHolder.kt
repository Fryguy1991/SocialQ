package com.chrisfry.socialq.userinterface.adapters.holders

import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.chrisfry.socialq.R

/**
 * Very basic holder for an image and text (name)
 */
open class BasicImageTextHolder(view : View) : RecyclerView.ViewHolder(view) {
    private val name = view.findViewById<TextView>(R.id.tv_item_name)
    private val image = view.findViewById<ImageView>(R.id.iv_item_image)

    fun setDisplayName(name : String) {
        this.name.text = name
    }

    fun setImage(imageUrl : String) {
        Glide.with(itemView).load(imageUrl).into(image)
    }
}