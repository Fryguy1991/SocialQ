package com.chrisfry.socialq.userinterface.adapters

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.model.NearbyDeviceData
import com.chrisfry.socialq.userinterface.adapters.holders.NearbyDeviceHolder

class NearbyDevicesAdapter() : RecyclerView.Adapter<NearbyDeviceHolder>(), NearbyDeviceHolder.ItemSelectionListenerNearbyDevices {
    lateinit var mDeviceSelectionListener: DeviceSelectionListener
    private var mNearbyDevices: List<NearbyDeviceData> = ArrayList<NearbyDeviceData>()

    constructor(listener: DeviceSelectionListener) : this() {
        mDeviceSelectionListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NearbyDeviceHolder {
        return NearbyDeviceHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.queue_list_holder, parent, false))
    }

    override fun getItemCount(): Int {
        return mNearbyDevices.size
    }

    override fun onBindViewHolder(holder: NearbyDeviceHolder, position: Int) {
        holder.setupHolder(mNearbyDevices.get(position).name, mNearbyDevices[position].endpointId, this)
    }

    fun updateDeviceList(devices: List<NearbyDeviceData>) {
        mNearbyDevices = devices
        notifyDataSetChanged()
    }

    override fun onItemSelected(endpointId: String) {
        mDeviceSelectionListener.onDeviceSelected(endpointId)
    }

    interface DeviceSelectionListener {
        fun onDeviceSelected(endpointId: String)
    }
}