package com.chrisfry.socialq.userinterface.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.model.NearbyDeviceData
import com.chrisfry.socialq.userinterface.adapters.NearbyDevicesAdapter
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import java.lang.Exception

class QueueConnectActivityNearbyDevices : QueueConnectActivity(), NearbyDevicesAdapter.DeviceSelectionListener {
    private val TAG = QueueConnectActivityNearbyDevices::class.java.name
    private lateinit var queueToJoinEndpointId: String
    private lateinit var queueToJoinName: String
    private var mNearbyDevices: MutableList<NearbyDeviceData> = mutableListOf()
    private lateinit var mNearbyDevicesAdapter: NearbyDevicesAdapter

    val mEndpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, discoveredEndpointInfo: DiscoveredEndpointInfo) {
            if (discoveredEndpointInfo.serviceId == AppConstants.SERVICE_NAME) {
                Log.d(TAG, "Found a SocialQ host")
                mNearbyDevices.add(NearbyDeviceData(discoveredEndpointInfo.endpointName, endpointId))
                mNearbyDevicesAdapter.updateDeviceList(mNearbyDevices)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            for (device: NearbyDeviceData in mNearbyDevices) {
                if (device.endpointId == endpointId) {
                    mNearbyDevices.remove(device)
                }
            }
            mNearbyDevicesAdapter.updateDeviceList(mNearbyDevices)
        }
    }

    override fun setupAdapter(recyclerView: RecyclerView?) {
        if (recyclerView != null) {
            mNearbyDevicesAdapter = NearbyDevicesAdapter(this)
            recyclerView.adapter = mNearbyDevicesAdapter
        }
    }

    override fun searchForQueues() {
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()

        Nearby.getConnectionsClient(this).startDiscovery(AppConstants.SERVICE_NAME, mEndpointDiscoveryCallback, options)
                .addOnSuccessListener(object: OnSuccessListener<Void> {
                    override fun onSuccess(p0: Void?) {
                        Log.d(TAG, "Successfully started discovering SocialQ Hosts")

                    }

                })
                .addOnFailureListener(object: OnFailureListener {
                    override fun onFailure(p0: Exception) {
                        Log.e(TAG, "Failed to start device discovery for SocialQ Hosts")
                        Log.e(TAG, p0.message)
                    }
                })
    }

    override fun connectToQueue() {
        Nearby.getConnectionsClient(this).stopDiscovery()
        if (queueToJoinEndpointId.isNotEmpty()) {
            val clientIntent = Intent(this, ClientActivityKotlin::class.java)
            clientIntent.putExtra(AppConstants.ND_ENDPOINT_ID_EXTRA_KEY, queueToJoinEndpointId)
            clientIntent.putExtra(AppConstants.QUEUE_TITLE_KEY, queueToJoinName)
            startActivity(clientIntent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        searchForQueues()
    }

    override fun onPause() {
        super.onPause()

        Nearby.getConnectionsClient(this).stopDiscovery()
    }

    override fun onDeviceSelected(endpointId: String, endpointName: String) {
        this.queueToJoinEndpointId = endpointId
        this.queueToJoinName = endpointName
        mQueueJoinButton.isEnabled = endpointId.isNotEmpty()
    }
}