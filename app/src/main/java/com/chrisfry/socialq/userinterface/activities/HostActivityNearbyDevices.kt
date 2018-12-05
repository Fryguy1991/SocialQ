package com.chrisfry.socialq.userinterface.activities

import android.util.Log
import android.widget.Toast
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.NearbyDevicesMessage
import com.chrisfry.socialq.enums.PayloadTransferUpdateStatus
import com.chrisfry.socialq.model.SongRequestData
import com.chrisfry.socialq.utils.ApplicationUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import kaaes.spotify.webapi.android.models.UserPublic
import java.lang.Exception
import java.util.regex.Pattern

class HostActivityNearbyDevices : HostActivityKotlin() {
    private val TAG = HostActivityNearbyDevices::class.java.name

    // List of the client enpoints that are currently connected to the host service
    private var mClientEndpoints = ArrayList<String>()
    // Flag indicating success of advertising (used during activity destruction)
    private var mSuccessfulAdvertisingFlag = false

    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // TODO: Consider removing code below.  This is a dialog for authenticating client connection.
//            AlertDialog.Builder(this@HostActivityNearbyDevices)
//                    .setTitle("Accept connection to " + connectionInfo.endpointName)
//                    .setMessage("Confirm the code " + connectionInfo.authenticationToken)
//                    .setPositiveButton("Accept", object : DialogInterface.OnClickListener {
//                        override fun onClick(dialog: DialogInterface?, which: Int) {
//                            // User confirmed, accept connection
//                            Nearby.getConnectionsClient(this@HostActivityNearbyDevices).acceptConnection(endpointId, mPayloadCallback)
//                        }
//                    })
//                    .setNegativeButton("Reject", object : DialogInterface.OnClickListener {
//                        override fun onClick(dialog: DialogInterface?, which: Int) {
//                            // User rejected, reject connection
//                            Nearby.getConnectionsClient(this@HostActivityNearbyDevices).rejectConnection(endpointId)
//                        }
//                    })
//                    .show

            // Uncomment code above to force connection verification
            Nearby.getConnectionsClient(this@HostActivityNearbyDevices).acceptConnection(endpointId, mPayloadCallback)
        }

        override fun onConnectionResult(endPoint: String, connectionResolution: ConnectionResolution) {
            when (connectionResolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Established connection with a client, send queue")
                    Toast.makeText(this@HostActivityNearbyDevices, "Client has joined!", Toast.LENGTH_SHORT).show()
                    mClientEndpoints.add(endPoint)
                    initiateNewClient(endPoint)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> Log.d(TAG, "Client connection rejected")
                ConnectionsStatusCodes.STATUS_ERROR -> Log.d(TAG, "Error during client connection")
                else -> TODO("not implemented")
            }
        }

        override fun onDisconnected(endPoint: String) {
            Toast.makeText(this@HostActivityNearbyDevices, "Client has disconnected!", Toast.LENGTH_SHORT).show()
            mClientEndpoints.remove(endPoint)
        }
    }

    private val mPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d(TAG, "Host received a payload")
            when (payload.type) {
                Payload.Type.BYTES -> handleClientPayload(payload)
                Payload.Type.FILE, Payload.Type.STREAM -> Log.e(TAG, "Currently not handling  streams or files")
            }
        }

        private fun handleClientPayload(payload: Payload) {
            if (payload.asBytes() != null) {
                val payloadString = String(payload.asBytes()!!)
                val payloadType = ApplicationUtils.getMessageTypeFromPayload(payloadString)

                Log.d(TAG, "Payload is type $payloadType")

                when (payloadType) {
                    NearbyDevicesMessage.SONG_REQUEST -> {
                        val songRequest = getSongRequestFromPayload(payloadString)
                        if (songRequest.uri.isNotEmpty()) {
                            handleSongRequest(songRequest)
                        } else {
                            Log.e(TAG, "Error retrieving data from client song request")
                        }
                    }
                    NearbyDevicesMessage.QUEUE_UPDATE, NearbyDevicesMessage.INITIATE_CLIENT -> {
                        Log.e(TAG, "Hosts should not receive queue update or initiate client messages")
                    }
                    NearbyDevicesMessage.INVALID -> {
                        Log.e(TAG, "Invalid payload was sent to host")
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

    private fun getSongRequestFromPayload(payloadString: String): SongRequestData {
        var pattern = Pattern.compile(AppConstants.FULL_SONG_REQUEST_REGEX)
        var matcher = pattern.matcher(payloadString)
        // Ensure a proper format has been sent for the track request
        if (matcher.find()) {
            // Extract track URI and client ID
            val songUri = matcher.group(1)
            val clientId = matcher.group(2)

            return SongRequestData(songUri, mSpotifyService.getUser(clientId))
        }
        return SongRequestData("", UserPublic())
    }

    override fun startHostConnection(queueTitle: String) {
        // Create advertising options (strategy)
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()

        Nearby.getConnectionsClient(this).startAdvertising(
                queueTitle,
                AppConstants.SERVICE_NAME,
                mConnectionLifecycleCallback,
                options)
                .addOnSuccessListener(object : OnSuccessListener<Void> {
                    override fun onSuccess(unusedResult: Void?) {
                        Log.d(TAG, "Successfully advertising the host")
                        mSuccessfulAdvertisingFlag = true
                    }
                })
                .addOnFailureListener(object : OnFailureListener {
                    override fun onFailure(p0: Exception) {
                        Log.d(TAG, "Failed to start advertising the host")
                        finish()
                    }
                })
    }

    override fun notifyClientsQueueUpdated(currentPlayingIndex: Int) {
        if (currentPlayingIndex >= 0) {
            for (endpointId: String in mClientEndpoints) {
                Nearby.getConnectionsClient(this).sendPayload(endpointId,
                        Payload.fromBytes(String.format(AppConstants.UPDATE_QUEUE_MESSAGE,
                                currentPlayingIndex.toString()).toByteArray()))
            }
        }
    }

    override fun initiateNewClient(client: Any) {
        if (mClientEndpoints.contains(client.toString()) && mPlaylist != null && mPlaylist!!.id != null && mCurrentUser != null && mCurrentUser!!.id != null) {
            Log.d(TAG, "Sending host ID, playlist id, and current playing index to new client")
            Nearby.getConnectionsClient(this).sendPayload(client.toString(), Payload.fromBytes(
                    String.format(AppConstants.INITIATE_CLIENT_MESSAGE_FORMAT,
                            mCurrentUser!!.id,
                            mPlaylist!!.id,
                            mCachedPlayingIndex).toByteArray()))
        }
    }

    override fun onDestroy() {
        if (mSuccessfulAdvertisingFlag) {
            Nearby.getConnectionsClient(this).stopAdvertising()
            Nearby.getConnectionsClient(this).stopAllEndpoints()
        }

        super.onDestroy()
    }
}