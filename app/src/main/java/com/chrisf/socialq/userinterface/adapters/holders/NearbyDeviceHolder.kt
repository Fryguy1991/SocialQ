package com.chrisf.socialq.userinterface.adapters.holders

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.R

class NearbyDeviceHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener{
    lateinit var endpointId: String
    lateinit var listener: ItemSelectionListenerNearbyDevices
    lateinit var nameTextView: TextView

    override fun onClick(view: View?) {
        if (view != null) {
            if (view.contentDescription == "not_selected") {
                view.contentDescription = "selected"
                view.setBackgroundColor(view.resources.getColor(R.color.Active_Button_Color))
                nameTextView.setTextColor(view.resources.getColor(R.color.White))
                listener.onItemSelected(endpointId, nameTextView.text.toString())
            } else {
                view.contentDescription = "not_selected"
                view.setBackgroundColor(view.resources.getColor(R.color.White))
                nameTextView.setTextColor(view.resources.getColor(R.color.Gray))
                listener.onItemSelected("", "")
            }
        }
    }

    fun setupHolder(name: String, endpointId: String, listener: ItemSelectionListenerNearbyDevices) {
        this.endpointId = endpointId
        this.listener = listener

        val parentView = itemView.findViewById<View>(R.id.ll_device_holder)
        parentView.setOnClickListener(this)

        nameTextView = itemView.findViewById<TextView>(R.id.tv_device_name)
        nameTextView.text = name
    }

    interface ItemSelectionListenerNearbyDevices {
        fun onItemSelected(endpointId: String, endpointName: String)
    }
}