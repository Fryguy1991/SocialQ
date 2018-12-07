package com.chrisfry.socialq.services

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.NearbyDevicesMessage
import com.chrisfry.socialq.enums.PayloadTransferUpdateStatus
import com.chrisfry.socialq.model.ClientRequestData
import com.chrisfry.socialq.userinterface.activities.ClientActivityNearbyDevices
import com.chrisfry.socialq.utils.ApplicationUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import kaaes.spotify.webapi.android.SpotifyCallback
import kaaes.spotify.webapi.android.SpotifyError
import kaaes.spotify.webapi.android.SpotifyService
import kaaes.spotify.webapi.android.models.Playlist
import kaaes.spotify.webapi.android.models.PlaylistSimple
import retrofit.client.Response
import java.lang.Exception
import java.lang.NumberFormatException
import java.util.HashMap
import java.util.regex.Pattern

class ClientService : SpotifyAccessService() {
    companion object {
        val TAG = ClientService::class.java.name
    }

    inner class ClientServiceBinder : Binder() {
        fun getService(): ClientService {
            return this@ClientService
        }
    }

    // SERVICE ELEMENTS
    // Binder for talking from bound activity to host
    private val hostServiceBinder = ClientServiceBinder()
    // Flag indicating if the service is bound to an activity
    private var isBound = false
    // Object listening for events from the service
    private var listener: ClientServiceListener? = null

    // NEARBY CONNECTIONS ELEMENTS
    // Variable to hold host endpoint
    private lateinit var mHostEndpointId: String
    // Flag indicating success of connection to host (used during activity destruction)
    private var mSuccessfulConnectionFlag = false
    // Flag to indicate if the user has initiated the disconnect
    private var userDisconnect = false

