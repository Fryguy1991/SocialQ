package com.chrisfry.socialq.userinterface.adapters.holders

import android.view.View
import com.chrisfry.socialq.R

/**
 * Very basic holder for an image and text (name) that will respond with a set item ID when clicked
 */
class ClickableImageTextHolder(view : View): BasicImageTextHolder(view) {
    private val baseLayout = view.findViewById<View>(R.id.cl_item_holder)
    private lateinit var itemId : String
    private lateinit var listener : ItemSelectionListener

    init {
        baseLayout.setOnClickListener {listener.onItemSelected(itemId)}
    }

    fun setId(itemId: String) {
        this.itemId = itemId
    }

    // Logic for listening to when an artist is selected
    interface ItemSelectionListener {
        fun onItemSelected(itemId: String)
    }

    fun setItemSelectionListener(listener : ItemSelectionListener) {
        this.listener = listener
    }
}