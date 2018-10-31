package com.chrisfry.socialq.userinterface.adapters

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup

/**
 * Base recycle view adapter class with generics for displaying a list of items
 * <T> is the view holder type
 * <E> is the item list type
 */
abstract class BaseRecyclerViewAdapter<T : RecyclerView.ViewHolder, E> : RecyclerView.Adapter<T>() {
    protected var itemList = listOf<E>()


    override fun getItemCount(): Int {
        return itemList.size
    }

    fun updateAdapter(newItemList : MutableList<E>) {
        itemList = newItemList
        notifyDataSetChanged()
    }

    abstract override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): T

    abstract override fun onBindViewHolder(holder: T, position: Int)
}