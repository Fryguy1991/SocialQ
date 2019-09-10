package com.chrisf.socialq.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.app.NotificationCompat.MediaStyle
import com.chrisf.socialq.R
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.dagger.components.ServiceComponent
import com.chrisf.socialq.enums.NearbyDevicesMessage
import com.chrisf.socialq.enums.PayloadTransferUpdateStatus
import com.chrisf.socialq.model.ClientRequestData
import com.chrisf.socialq.model.spotify.Track
import com.chrisf.socialq.network.BitmapListener
import com.chrisf.socialq.network.GetBitmapTask
import com.chrisf.socialq.processor.HostProcessor
import com.chrisf.socialq.processor.HostProcessor.HostAction
import com.chrisf.socialq.processor.HostProcessor.HostAction.*
import com.chrisf.socialq.processor.HostProcessor.HostState
import com.chrisf.socialq.processor.HostProcessor.HostState.*
import com.chrisf.socialq.userinterface.App
import com.chrisf.socialq.userinterface.activities.HostActivity
import com.chrisf.socialq.utils.ApplicationUtils
import com.chrisf.socialq.utils.DisplayUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import timber.log.Timber
import java.lang.Exception
import java.util.*
import java.util.regex.Pattern

class HostService : BaseService<HostState, HostAction, HostProcessor>(), BitmapListener {

    inner class HostServiceBinder : Binder() {
        fun getService(): HostService {
            return this@HostService
        }
    }

    // Flag for indicating if we actually need a new access token (set to true when shutting down)
    private var isServiceEnding = false

    // NOTIFICATION ELEMENTS
    // Reference to notification manager
    private lateinit var notificationManager: NotificationManager
    // Builder for foreground notification
    private lateinit var notificationBuilder: NotificationCompat.Builder
    // Reference to media session
    private lateinit var mediaSession: MediaSessionCompat
    // Reference to meta data builder
    private val metaDataBuilder = MediaMetadataCompat.Builder()

    // SERVICE ELEMENTS
    // Binder for talking from bound activity to host
    private val hostServiceBinder = HostServiceBinder()
    // Flag indicating if the service is bound to an activity
    private var isBound = false
    // Object listening for events from the service
    private var listener: HostServiceListener? = null

    // NOTIFICATION ELEMENTS
    // Reference to playback state builder
    private val playbackStateBuilder = PlaybackStateCompat.Builder()
    // Reference to notification style
    private val mediaStyle = MediaStyle()

    // NEARBY CONNECTION ELEMENTS
    // List of the client endpoints that are currently connected to the host service
    private var clientEndpoints = ArrayList<String>()
    // Flag indicating success of advertising (used during activity destruction)
    private var successfulAdvertisingFlag = false

