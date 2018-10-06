package com.chrisfry.socialq.userinterface.activities

import android.app.AlertDialog
import android.content.DialogInterface
import android.util.Log
import android.widget.Toast
import com.chrisfry.socialq.business.AppConstants
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import java.lang.Exception

class HostActivityNearbyDevices : HostActivity() {
    private val TAG = HostActivityNearbyDevices::class.java.name

    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            AlertDialog.Builder(this@HostActivityNearbyDevices)
                    .setTitle("Accept connection to " + connectionInfo.endpointName)
                    .setMessage("Confirm the code " + connectionInfo.authenticationToken)
                    .setPositiveButton("Accept", object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            // User confirmed, accept connection
                            Nearby.getConnectionsClient(this@HostActivityNearbyDevices).acceptConnection(endpointId, mPayloadCallback)
                        }
                    })
                    .setNegativeButton("Reject", object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            // User rejected, reject connection
                            Nearby.getConnectionsClient(this@HostActivityNearbyDevices).rejectConnection(endpointId)
                        }
                    })
                    .show()
        }

        override fun onConnectionResult(endPoint: String, connectionResolution: ConnectionResolution) {
            when (connectionResolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Established connection with a client")
                    Toast.makeText(this@HostActivityNearbyDevices, "Client has joined!", Toast.LENGTH_SHORT).show()
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> Log.d(TAG, "Client connection rejected")
                ConnectionsStatusCodes.STATUS_ERROR -> Log.d(TAG, "Error during client connection")
                else -> TODO("not implemented")
            }
        }

        override fun onDisconnected(endPoint: String) {
            Toast.makeText(this@HostActivityNearbyDevices, "Client has disconnected!", Toast.LENGTH_SHORT).show()
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

    override fun startHostConnection() {
        // Create advertising options (strategy)
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()

        Nearby.getConnectionsClient(this).startAdvertising(
                "SocialQ Host",
                AppConstants.SERVICE_NAME,
                mConnectionLifecycleCallback,
                options)
                .addOnSuccessListener(object : OnSuccessListener<Void> {
                    override fun onSuccess(unusedResult: Void?) {
                        Log.d(TAG, "Successfully advertising the host")
                    }
                })
                .addOnFailureListener(object : OnFailureListener {
                    override fun onFailure(p0: Exception) {
                        Log.d(TAG, "Failed to start advertising the host")
                        TODO("Handle advertising failure")
                    }
                })
    }

    override fun onDestroy() {
        super.onDestroy()

        Nearby.getConnectionsClient(this).stopAdvertising()
        Nearby.getConnectionsClient(this).stopAllEndpoints()
    }
}