package com.chrisfry.socialq.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.NearbyDevicesMessage
import com.chrisfry.socialq.enums.PayloadTransferUpdateStatus
import com.chrisfry.socialq.userinterface.App
import com.chrisfry.socialq.userinterface.activities.ClientActivity
import com.chrisfry.socialq.utils.ApplicationUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import kaaes.spotify.webapi.android.SpotifyCallback
import kaaes.spotify.webapi.android.SpotifyError
import kaaes.spotify.webapi.android.SpotifyService
import kaaes.spotify.webapi.android.models.*
import retrofit.client.Response
import java.lang.Exception
import java.lang.NumberFormatException
import java.util.HashMap
import java.util.regex.Pattern

class ClientService : SpotifyAccessService() {
    companion object {
        val TAG = ClientService::class.java.name
    }

    inner class ClientServiceBinder : SpotifyAccessServiceBinder() {
        override fun getService(): ClientService {
            return this@ClientService
        }
    }

    // SERVICE ELEMENTS
    // Binder for talking from bound activity to host
    private val clientServiceBinder = ClientServiceBinder()
    // Flag indicating if the service is bound to an activity
    private var isBound = false
    // Object listening for events from the service
    private var listener: ClientServiceListener? = null

    // NEARBY CONNECTIONS ELEMENTS
    // Variable to hold host endpoint
    private var hostEndpointId = ""
    // Flag indicating success of connection to host (used during activity destruction)
    private var successfulConnectionFlag = false
    // Flag to indicate if the host disconnected from the client
    private var hostDisconnect = false
    // Flag to indicate if the client is mid-initiation
    private var isBeingInitiated = false
    // Count to indicate how many times we've retried reconnecting to the host endpoint
    private var reconnectCount = 0

    // SPOTIFY ELEMENTS
    // Cached index for displaying correct track list
    private var cachedPlayingIndex = 0
    // Reference to client user Spotify account
    private var clientUser: UserPublic? = null

    // Title of the SocialQ
    private lateinit var hostQueueTitle: String

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Client service is being started")

        if (intent != null) {
            // Get name of queue from intent
            val titleString = intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY)
            if (titleString.isNullOrEmpty()) {
                hostQueueTitle = getString(R.string.queue_title_default_value)
            } else {
                hostQueueTitle = titleString
            }