    private val hostServiceBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                when (intent.action) {
                    AppConstants.BR_INTENT_ACCESS_TOKEN_UPDATED -> {
                        Timber.d("Access token has been refreshed, update player access token")
                        actionStream.accept(AccessTokenUpdated)
                    }
                    else -> {
                        // Not handling other actions here, do nothing
                    }
                }
            }
        }
    }

    override fun resolveDependencies(serviceComponent: ServiceComponent) {
        serviceComponent.inject(this)
    }

    override fun handleState(state: HostState) {
        when (state) {
            is QueueInitiationComplete -> onQueueInitiationComplete(state)
            is PlaybackResumed -> onPlaybackResumed(state)
            is PlaybackPaused -> onPlaybackPaused(state)
            is PlaybackNext -> onPlaybackNext(state)
            is AudioDeliveryDone -> onAudioDeliveryDone(state)
            is TrackAdded -> onTrackAdded(state)
            is InitiateNewClient -> initiateNewClient(state)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                null -> {
                    Timber.d("Host service is being started")

                    // Set default for queue title
                    var queueTitle = getString(R.string.queue_title_default_value)

                    // Check intent for storage of queue settings
                    if (!intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY).isNullOrBlank()) {
                        queueTitle = intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY)
                    }
                    val isQueueFairPlay = intent.getBooleanExtra(AppConstants.FAIR_PLAY_KEY, resources.getBoolean(R.bool.fair_play_default))
                    val basePlaylistId = intent.getStringExtra(AppConstants.BASE_PLAYLIST_ID_KEY)

                    actionStream.accept(
                            ServiceStarted(
                                    this,
                                    queueTitle,
                                    isQueueFairPlay,
                                    basePlaylistId
                            )
                    )

                    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    // Create intent for touching foreground notification
                    val pendingIntent = PendingIntent.getActivity(
                            this,
                            0,
                            Intent(this, HostActivity::class.java),
                            0)

                    // Build foreground notification
                    notificationBuilder = NotificationCompat.Builder(this, App.CHANNEL_ID)
                            .setContentTitle(String.format(getString(R.string.host_notification_content_text), queueTitle))
                            .setSmallIcon(R.drawable.app_notification_icon)
                            .setContentIntent(pendingIntent)
                            .setColorized(true)
                            .setOnlyAlertOnce(true)
                            .setShowWhen(false)

                    mediaSession = MediaSessionCompat(baseContext, AppConstants.HOST_MEDIA_SESSION_TAG)

                    // Register to receive access token update events
                    LocalBroadcastManager.getInstance(applicationContext).registerReceiver(
                            hostServiceBroadcastReceiver, IntentFilter(AppConstants.BR_INTENT_ACCESS_TOKEN_UPDATED))

                    // Start service in the foreground
                    startForeground(AppConstants.HOST_SERVICE_ID, notificationBuilder.build())

                    // Let app object know that a service has been started
                    App.hasServiceBeenStarted = true
                }

                AppConstants.ACTION_REQUEST_PLAY_PAUSE -> actionStream.accept(RequestTogglePlayPause)
                AppConstants.ACTION_REQUEST_NEXT -> actionStream.accept(RequestNext)
                else -> {
                    Timber.e("Not handling action: ${intent.action}")
                }
            }
        } else {
            Timber.e("Intent for onStartCommand was null")
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.d("Host service is being bound")
        isBound = true
        return hostServiceBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Timber.d("Host service is completely unbound")
        isBound = false
        return true
    }

    override fun onRebind(intent: Intent?) {
        Timber.d("Host service is being rebound")
        isBound = true
    }

    override fun onDestroy() {
        Timber.d("Host service is ending")

        mediaSession.release()
        isServiceEnding = true

        // Stop advertising and alert clients we have disconnected
        if (successfulAdvertisingFlag) {
            Timber.d("Stop advertising host")

            Nearby.getConnectionsClient(applicationContext).stopAdvertising()
            Nearby.getConnectionsClient(applicationContext).stopAllEndpoints()
        }

        // Let app know that the service has ended
        App.hasServiceBeenStarted = false

        // Unregister broadcast receiver
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(hostServiceBroadcastReceiver)

        super.onDestroy()
    }

    private fun onQueueInitiationComplete(state: QueueInitiationComplete) {
        mediaSession.setPlaybackState(
                playbackStateBuilder.setState(
                        PlaybackStateCompat.STATE_PAUSED,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        0F
                ).build())
        mediaSession.isActive = true
        mediaStyle.setMediaSession(mediaSession.sessionToken)

        if (state.requestDataList.isNotEmpty()) {
            addActionsToNotificationBuilder(false)
            showTrackInNotification(state.requestDataList[0].track.track)
        }
        startNearbyAdvertising(state)

        listener?.onQueueUpdated(state.requestDataList)
    }

    private fun onPlaybackResumed(state: PlaybackResumed) {
        // Update session playback state
        mediaSession.setPlaybackState(
                playbackStateBuilder.setState(
                        PlaybackStateCompat.STATE_PLAYING,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        1F
                ).build())

        // Update notification builder action buttons for playing
        addActionsToNotificationBuilder(true)
        notificationManager.notify(AppConstants.HOST_SERVICE_ID, notificationBuilder.build())

        notifyPlayStarted()
    }

    private fun onPlaybackPaused(state: PlaybackPaused) {
        // Update session playback state
        mediaSession.setPlaybackState(
                playbackStateBuilder.setState(
                        PlaybackStateCompat.STATE_PAUSED,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        0F
                ).build())

        if (state.tracksRemaining) {
            addActionsToNotificationBuilder(false)
            notificationManager.notify(AppConstants.HOST_SERVICE_ID, notificationBuilder.build())
        }
        notifyPaused()
    }

    private fun onPlaybackNext(state: PlaybackNext) {
        // TODO: Show track in notification

        if (listener != null) {
            listener?.onQueueUpdated(state.trackRequestData)
        }

        Timber.d("Updating notification")
        if (state.trackRequestData.isEmpty()) {
            clearTrackInfoFromNotification(state.queueTitle)
        } else {
            showTrackInNotification(state.trackRequestData[0].track.track)
        }

        Timber.d("Notifying clients of next event")
        if (state.currentPlaylistIndex >= 0) {
            for (endpointId: String in clientEndpoints) {
                Nearby.getConnectionsClient(this).sendPayload(endpointId,
                        Payload.fromBytes(String.format(NearbyDevicesMessage.CURRENTLY_PLAYING_UPDATE.messageFormat,
                                state.currentPlaylistIndex.toString()).toByteArray()))
            }
        }
    }

    private fun onAudioDeliveryDone(state: AudioDeliveryDone) {
        // Stop service if we're not bound and out of songs
        if (!isBound) {
            Timber.d("Out of songs and not bound to host activity. Shutting down service.")
            stopSelf()
        }
    }

    private fun startNearbyAdvertising(state: QueueInitiationComplete) {
        // Create advertising options (strategy)
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()

        // Build name of host. See AppConstants.NEARBY_HOST_NAME_FORMAT
        val ownerName = if (state.hostDisplayName.isEmpty()) state.hostId else state.hostDisplayName

        val isFairplayCharacter = if (state.isFairPlay) {
            AppConstants.FAIR_PLAY_TRUE_CHARACTER
        } else {
            AppConstants.FAIR_PLAY_FALSE_CHARACTER
        }
        val hostName = String.format(AppConstants.NEARBY_HOST_NAME_FORMAT, state.queueTitle, ownerName, isFairplayCharacter)

        // Attempt to start advertising
        Nearby.getConnectionsClient(this).startAdvertising(
                hostName,
                AppConstants.SERVICE_NAME,
                mConnectionLifecycleCallback,
                options)
                .addOnSuccessListener(object : OnSuccessListener<Void> {
                    override fun onSuccess(unusedResult: Void?) {
                        Timber.d("Successfully advertising the host")
                        successfulAdvertisingFlag = true
                    }
                })
                .addOnFailureListener(object : OnFailureListener {
                    override fun onFailure(p0: Exception) {
                        Timber.e(p0)
                        Timber.e("Failed to start advertising the host")
                        stopSelf()
                    }
                })
    }


    /**
     * Adds action icons to notification builder, also removes existing icons
     *
     * @param isPlaying: Whether the Spotify player is currently playing or not
     */
    private fun addActionsToNotificationBuilder(isPlaying: Boolean) {
        // Remove actions from notification builder
        @Suppress("RestrictedApi")
        notificationBuilder.mActions.clear()

        val searchIntent = Intent(this, HostActivity::class.java)
        searchIntent.action = AppConstants.ACTION_NOTIFICATION_SEARCH

        // Intent for starting search
        val searchPendingIntent = PendingIntent.getActivity(
                this,
                0,
                searchIntent,
                0)

        // Intent for toggling play/pause
        val playPausePendingIntent = PendingIntent.getService(
                this,
                0,
                Intent(this, HostService::class.java).setAction(AppConstants.ACTION_REQUEST_PLAY_PAUSE),
                0)

        // Intent for skipping
        val skipPendingIntent = PendingIntent.getService(
                this,
                0,
                Intent(this, HostService::class.java).setAction(AppConstants.ACTION_REQUEST_NEXT),
                0)

        // Determine if we need the play or pause icon
        val playPauseResourceId = if (isPlaying) {
            R.mipmap.ic_media_pause
        } else {
            R.mipmap.ic_media_play
        }

        notificationBuilder
                .addAction(R.mipmap.search_icon_white, AppConstants.ACTION_NOTIFICATION_SEARCH, searchPendingIntent)
                .addAction(playPauseResourceId, AppConstants.ACTION_REQUEST_PLAY_PAUSE, playPausePendingIntent)
                .addAction(R.mipmap.ic_media_next, AppConstants.ACTION_REQUEST_NEXT, skipPendingIntent)

        // Display play/pause and next in compat view
        val style = mediaStyle.setShowActionsInCompactView(1, 2)
        notificationBuilder.setStyle(style)
    }

    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Nearby.getConnectionsClient(applicationContext).acceptConnection(endpointId, mPayloadCallback)
        }

        override fun onConnectionResult(endPoint: String, connectionResolution: ConnectionResolution) {
            when (connectionResolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Timber.d("Established connection with a client ($endPoint), initiate client")

                    // Notify activity a client connected
                    listener?.showClientConnected()
                    clientEndpoints.add(endPoint)
                    actionStream.accept(ClientConnected(endPoint))
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> Timber.d("Client connection rejected")
                ConnectionsStatusCodes.STATUS_ERROR -> Timber.d("Error during client connection")
                else -> TODO("not implemented")
            }
        }

        override fun onDisconnected(endPoint: String) {
            Timber.d("Client $endPoint disconnected")

            // Notify activity a client disconnected
            listener?.showClientDisconnected()
            clientEndpoints.remove(endPoint)
        }
    }

    private val mPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Timber.d("Host received a payload")
            when (payload.type) {
                Payload.Type.BYTES -> handleClientPayload(payload)
                Payload.Type.FILE, Payload.Type.STREAM -> Timber.e("Currently not handling  streams or files")
            }
        }

        private fun handleClientPayload(payload: Payload) {
            // TODO: Should move this processing to processor XD
            if (payload.asBytes() != null) {
                val payloadString = String(payload.asBytes()!!)
                val payloadType = ApplicationUtils.getMessageTypeFromPayload(payloadString)

                Timber.d("Payload is type $payloadType")

                when (payloadType) {
                    NearbyDevicesMessage.SONG_REQUEST -> handlePayloadData(payloadString)
                    NearbyDevicesMessage.CURRENTLY_PLAYING_UPDATE,
                    NearbyDevicesMessage.INITIATE_CLIENT,
                    NearbyDevicesMessage.HOST_DISCONNECTING,
                    NearbyDevicesMessage.NEW_TRACK_ADDED -> {
                        Timber.e("Hosts should not receive $payloadType messages")
                    }
                    NearbyDevicesMessage.INVALID -> {
                        Timber.e("Invalid payload was sent to host")
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
    }

    private fun handlePayloadData(payloadString: String) {
        // TODO: Should move this processing to processor XD
        val matcher = Pattern.compile(AppConstants.FULL_SONG_REQUEST_REGEX).matcher(payloadString)
        // Ensure a proper format has been sent for the track request
        if (matcher.find()) {
            // Extract track URI and client ID
            val songUri = matcher.group(1)
            val clientId = matcher.group(2)

            if (songUri.isNullOrEmpty()) {
                Timber.e("Error retrieving data from client song request")
                return
            }

            actionStream.accept(TrackRequested(clientId, songUri))
        }
    }

    private fun onTrackAdded(state: TrackAdded) {
        listener?.onQueueUpdated(state.trackRequestData)

        if (state.needDisplay && state.trackRequestData.isNotEmpty()) {
            addActionsToNotificationBuilder(state.isPlaying)
            showTrackInNotification(state.trackRequestData[0].track.track)
        }

        if (state.newTrackIndex >= 0) {
            for (endpointId: String in clientEndpoints) {
                Nearby.getConnectionsClient(this).sendPayload(endpointId,
                        Payload.fromBytes(String.format(NearbyDevicesMessage.NEW_TRACK_ADDED.messageFormat,
                                state.newTrackIndex.toString()).toByteArray()))
            }
        }
    }

    private fun notifyClientsHostDisconnecting() {
        for (endpoint: String in clientEndpoints) {
            Nearby.getConnectionsClient(this).sendPayload(endpoint,
                    Payload.fromBytes(AppConstants.HOST_DISCONNECT_MESSAGE.toByteArray()))
        }
    }

    private fun initiateNewClient(state: InitiateNewClient) {
        if (clientEndpoints.contains(state.newClientId)
                && state.playlistId.isNotEmpty()
                && state.hostUserId.isNotEmpty()) {
            Timber.d("Sending host ID, playlist id, and current playing index to new client")
            Nearby.getConnectionsClient(this).sendPayload(state.newClientId, Payload.fromBytes(
                    String.format(NearbyDevicesMessage.INITIATE_CLIENT.messageFormat,
                            state.hostUserId,
                            state.playlistId,
                            state.currentPlaylistIndex).toByteArray()))
        }
    }

    // Inner interface used to cast listeners for service events
    interface HostServiceListener {

        fun onQueuePause()

        fun onQueuePlay()

        fun onQueueUpdated(songRequests: List<ClientRequestData>)

        fun showLoadingScreen()

        fun closeHost()

        fun showClientConnected()

        fun showClientDisconnected()

        fun initiateView(title: String, songRequests: List<ClientRequestData>, isPlaying: Boolean)
    }

    private fun notifyPaused() {
        if (listener != null) {
            listener?.onQueuePause()
        }
    }

    private fun notifyPlayStarted() {
        if (listener != null) {
            listener?.onQueuePlay()
        }
    }

    private fun notifyLoading() {
        if (listener != null) {
            listener?.showLoadingScreen()
        }
    }

    fun setPlayQueueServiceListener(listener: HostServiceListener) {
        this.listener = listener
    }

    fun removePlayQueueServiceListener() {
        this.listener = null
    }

    fun unfollowQueuePlaylist() {
        // Unfollow the playlist created for SocialQ
        actionStream.accept(UnfollowPlaylist)

        shutdownQueue()
    }

    private fun shutdownQueue() {
        notifyClientsHostDisconnecting()

        listener?.closeHost()
        listener = null
    }


    fun savePlaylistAs(playlistName: String) {
        if (playlistName.isNotEmpty()) {
            Timber.d("Saving playlist as: $playlistName")
            actionStream.accept(UpdatePlaylistName(playlistName))
        }

        shutdownQueue()
    }

    fun hostRequestSong(uri: String) {
        if (uri.isNotEmpty()) {
            actionStream.accept(HostTrackRequested(uri))
        }
    }

    fun requestPlayPauseToggle() {
        actionStream.accept(RequestTogglePlayPause)
    }

    fun requestPlayNext() {
        actionStream.accept(RequestNext)
    }

    // TODO: Might need if activity is lost/recreated
//    fun requestInitiation() {
//        listener?.initiateView(queueTitle, createDisplayList(playlistTracks.subList(currentPlaylistIndex, playlistTracks.size)), isPlaying)
//    }

    private fun clearTrackInfoFromNotification(queueTitle: String) {
        mediaSession.setMetadata(null)

        // Update session playback state
        mediaSession.setPlaybackState(
                playbackStateBuilder.setState(
                        PlaybackStateCompat.STATE_STOPPED,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        0F
                ).build())

        @Suppress("RestrictedApi")
        notificationBuilder.mActions.clear()

        // Update notification data
        notificationBuilder.setContentTitle(String.format(getString(R.string.host_notification_content_text), queueTitle))
        notificationBuilder.setContentText("")
        notificationBuilder.setLargeIcon(null)
        notificationBuilder.setStyle(null)

        // Display updated notification
        notificationManager.notify(AppConstants.HOST_SERVICE_ID, notificationBuilder.build())
    }

    /**
     * Sets up metadata for displaying a track in the service notification and updates that notification.
     * WARNING: Notification manager and builder need to be setup before using this method.
     */
    private fun showTrackInNotification(trackToShow: Track) {
        // Update metadata for media session
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, trackToShow.album.name)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, trackToShow.name)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, trackToShow.name)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, DisplayUtils.getTrackArtistString(trackToShow))

        // Attempt to update album art in notification and metadata
        if (trackToShow.album.images.isNotEmpty()) {
           val task = GetBitmapTask(this)
            task.execute(trackToShow.album.images[0].url)
        }
        mediaSession.setMetadata(metaDataBuilder.build())

        // Update notification data
        notificationBuilder.setContentTitle(trackToShow.name)
        notificationBuilder.setContentText(DisplayUtils.getTrackArtistString(trackToShow))

        // Display updated notification
        notificationManager.notify(AppConstants.HOST_SERVICE_ID, notificationBuilder.build())
    }

    override fun displayBitmap(bitmap: Bitmap?) {
        // Set bitmap data for lock screen display
        metaDataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
        // Set bitmap data for notification
        notificationBuilder.setLargeIcon(bitmap)
        // Display updated notification
        notificationManager.notify(AppConstants.HOST_SERVICE_ID, notificationBuilder.build())
    }
}