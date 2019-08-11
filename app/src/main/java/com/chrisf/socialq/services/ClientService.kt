package com.chrisf.socialq.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.chrisf.socialq.R
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.enums.NearbyDevicesMessage
import com.chrisf.socialq.enums.PayloadTransferUpdateStatus
import com.chrisf.socialq.extensions.addTo
import com.chrisf.socialq.model.AccessModel
import com.chrisf.socialq.model.spotify.PlaylistTrack
import com.chrisf.socialq.model.spotify.UserPrivate
import com.chrisf.socialq.userinterface.App
import com.chrisf.socialq.userinterface.activities.ClientActivity
import com.chrisf.socialq.utils.ApplicationUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.lang.Exception
import java.lang.NumberFormatException
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
    private var clientUser: UserPrivate? = null

    // Title of the SocialQ
    private lateinit var hostQueueTitle: String
    // Notification subtext content
    private lateinit var notificationSubtext: String

    // Media notification style object
    private val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("Client service is being started")

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
                Timber.d("Stopping client service due to bad host endpoint ID")
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


            notificationSubtext = String.format(getString(R.string.client_notification_title_n_plus, hostQueueTitle))

            //TODO: This style may not be the best for older versions of Android
            mediaStyle.setMediaSession(token)

            notificationBuilder = NotificationCompat.Builder(this, App.CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(notificationSubtext)
                    .setSmallIcon(R.drawable.app_notification_icon)
                    .setContentIntent(pendingIntent)
                    .setColor(colorResInt)
                    .setColorized(true)
                    .setShowWhen(false)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)

            // Start service in the foreground
            startForeground(AppConstants.CLIENT_SERVICE_ID, notificationBuilder.build())

            initClient()

            // Let app object know that a service has been started
            App.hasServiceBeenStarted = true
        } else {
            Timber.e("Something went wrong initializing the media session")

            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.d("Client service is being bound")
        isBound = true
        return clientServiceBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Timber.d("Client service is completely unbound")
        isBound = false
        return true
    }

    override fun onRebind(intent: Intent?) {
        Timber.d("Client service is being rebound")
        isBound = true
    }

    override fun authorizationFailed() {
        Timber.d("Client service is ending due to authorization failure")

        if (successfulConnectionFlag) {
            Timber.d("Disconnecting from host")
            Nearby.getConnectionsClient(this).stopAllEndpoints()
        }

        listener?.closeClient()

        App.hasServiceBeenStarted = false
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
                Timber.d("Sending track request message to host: $requestMessage")
                Nearby.getConnectionsClient(this).sendPayload(hostEndpointId, Payload.fromBytes(requestMessage.toByteArray()))
            }
        }
    }

    fun followPlaylist() {
        spotifyApi.followPlaylist(playlist.id)
                .subscribe({
                    if (it.code() == 200) {
                        Timber.d("Successfully followed playlist")
                    } else {
                        Timber.e("Error following playlist")
                    }
                }, {
                    Timber.e(it)
                })
                .addTo(subscriptions)

        if (hostDisconnect) {
            // Followed playlist when host disconnected
            listener?.closeClient()
        } else {
            // Followed playlist on user requested disconnect
            requestDisconnect()
        }
    }

    override fun playlistRefreshComplete() {
        // This will be the last call of proper client initiation
        if (isBeingInitiated) {
            Timber.d("Finished Client initiation")
        }
        isBeingInitiated = false
        listener?.onQueueUpdated(playlistTracks.subList(cachedPlayingIndex, playlistTracks.size))

        if (cachedPlayingIndex < playlistTracks.size) {
            notificationBuilder.setStyle(mediaStyle)
            notificationBuilder.setSubText(notificationSubtext)
            showTrackInNotification(playlistTracks[cachedPlayingIndex].track, false)
        } else {
            clearTrackInfoFromNotification()
        }
    }

    override fun newTrackRetrievalComplete(newTrackIndex: Int) {
        listener?.onQueueUpdated(playlistTracks.subList(cachedPlayingIndex, playlistTracks.size))

        if (playlistTracks.size - cachedPlayingIndex == 1) {
            notificationBuilder.setStyle(mediaStyle)
            notificationBuilder.setSubText(notificationSubtext)
            showTrackInNotification(playlistTracks[cachedPlayingIndex].track, false)
        }
    }

    private fun clearTrackInfoFromNotification() {
        mediaSession.setMetadata(null)

        @Suppress("RestrictedApi")
        notificationBuilder.mActions.clear()

        notificationBuilder.setContentTitle(getString(R.string.app_name))
        notificationBuilder.setContentText(notificationSubtext)
        notificationBuilder.setSubText("")
        notificationBuilder.setLargeIcon(null)
        notificationBuilder.setStyle(null)

        // Display updated notification
        notificationManager.notify(AppConstants.CLIENT_SERVICE_ID, notificationBuilder.build())
    }

    private fun initClient() {
        Timber.d("Initializing Client (connect to host)")

        if (AccessModel.getCurrentUser() == null) {
            spotifyApi.getCurrentUser()
                    .subscribe({
                        val user = it.body()
                        if (user == null) {
                            Timber.e("Error user call returned null")
                            // TODO: can cause infinite loop
                            initClient()
                        } else {
                            AccessModel.setCurrentUser(user)
                            clientUser = user
                            connectToHost()
                        }
                    }, {
                        Timber.e(it)
                        // TODO: can cause infinite loop
                        initClient()
                    })
                    .addTo(subscriptions)
        } else {
            clientUser = AccessModel.getCurrentUser()
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
                    Timber.d("Connection to host successful!")
                    successfulConnectionFlag = true
                    reconnectCount = 0
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Timber.d("Connection to host rejected")
                    stopSelf()
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    if (reconnectCount >= 3) {
                        Timber.e("Error connecting to host")
                        if (successfulConnectionFlag) {
                            hostDisconnect = true
                            listener?.showHostDisconnectDialog()
                        } else {
                            stopSelf()
                        }
                    } else {
                        Timber.d("Reattempting connection to host")
                        reconnectCount++
                        connectToHost()
                    }
                }
            }
        }

        override fun onDisconnected(endPoint: String) {
            // If host has not indicated it is shutting down attempt to reconnect
            if (!hostDisconnect) {
                Timber.e("Lost connection with host. Attempting to reconnect")
                connectToHost()
            }
        }
    }

    private val mPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Timber.d("Client received a payload")
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

                Timber.d(payloadType.toString() + " payload received from host")
                when (payloadType) {
                    NearbyDevicesMessage.INITIATE_CLIENT -> {
                        Timber.d("Started Client initiation")
                        isBeingInitiated = true
                        if (regexMatcher.find()) {
                            val hostId = regexMatcher.group(1)
                            val playlistId = (regexMatcher.group(2))

                            if (hostId.isNotEmpty() && playlistId.isNotEmpty()) {
                                playlistOwnerUserId = hostId
                                playlistTracks.clear()
                                getPlaylistTracks(playlistId)
                            }

                            try {
                                cachedPlayingIndex = (regexMatcher.group(3).toInt())
                            } catch (ex: NumberFormatException) {
                                Timber.e("Invalid index was sent")
                                cachedPlayingIndex = -1
                            }
                        } else {
                            Timber.e("Something went wrong. Regex failed matching for $payloadType")
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
                                    listener?.onQueueUpdated(playlistTracks.subList(cachedPlayingIndex, playlistTracks.size))

                                    if (cachedPlayingIndex < 0 || cachedPlayingIndex > playlistTracks.size) {
                                        Timber.e("Invalid index was sent")
                                    } else if (cachedPlayingIndex == playlistTracks.size) {
                                        clearTrackInfoFromNotification()
                                    } else {
                                        showTrackInNotification(playlistTracks[cachedPlayingIndex].track, false)
                                    }
                                }
                            } catch (exception: NumberFormatException) {
                                Timber.e("Invalid index was sent")
                                cachedPlayingIndex = -1
                            }
                        } else {
                            Timber.e("Something went wrong. Regex failed matching for $payloadType")
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
                                        Timber.e("Invalid index was sent")
                                    } else {
                                        // Pull new track for display
                                        pullNewTrack(newTrackIndex)
                                    }
                                }
                            } catch (exception: NumberFormatException) {
                                Timber.e("Invalid index was sent")
                            }
                        } else {
                            Timber.e("Something went wrong. Regex failed matching for $payloadType")
                        }
                    }
                    NearbyDevicesMessage.HOST_DISCONNECTING -> {
                        Timber.d("Host has indicated that it is shutting down")
                        hostDisconnect = true
                        listener?.showHostDisconnectDialog()
                    }
                    NearbyDevicesMessage.SONG_REQUEST -> {
                        // Should not receive this case as the client
                        Timber.e("Clients should not receive song request messages")
                    }
                    NearbyDevicesMessage.INVALID -> {
                        Timber.e("Invalid payload was sent to client")
                    }
                    else -> {
                        // TODO currently not handling null case
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, payloadTransferUpdate: PayloadTransferUpdate) {
            val status = PayloadTransferUpdateStatus.getStatusFromConstant(payloadTransferUpdate.status)
            Timber.d("Payload Transfer to/from $endpointId has status $status")
        }

        private fun getPlaylistTracks(playlistId: String, offset: Int = 0) {
            spotifyApi.getPlaylistTracks(playlistId, 50, offset)
                    .subscribeOn(Schedulers.io())
                    .subscribe { response ->
                        val playlist = response.body()
                        if (playlist == null) {
                            Timber.e("Error playlist returned null")
                            // TODO: Try again?
                        } else {
                            playlistTracks.addAll(playlist.items)

                            if (playlist.next != null) {
                                Timber.d("Retrieving more playlist tracks")
                                val nextOffset = playlist.offset + playlist.items.size
                                getPlaylistTracks(playlistId, nextOffset)
                            } else {
                                Timber.d("Finished retrieving playlist tracks")
                                playlistRefreshComplete()
                            }
                        }
                    }
                    .addTo(subscriptions)
        }
    }

    override fun onDestroy() {
        Timber.d("Client service is ending")

        if (successfulConnectionFlag) {
            Timber.d("Disconnecting from host")
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
                        Timber.d("Successfully sent a connection request")
                    }
                })
                .addOnFailureListener(object : OnFailureListener {
                    override fun onFailure(exception: Exception) {
                        Timber.e("Failed to send a connection request, can't connect")
                        Timber.e(exception.message)
                        if (successfulConnectionFlag) {
                            hostDisconnect = true
                            listener?.showHostDisconnectDialog()
                        } else {
                            listener?.failedToConnect()
                        }
                    }
                })
    }

    fun requestDisconnect() {
        Timber.d("Disconnecting client from host at user's request")
        Nearby.getConnectionsClient(this).stopAllEndpoints()
        successfulConnectionFlag = false

        listener?.closeClient()
    }

    fun requestInitiation() {
        Timber.d("View has been recreated. Requesting initiation")

        listener?.initiateView(hostQueueTitle, playlistTracks.subList(cachedPlayingIndex, playlistTracks.size))

        if (hostDisconnect) {
            listener?.showHostDisconnectDialog()
        }
    }

    private fun buildSongRequestMessage(trackUri: String?, userId: String?): String {
        if (trackUri.isNullOrEmpty() || userId.isNullOrEmpty()) {
            Timber.d("Can't build track request for URI: $trackUri, user ID: $userId")
            return ""
        } else {
            return String.format(NearbyDevicesMessage.SONG_REQUEST.messageFormat, trackUri, userId)
        }
    }

    // Interface used to cast listeners for client service events
    interface ClientServiceListener {

        fun onQueueUpdated(queueTracks: List<PlaylistTrack>)

        fun showLoadingScreen()

        fun showHostDisconnectDialog()

        fun closeClient()

        fun initiateView(queueTitle: String, trackList: List<PlaylistTrack>)

        fun failedToConnect()
    }
}