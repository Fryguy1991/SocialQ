package com.chrisfry.socialq.userinterface.activities

import android.util.Log
import android.widget.Toast
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.NearbyDevicesMessage
import com.chrisfry.socialq.model.SongRequestData
import com.chrisfry.socialq.utils.ApplicationUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import kaaes.spotify.webapi.android.models.UserPublic
import java.lang.Exception
import java.util.regex.Pattern

class HostActivityNearbyDevices : HostActivity() {
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
            when (payload.type) {
                Payload.Type.BYTES -> handleClientPayload(payload)
                Payload.Type.FILE, Payload.Type.STREAM  -> TODO("not implemented")
            }
        }

        private fun handleClientPayload(payload: Payload) {
            if (payload.asBytes() != null) {
                val payloadString = String(payload.asBytes()!!)
                val payloadType = ApplicationUtils.getMessageTypeFromPayload(payloadString)

                when (payloadType) {
                    NearbyDevicesMessage.SONG_REQUEST -> {
                        val songRequest = getSongRequestFromPayload(payloadString)
                        handleSongRequest(songRequest)
                    }
                    NearbyDevicesMessage.RECEIVE_PLAYLIST_ID, NearbyDevicesMessage.QUEUE_UPDATE,
                    NearbyDevicesMessage.RECEIVE_HOST_USER_ID -> {
                        // Should not receive these messages as the host
                        Log.e(TAG, "Hosts should not receive playlist ID, host ID or queue update messages")
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
            // Do we care about status?
        }

    }

    private fun getSongRequestFromPayload(payloadString: String): SongRequestData {
        var pattern = Pattern.compile(AppConstants.FULL_SONG_REQUEST_REGEX)
        var matcher = pattern.matcher(payloadString)
        // Ensure a proper format has been sent for the track request
        if (matcher.matches()) {
            // Extract track URI
            pattern = Pattern.compile(AppConstants.SONG_REQUEST_MESSAGE)
            matcher = pattern.matcher(payloadString)
            var songUri = matcher.replaceFirst("")
            pattern = Pattern.compile(AppConstants.EXTRACT_SONG_ID_REGEX)
            matcher = pattern.matcher(songUri)
            songUri = matcher.replaceFirst("")

            // Extract user ID
            pattern = Pattern.compile(AppConstants.EXTRACT_CLIENT_ID_REGEX)
            matcher = pattern.matcher(payloadString)
            val clientId = matcher.replaceFirst("")

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
                        Payload.fromBytes(ApplicationUtils.buildBasicPayload(
                                NearbyDevicesMessage.QUEUE_UPDATE.payloadPrefix, currentPlayingIndex.toString()).toByteArray()))
            }
        }
    }

    override fun initiateNewClient(client: Any) {
        if(mClientEndpoints.contains(client.toString()) && mPlaylist != null && mPlaylist.id != null && mCurrentUser != null && mCurrentUser.id != null) {
            Log.d(TAG, "Sending host ID to new client")
            Nearby.getConnectionsClient(this).sendPayload(client.toString(), Payload.fromBytes(
                    ApplicationUtils.buildBasicPayload(NearbyDevicesMessage.RECEIVE_HOST_USER_ID.payloadPrefix, mCurrentUser.id).toByteArray()))
            Log.d(TAG, "Sending playlist ID to new client")
            Nearby.getConnectionsClient(this).sendPayload(client.toString(), Payload.fromBytes(
                    ApplicationUtils.buildBasicPayload(NearbyDevicesMessage.RECEIVE_PLAYLIST_ID.payloadPrefix, mPlaylist.id).toByteArray()))
            Log.d(TAG, "Updating queue of new client (current playing index)")
            Nearby.getConnectionsClient(this).sendPayload(client.toString(),
                    Payload.fromBytes(ApplicationUtils.buildBasicPayload(
                            NearbyDevicesMessage.QUEUE_UPDATE.payloadPrefix, mCachedPlayingIndex.toString()).toByteArray()))
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