package com.chrisf.socialq.userinterface.adapters.holders

import android.view.View
import android.widget.TextView
import com.chrisf.socialq.R

class HostTrackHolder(view : View) : BasicTrackAlbumHolder(view) {
    private val clientNameView = view.findViewById<TextView>(R.id.tv_client_name)

    fun setClientName(name : String) {
        clientNameView.text = name
    }
}