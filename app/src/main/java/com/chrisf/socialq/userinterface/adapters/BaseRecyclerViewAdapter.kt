package com.chrisf.socialq.userinterface.adapters

import android.view.ViewGroup

/**
 * Base recycle view adapter class with generics for displaying a list of items
 * <Viewholder> is the view holder type
 * <ItemType> is the item list type
 */
abstract class BaseRecyclerViewAdapter<ViewHolder : androidx.recyclerview.widget.RecyclerView.ViewHolder, ItemType> : androidx.recyclerview.widget.RecyclerView.Adapter<ViewHolder>() {
    protected var itemList = listOf<ItemType>()


    override fun getItemCount(): Int {
        return itemList.size
    }

    fun updateAdapter(newItemList : List<ItemType>) {
        itemList = newItemList
        notifyDataSetChanged()
    }

    abstract override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder

    abstract override fun onBindViewHolder(holder: ViewHolder, position: Int)
}