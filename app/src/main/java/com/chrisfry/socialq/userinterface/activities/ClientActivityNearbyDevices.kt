package com.chrisfry.socialq.userinterface.activities

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.utils.ApplicationUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import kaaes.spotify.webapi.android.models.Track
import java.lang.Exception

class ClientActivityNearbyDevices : ClientActivity() {
    private val TAG = ClientActivityNearbyDevices::class.java.name
    private lateinit var mHostEndpointId : String

    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
//            AlertDialog.Builder(this@ClientActivityNearbyDevices)
//                    .setTitle("Accept connection to " + connectionInfo.endpointName)
//                    .setMessage("Confirm the code " + connectionInfo.authenticationToken)
//                    .setPositiveButton("Accept", object : DialogInterface.OnClickListener {
//                        override fun onClick(dialog: DialogInterface?, which: Int) {
//                            // User confirmed, accept connection
//                            Nearby.getConnectionsClient(this@ClientActivityNearbyDevices).acceptConnection(endpointId, mPayloadCallback)
//                        }
//                    })
//                    .setNegativeButton("Reject", object : DialogInterface.OnClickListener {
//                        override fun onClick(dialog: DialogInterface?, which: Int) {
//                            // User rejected, reject connection
//                            Nearby.getConnectionsClient(this@ClientActivityNearbyDevices).rejectConnection(endpointId)
//                        }
//                    })
//                    .show()

            // Uncomment code above to force connection verification
            Nearby.getConnectionsClient(this@ClientActivityNearbyDevices).acceptConnection(endpointId, mPayloadCallback)
        }

        override fun onConnectionResult(endPoint: String, connectionResolution: ConnectionResolution) {
            when (connectionResolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> Log.d(TAG, "Connection to host successful!")
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> Log.d(TAG, "Connection to host rejected")
                ConnectionsStatusCodes.STATUS_ERROR -> Log.d(TAG, "Error connecting to host")
                else -> TODO("not implemented")
            }
        }

        override fun onDisconnected(endPoint: String) {
            Log.d(TAG, "Host disconnected from the client")
            Toast.makeText(this@ClientActivityNearbyDevices, getString(R.string.toast_host_disconnected), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val mPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val trackStringList = ApplicationUtils.convertQueueByteArrayToStringList(payload.asBytes())

            // Retrieve tracks for the list of track URIs
            var trackList = mutableListOf<Track>()
            if (trackStringList.size > 0) {
                trackList = MutableList<Track>(trackStringList.size) { index: Int -> mSpotifyService.getTrack(trackStringList.get(index)) }
            }
            updateQueue(trackList)
        }

        override fun onPayloadTransferUpdate(endpointId: String, payloadTransferUpdate: PayloadTransferUpdate) {
            // Do we care about status?
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val endpointId = intent.getStringExtra(AppConstants.ND_ENDPOINT_ID_EXTRA_KEY)
        if (endpointId == null) {
            Log.d(TAG, "Have no endpoint ID for host connection, can't connect")
            finish()
        } else {
            mHostEndpointId = endpointId
            Nearby.getConnectionsClient(this).requestConnection(
                    "SocialQ Client",
                    endpointId,
                    mConnectionLifecycleCallback)
                    .addOnSuccessListener(object : OnSuccessListener<Void> {
                        override fun onSuccess(p0: Void?) {
                            Log.d(TAG, "Successfully sent a connection request")
                        }
                    })
                    .addOnFailureListener(object : OnFailureListener {
                        override fun onFailure(p0: Exception) {
                            Log.d(TAG, "Failed to send a connection request, can't connect")
                        }
                    })
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        Nearby.getConnectionsClient(this).stopAllEndpoints()
    }

    override fun sendTrackToHost(trackUri: String?) {
        if (trackUri != null) {
            Nearby.getConnectionsClient(this).sendPayload(mHostEndpointId, Payload.fromBytes(trackUri.toByteArray()))
        }
    }
}