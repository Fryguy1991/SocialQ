package com.chrisfry.socialq.userinterface.adapters.holders

import android.view.Gravity
import android.view.View
import com.chrisfry.socialq.userinterface.interfaces.ISpotifySelectionListener
import com.chrisfry.socialq.utils.DisplayUtils

class SelectableAlbumTrackHolder(itemView: View) : SimpleTextHolder(itemView), View.OnClickListener {
    lateinit var uri: String
    lateinit var listener: ISpotifySelectionListener

    init {
        textView.height = DisplayUtils.convertDpToPixels(textView.context,30)
        textView.gravity = Gravity.CENTER_VERTICAL

        itemView.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        listener.onSelection(uri)
    }
}