package com.chrisf.socialq.userinterface.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import com.chrisf.socialq.userinterface.interfaces.ISpotifySelectionListener

class TextViewWithUri : TextView, View.OnClickListener {
    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet, defStyle: Int) : super(context, attributeSet, defStyle)

    lateinit var uri: String
    lateinit var listener: ISpotifySelectionListener

    init {
        setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        listener.onSelection(uri)
    }
}