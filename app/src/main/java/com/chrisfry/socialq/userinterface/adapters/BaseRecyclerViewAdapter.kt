package com.chrisfry.socialq.userinterface.adapters

import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup

/**
 * Base recycle view adapter class with generics for displaying a list of items
 * <T> is the view holder type
 * <E> is the item list type
 */
abstract class BaseRecyclerViewAdapter<T : androidx.recyclerview.widget.RecyclerView.ViewHolder, E> : androidx.recyclerview.widget.RecyclerView.Adapter<T>() {
    protected var itemList = listOf<E>()


    override fun getItemCount(): Int {
        return itemList.size
    }

    fun updateAdapter(newItemList : List<E>) {
        itemList = newItemList
        notifyDataSetChanged()
    }

    abstract override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): T

    abstract override fun onBindViewHolder(holder: T, position: Int)
}