    // SPOTIFY ELEMENTS
    // Spotify user ID of the host
    private var mHostUserId: String? = null
    // Cached index for displaying correct track list
    private var cachedPlayingIndex = 0


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "Client service is being bound")
        isBound = true
        return hostServiceBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Client service is completely unbound")
        isBound = false
        return true
    }

    override fun onRebind(intent: Intent?) {
        Log.d(TAG, "Client service is being rebound")
        isBound = true
    }


    // Inner interface used to cast listeners for service events
    interface ClientServiceListener {

        fun onQueueUpdated(songRequests: List<ClientRequestData>)

        fun showLoadingScreen()

        fun showHostDisconnectDialog()

        fun closeClient()
    }

    fun setClientServiceListener(listener: ClientServiceListener) {
        this.listener = listener
    }

    fun removeClientServiceListener() {
        listener = null
    }

    fun sendTrackToHost(uri: String) {

    }

    fun followPlaylist() {

    }

    override fun playlistRefreshComplete() {
        listener?.onQueueUpdated(playlistTracks.subList())
    }







    // COPY OVER NEARBY CLIENT CODE


    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Nearby.getConnectionsClient(this@ClientService).acceptConnection(endpointId, mPayloadCallback)
        }

        override fun onConnectionResult(endPoint: String, connectionResolution: ConnectionResolution) {
            when (connectionResolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connection to host successful!")
                    mSuccessfulConnectionFlag = true
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d(TAG, "Connection to host rejected")
                    stopSelf()
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.d(TAG, "Error connecting to host")
                    stopSelf()
                }
            }
        }

        override fun onDisconnected(endPoint: String) {
            Log.d(TAG, "Host disconnected from the client")

            // If host has disconnected from the client.  Allow the client to follow the playlist
            // If client disconnected from the host they will have already picked if they wanted to
            // follow the playlist
            if (!userDisconnect) {
                listener?.showHostDisconnectDialog()
            }
        }
    }

    private val mPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d(TAG, "Client received a payload")
            when (payload.type) {
                Payload.Type.BYTES -> handleHostPayload(payload)
                Payload.Type.STREAM, Payload.Type.FILE -> TODO("not implemented")
            }
        }

        private fun handleHostPayload(payload: Payload) {
            if (payload.asBytes() != null) {
                val payloadString = String(payload.asBytes()!!)
                val payloadType = ApplicationUtils.getMessageTypeFromPayload(payloadString)

                val regexMatcher = Pattern.compile(payloadType.regex).matcher(payloadString)

                Log.d(TAG, payloadType.toString() + " payload received from host")
                when (payloadType) {
                    NearbyDevicesMessage.INITIATE_CLIENT -> {
                        if (regexMatcher.find()) {
                            val hostId = regexMatcher.group(1)
                            val playlistId = (regexMatcher.group(2))

                            if (hostId.isNotEmpty() && playlistId.isNotEmpty()) {
                                mHostUserId = hostId
                                spotifyService.getPlaylist(hostId, playlistId, playlistCallback)
                            }

                            try {
                                cachedPlayingIndex = (regexMatcher.group(3).toInt())
                            } catch (ex: NumberFormatException) {
                                Log.e(TAG, "Invalid index was sent")
                                cachedPlayingIndex = -1
                            }
                        } else {
                            Log.e(TAG, "Something went wrong. Regex failed matching for $payloadType")
                        }
                    }
                    NearbyDevicesMessage.QUEUE_UPDATE -> {
                        // Host is notifying us that the queue has been updated
                        if (regexMatcher.find()) {
                            try {
                                refreshPlaylist()
                            } catch (exception: NumberFormatException) {
                                Log.e(TAG, "Invalid index was sent")
                            }
                        }  else {
                            Log.e(TAG, "Something went wrong. Regex failed matching for $payloadType")
                        }
                    }
                    NearbyDevicesMessage.SONG_REQUEST -> {
                        // Should not receive this case as the client
                        Log.e(TAG, "Clients should not receive song request messages")
                    }
                    NearbyDevicesMessage.INVALID -> {
                        Log.e(TAG, "Invalid payload was sent to client")
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
        Log.d(TAG, "Client nearby activity being destroyed")

        if (mSuccessfulConnectionFlag) {
            Log.d(TAG, "Disconnecting from host")
            Nearby.getConnectionsClient(this).stopAllEndpoints()
        }

        super.onDestroy()
    }

    fun sendTrackToHost(requestMessage: String?) {
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
                            Log.e(TAG, "Failed to send a connection request, can't connect")
//                            Toast.makeText(this@ClientActivityNearbyDevices, getString(R.string.toast_host_connection_error), Toast.LENGTH_SHORT).show()
//                            finish()
                        }
                    })
        }
    }

    override fun disconnectClient() {
        userDisconnect = true
        Nearby.getConnectionsClient(this).disconnectFromEndpoint(mHostEndpointId)
    }



    // COPYING OVER USERFUL ACTIVITY CODE

    private fun buildSongRequestMessage(trackUri: String?, userId: String?): String? {
        if (trackUri != null && userId != null && !trackUri.isEmpty() && !userId.isEmpty()) {
            return String.format(NearbyDevicesMessage.SONG_REQUEST.messageFormat, trackUri, userId)
        }
        Log.d(TAG, "Can't build track request for URI: $trackUri, user ID: $userId")
        return null
    }







    // Callback object for getting host playlist
    private val playlistCallback = object : SpotifyCallback<Playlist>() {
        override fun success(playlist: Playlist?, response: Response?) {
            if (playlist != null) {
                this@ClientService.playlist = playlist

                playlistTracks.clear()
                playlistTracks.addAll(playlist.tracks.items)

                if (playlistTracks.size < playlist.tracks.total) {
                    // Need to pull more tracks
                    val options = HashMap<String, Any>()
                    options[SpotifyService.OFFSET] = playlistTracks.size
                    options[SpotifyService.LIMIT] = AppConstants.PLAYLIST_TRACK_LIMIT

                    spotifyService.getPlaylistTracks(playlistOwnerUserId, playlist.id, options, playlistTrackCallback)
                } else {
                    Log.d(TAG, "Finished retrieving playlist tracks")
                    playlistRefreshComplete()
                }
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            if (spotifyError != null) {
                Log.e(TAG, spotifyError.errorDetails.message)
            }
            Log.e(TAG, "Failed to get host playlist")
        }

    }

}