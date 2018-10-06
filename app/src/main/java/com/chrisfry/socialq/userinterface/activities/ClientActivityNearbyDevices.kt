package com.chrisfry.socialq.userinterface.activities

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import com.chrisfry.socialq.business.AppConstants
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import java.lang.Exception

class ClientActivityNearbyDevices : ClientActivity() {
    private val TAG = ClientActivityNearbyDevices::class.java.name

    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            AlertDialog.Builder(this@ClientActivityNearbyDevices)
                    .setTitle("Accept connection to " + connectionInfo.endpointName)
                    .setMessage("Confirm the code " + connectionInfo.authenticationToken)
                    .setPositiveButton("Accept", object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            // User confirmed, accept connection
                            Nearby.getConnectionsClient(this@ClientActivityNearbyDevices).acceptConnection(endpointId, mPayloadCallback)
                        }
                    })
                    .setNegativeButton("Reject", object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            // User rejected, reject connection
                            Nearby.getConnectionsClient(this@ClientActivityNearbyDevices).rejectConnection(endpointId)
                        }
                    })
                    .show()
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
            Log.d(TAG, "Client disconnected from the host")
            finish()
        }
    }

    private val mPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onPayloadTransferUpdate(endpointId: String, payloadTransferUpdate: PayloadTransferUpdate) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val endpointId = intent.extras.getString(AppConstants.ND_ENDPOINT_ID_EXTRA_KEY)
        if (endpointId == null) {
            Log.d(TAG, "Have no endpoint ID for host connection, can't connect")
            finish()
        } else {
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

        val endpointId = intent.extras.getString(AppConstants.ND_ENDPOINT_ID_EXTRA_KEY)
        if (endpointId.isNotEmpty()) {
            Nearby.getConnectionsClient(this).disconnectFromEndpoint(endpointId)
        }
    }

    override fun sendTrackToHost(trackUri: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}