            // Ensure our endpoint is valid and store it
            val endpointString = intent.getStringExtra(AppConstants.ND_ENDPOINT_ID_EXTRA_KEY)
            if (endpointString.isNullOrEmpty()) {
                Log.d(TAG, "Stopping client service due to bad host endpoint ID")
                stopSelf()
            } else {
                hostEndpointId = endpointString
            }
        }

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create media session so Android colorizes based on album art
        mediaSession = MediaSessionCompat(baseContext, AppConstants.CLIENT_MEDIA_SESSION_TAG)
        mediaSession.isActive = true

        val token = mediaSession.sessionToken
        if (token != null) {

            // Create intent/pending intent for returning to application when touching notification
            val notificationIntent = Intent(this, ClientActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

            val colorResInt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getColor(R.color.Active_Button_Color)
            } else {
                resources.getColor(R.color.Active_Button_Color)
            }


            val subtext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                String.format(getString(R.string.client_notification_title_n_plus, hostQueueTitle))
            } else {
                String.format(getString(R.string.client_notification_title_pre_n, hostQueueTitle))
            }

            //TODO: This style may not be the best for older versions of Android
            val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            mediaStyle.setMediaSession(token)

            notificationBuilder = NotificationCompat.Builder(this, App.CHANNEL_ID)
                    .setSubText(subtext)
                    .setSmallIcon(R.drawable.notification_icon)
                    .setContentIntent(pendingIntent)
                    .setColor(colorResInt)
                    .setColorized(true)
                    .setShowWhen(false)
                    .setOnlyAlertOnce(true)
                    .setStyle(mediaStyle)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)

            // Start service in the foreground
            startForeground(AppConstants.CLIENT_SERVICE_ID, notificationBuilder.build())

            // Request authorization code for Spotify
            requestClientAuthorization()

            // Let app object know that a service has been started
            App.hasServiceBeenStarted = true
        } else {
            Log.e(TAG, "Something went wrong initializing the media session")

            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "Client service is being bound")
        isBound = true
        return clientServiceBinder
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

    fun setClientServiceListener(listener: ClientServiceListener) {
        this.listener = listener
    }

    fun removeClientServiceListener() {
        listener = null
    }

    fun sendTrackToHost(uri: String) {
        if (uri.isNotEmpty()) {
            val requestMessage = buildSongRequestMessage(uri, clientUser!!.id)
            if (requestMessage.isNotEmpty()) {
                Log.d(TAG, "Sending track request message to host: $requestMessage")
                Nearby.getConnectionsClient(this).sendPayload(hostEndpointId, Payload.fromBytes(requestMessage.toByteArray()))
            }
        }
    }

    fun followPlaylist() {
        spotifyService.followPlaylist(clientUser!!.id, playlist.id, followPlaylistCallback)
    }

    override fun playlistRefreshComplete() {
        // This will be the last call of proper client initiation
        if (isBeingInitiated) {
            Log.d(TAG, "Finished Client initiation")
        }
        isBeingInitiated = false
        listener?.onQueueUpdated(playlistTracks.subList(cachedPlayingIndex, playlist.tracks.total))

        if (cachedPlayingIndex < playlistTracks.size) {
            showTrackInNotification(playlistTracks[cachedPlayingIndex].track, false)
        } else {
            // TODO: Remove track information from the notification
        }
    }

    override fun newTrackRetrievalComplete(newTrackIndex: Int) {
        listener?.onQueueUpdated(playlistTracks.subList(cachedPlayingIndex, playlist.tracks.total))
    }

    override fun initSpotifyElements(accessToken: String) {
        super.initSpotifyElements(accessToken)

        if (clientUser == null) {
            Log.d(HostService.TAG, "Receiving Spotify access for the first time. Retrieve current user and connect to host")

            clientUser = spotifyService.me
            connectToHost()
        }
    }

    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Nearby.getConnectionsClient(this@ClientService).acceptConnection(endpointId, mPayloadCallback)
        }

        override fun onConnectionResult(endPoint: String, connectionResolution: ConnectionResolution) {
            when (connectionResolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connection to host successful!")
                    successfulConnectionFlag = true
                    reconnectCount = 0
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d(TAG, "Connection to host rejected")
                    stopSelf()
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    if (reconnectCount >= 3) {
                        Log.e(TAG, "Error connecting to host")
                        if (successfulConnectionFlag) {
                            hostDisconnect = true
                            listener?.showHostDisconnectDialog()
                        } else {
                            stopSelf()
                        }
                    } else {
                        Log.d(TAG, "Reattempting connection to host")
                        reconnectCount++
                        connectToHost()
                    }
                }
            }
        }

        override fun onDisconnected(endPoint: String) {
            // If host has not indicated it is shutting down attempt to reconnect
            if (!hostDisconnect) {
                Log.e(TAG, "Lost connection with host. Attempting to reconnect")
                connectToHost()
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
                        Log.d(TAG, "Started Client initiation")
                        isBeingInitiated = true
                        if (regexMatcher.find()) {
                            val hostId = regexMatcher.group(1)
                            val playlistId = (regexMatcher.group(2))

                            if (hostId.isNotEmpty() && playlistId.isNotEmpty()) {
                                playlistOwnerUserId = hostId
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
                            isBeingInitiated = false
                        }
                    }
                    NearbyDevicesMessage.CURRENTLY_PLAYING_UPDATE -> {
                        // Host is notifying us that the currently playing index has changed
                        if (regexMatcher.find()) {
                            try {
                                cachedPlayingIndex = regexMatcher.group(1).toInt()
                                // Don't interrupt client initiation
                                if (!isBeingInitiated) {
                                    listener?.onQueueUpdated(playlistTracks.subList(cachedPlayingIndex, playlist.tracks.total))

                                    if (cachedPlayingIndex < 0 || cachedPlayingIndex > playlistTracks.size) {
                                        Log.e(TAG, "Invalid index was sent")
                                    } else if (cachedPlayingIndex == playlistTracks.size) {
                                        // TODO: Clear track info from notification
                                    } else {
                                        showTrackInNotification(playlistTracks[cachedPlayingIndex].track, false)
                                    }
                                }
                            } catch (exception: NumberFormatException) {
                                Log.e(TAG, "Invalid index was sent")
                                cachedPlayingIndex = -1
                            }
                        } else {
                            Log.e(TAG, "Something went wrong. Regex failed matching for $payloadType")
                        }
                    }
                    NearbyDevicesMessage.NEW_TRACK_ADDED -> {
                        // Host is notifying us that a new track has been added
                        if (regexMatcher.find()) {
                            try {
                                val newTrackIndex = regexMatcher.group(1).toInt()
                                // Don't interrupt client initiation
                                if (!isBeingInitiated) {
                                    if (newTrackIndex < 0 || newTrackIndex > playlistTracks.size) {
                                        Log.e(TAG, "Invalid index was sent")
                                    } else {
                                        // Pull new track for display
                                        pullNewTrack(newTrackIndex)
                                    }
                                }
                            } catch (exception: NumberFormatException) {
                                Log.e(TAG, "Invalid index was sent")
                            }
                        } else {
                            Log.e(TAG, "Something went wrong. Regex failed matching for $payloadType")
                        }
                    }
                    NearbyDevicesMessage.HOST_DISCONNECTING -> {
                        Log.d(TAG, "Host has indicated that it is shutting down")
                        hostDisconnect = true
                        listener?.showHostDisconnectDialog()
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
        Log.d(TAG, "Client service is ending")

        if (successfulConnectionFlag) {
            Log.d(TAG, "Disconnecting from host")
            Nearby.getConnectionsClient(this).stopAllEndpoints()
        }

        App.hasServiceBeenStarted = false
        super.onDestroy()
    }

    fun connectToHost() {
        Nearby.getConnectionsClient(this).requestConnection(
                "SocialQ Client",
                hostEndpointId,
                mConnectionLifecycleCallback)
                .addOnSuccessListener(object : OnSuccessListener<Void> {
                    override fun onSuccess(p0: Void?) {
                        Log.d(TAG, "Successfully sent a connection request")
                    }
                })
                .addOnFailureListener(object : OnFailureListener {
                    override fun onFailure(exception: Exception) {
                        Log.e(TAG, "Failed to send a connection request, can't connect")
                        Log.e(TAG, exception.message)
                        if (successfulConnectionFlag) {
                            hostDisconnect = true
                            listener?.showHostDisconnectDialog()
                        } else {
                            stopSelf()
                        }
                    }
                })
    }

    fun requestDisconnect() {
        Log.d(TAG, "Disconnecting client from host at user's request")
        Nearby.getConnectionsClient(this).stopAllEndpoints()
        successfulConnectionFlag = false

        listener?.closeClient()
    }

    fun requestInitiation() {
        Log.d(TAG, "View has been recreated. Requesting initiation")

        listener?.initiateView(hostQueueTitle, playlistTracks.subList(cachedPlayingIndex, playlist.tracks.total) )

        if (hostDisconnect) {
            listener?.showHostDisconnectDialog()
        }
    }

    private fun buildSongRequestMessage(trackUri: String?, userId: String?): String {
        if (trackUri.isNullOrEmpty() || userId.isNullOrEmpty()) {
            Log.d(TAG, "Can't build track request for URI: $trackUri, user ID: $userId")
            return ""
        } else {
            return String.format(NearbyDevicesMessage.SONG_REQUEST.messageFormat, trackUri, userId)
        }
    }

    // START SPOTIFY CALLBACK OBJECTS
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
            Log.e(TAG, spotifyError?.errorDetails?.message.toString())
            Log.e(TAG, "Failed to get host playlist")
        }
    }

    private val followPlaylistCallback = object : SpotifyCallback<Result>() {
        override fun success(result: Result?, response: Response?) {
            Log.d(TAG, "Successfully followed the playlist")

            if (hostDisconnect) {
                // Followed playlist when host disconnected
                listener?.closeClient()
            } else {
                // Followed playlist on user requested disconnect
                requestDisconnect()
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            Log.e(TAG, spotifyError?.errorDetails?.message.toString())
            Log.e(TAG, "Failed to follow the playlist")
            if (hostDisconnect) {
                // Tried to follow playlist when host disconnected
                listener?.closeClient()
            } else {
                // Tried to follow playlist on user requested disconnect
                requestDisconnect()
            }
        }
    }
    // END SPOTIFY CALLBACK OBJECTS

    // Interface used to cast listeners for client service events
    interface ClientServiceListener {

        fun onQueueUpdated(queueTracks: List<PlaylistTrack>)

        fun showLoadingScreen()

        fun showHostDisconnectDialog()

        fun closeClient()

        fun initiateView(queueTitle: String, trackList: List<PlaylistTrack>)
    }
}