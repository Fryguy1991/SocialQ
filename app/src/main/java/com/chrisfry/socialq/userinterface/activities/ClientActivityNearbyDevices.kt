package com.chrisfry.socialq.userinterface.activities

import android.util.Log
import android.widget.Toast
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.NearbyDevicesMessage
import com.chrisfry.socialq.enums.PayloadTransferUpdateStatus
import com.chrisfry.socialq.utils.ApplicationUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import java.lang.Exception
import java.lang.NumberFormatException

class ClientActivityNearbyDevices : ClientActivity() {
    private val TAG = ClientActivityNearbyDevices::class.java.name

    // Flag to indicate if the user has initiated the disconnect
    private var userDisconnect = false

    // Variable to hold host endpoint
    private lateinit var mHostEndpointId: String
    // Flag indicating success of connection to host (used during activity destruction)
    private var mSuccessfulConnectionFlag = false

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
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connection to host successful!")
                    mSuccessfulConnectionFlag = true
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d(TAG, "Connection to host rejected")
                    finish()
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.d(TAG, "Error connecting to host")
                    finish()
                }
                else -> TODO("not implemented")
            }
        }

        override fun onDisconnected(endPoint: String) {
            Log.d(TAG, "Host disconnected from the client")

            // If host has disconnected from the client.  Allow the client to follow the playlist
            // If client disconnected from the host they will have already picked if they wanted to
            // follow the playlist
            if (!userDisconnect) {
                showHostDisconnectedFollowPlaylistDialog()
            }
        }
    }

    private val mPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.e(TAG, "Client received a payload")
            when (payload.type) {
                Payload.Type.BYTES -> handleHostPayload(payload)
                Payload.Type.STREAM, Payload.Type.FILE -> TODO("not implemented")
            }
        }

        private fun handleHostPayload(payload: Payload) {
            if (payload.asBytes() != null) {
                val payloadString = String(payload.asBytes()!!)
                val payloadType = ApplicationUtils.getMessageTypeFromPayload(payloadString)

                Log.d(TAG, payloadType.toString() + " payload received from host")

                when (payloadType) {
                    NearbyDevicesMessage.RECEIVE_HOST_USER_ID -> {
                        // Host is giving us the playlist owner ID
                        mHostUserId = ApplicationUtils.getBasicPayloadDataFromPayloadString(payloadType.payloadPrefix, payloadString)
                    }
                    NearbyDevicesMessage.RECEIVE_PLAYLIST_ID -> {
                        // Host is giving us the playlist ID
                        setupQueuePlaylistOnConnection(ApplicationUtils.getBasicPayloadDataFromPayloadString(payloadType.payloadPrefix, payloadString))
                    }
                    NearbyDevicesMessage.QUEUE_UPDATE -> {
                        // Host is notifying us that the queue has been updated
                        val currentPlayingIndex = ApplicationUtils.getBasicPayloadDataFromPayloadString(payloadType.payloadPrefix, payloadString)
                        try {
                            updateQueue(Integer.parseInt(currentPlayingIndex))
                        } catch (exception: NumberFormatException) {
                            Log.e(TAG, "Invalid index was sent")
                        }
                    }
                    NearbyDevicesMessage.SONG_REQUEST -> {
                        // Should not receive this case as the client
                        Log.e(TAG, "Clients should not receive song request messages")
                    }
                    NearbyDevicesMessage.INVALID -> {
                        // TODO currently not handling this case
                    }
                    else -> {
                        // TODO currently not handling null case
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, payloadTransferUpdate: PayloadTransferUpdate) {
            val status = PayloadTransferUpdateStatus.getStatusFromConstant(payloadTransferUpdate.status)
            Log.d(TAG, "Payload Transfer to/from $endpointId has status $status")
        }

    }

    override fun onDestroy() {
        super.onDestroy()

        if (mSuccessfulConnectionFlag) {
            Nearby.getConnectionsClient(this).stopAllEndpoints()
        }
    }

    override fun sendTrackToHost(requestMessage: String) {
        if (requestMessage != null) {
            Log.d(TAG, "Sending track request message to host: $requestMessage")
            Nearby.getConnectionsClient(this).sendPayload(mHostEndpointId, Payload.fromBytes(requestMessage.toByteArray()))
        }
    }

    override fun connectToHost() {
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
                            Toast.makeText(this@ClientActivityNearbyDevices, getString(R.string.toast_host_connection_error), Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    })
        }
    }

    override fun disconnectClient() {
        userDisconnect = true
        Nearby.getConnectionsClient(this).disconnectFromEndpoint(mHostEndpointId)
    }
}