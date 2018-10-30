package com.chrisfry.socialq.userinterface.adapters.holders

import android.view.View
import android.widget.TextView
import com.chrisfry.socialq.R

class HostTrackHolder(view : View) : BasicTrackHolder(view) {
    private val clientNameView = view.findViewById<TextView>(R.id.tv_client_name)

    fun setClientName(name : String) {
        clientNameView.text = name
    }
}