package com.chrisf.socialq.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.chrisf.socialq.R
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.enums.NearbyDevicesMessage
import com.chrisf.socialq.enums.PayloadTransferUpdateStatus
import com.chrisf.socialq.extensions.addTo
import com.chrisf.socialq.model.AccessModel
import com.chrisf.socialq.model.ClientRequestData
import com.chrisf.socialq.model.SongRequestData
import com.chrisf.socialq.model.spotify.PlaylistTrack
import com.chrisf.socialq.model.spotify.UserPrivate
import com.chrisf.socialq.model.spotify.UserPublic
import com.chrisf.socialq.network.FrySpotifyService
import com.chrisf.socialq.userinterface.App
import com.chrisf.socialq.userinterface.activities.HostActivity
import com.chrisf.socialq.utils.ApplicationUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.gson.JsonArray
import com.spotify.sdk.android.player.*
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.lang.Exception
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class HostService : SpotifyAccessService(), ConnectionStateCallback, Player.NotificationCallback,
        Player.OperationCallback, AudioManager.OnAudioFocusChangeListener {

    companion object {
        val TAG = HostService::class.java.name
    }

    inner class HostServiceBinder : SpotifyAccessServiceBinder() {
        override fun getService(): HostService {
            return this@HostService
        }
    }

    // SERVICE ELEMENTS
    // Binder for talking from bound activity to host
    private val hostServiceBinder = HostServiceBinder()
    // Flag indicating if the service is bound to an activity
    private var isBound = false
    // Object listening for events from the service
    private var listener: HostServiceListener? = null

    // NOTIFICATION ELEMENTS
    // Reference to playback state and it's builder
    private lateinit var playbackState: PlaybackStateCompat
    private val playbackStateBuilder = PlaybackStateCompat.Builder()
    // Reference to notification style
    private val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()

    // NEARBY CONNECTION ELEMENTS
    // List of the client endpoints that are currently connected to the host service
    private var clientEndpoints = ArrayList<String>()
    // Flag indicating success of advertising (used during activity destruction)
    private var successfulAdvertisingFlag = false

    // AUDIO ELEMENTS
    // Reference to system audio manager
    private lateinit var audioManager: AudioManager
    // Flag to resume playback when audio focus is returned
    private var resumeOnAudioFocus = false
    // Reference to current audio focus state
    private var audioFocusState = -1
    // Member player object used for playing audio
    private var spotifyPlayer: SpotifyPlayer? = null
    // Integer to keep track of song index in the queue
    private var currentPlaylistIndex = 0
    // Boolean flag to store when when delivery is done
    private var audioDeliveryDoneFlag = true
    // Boolean flag to store if MetaData is incorrect
    private var incorrectMetaDataFlag = false
    // Boolean flag for if a pause event was requested by the user
    private var userRequestedPause = false
    // Boolean flag for if the player is active
    private var isPlayerActive = false
    // Boolean flag for indicating if the player is playing (used for view initiation)
    private var isPlaying = false

    // QUEUE SORTING/FAIR PLAY ELEMENTS
    // Boolean flag to store if queue should be "fair play"
    private var isQueueFairPlay: Boolean = false
    // List containing client song requests
    private val songRequests = mutableListOf<SongRequestData>()
    // Flag for storing if a base playlist has been loaded
    private var wasBasePlaylistLoaded = false
    // Cached value for newly added track index
    private var newTrackIndex = -1
    // List of shuffled tracks to be added to the playlist
    private val shuffledTracks = mutableListOf<PlaylistTrack>()

    // Title of the SocialQ
    private lateinit var queueTitle: String
    // ID of the base playlist to load
    private var basePlaylistId: String = ""
    // User object of the host's Spotify account
    private lateinit var hostUser: UserPrivate

    // Callback for successful/failed player connection
    private val connectivityCallback = object : Player.OperationCallback {
        override fun onSuccess() {
            Timber.d("Success!")
        }

        override fun onError(error: Error) {
            Timber.e("ERROR: $error")
        }
    }

    // Callback for media session calls (ex: media buttons)
    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            requestPlay()
        }

        override fun onSkipToNext() {
            requestPlayNext()
        }

        override fun onPause() {
            requestPause()
        }

        // TODO: May need this method for earlier versions of Android
//        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
//            if (mediaButtonEvent != null) {
//            }
//            return false
//        }
    }

    private val hostServiceBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                when (intent.action) {
                    AppConstants.BR_INTENT_ACCESS_TOKEN_UPDATED -> {
                        Timber.d("Access token has been refreshed, update player access token")
                        spotifyPlayer?.login(AccessModel.getAccessToken())
                    }
                    else -> {
                        // Not handling other actions here, do nothing
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                null -> {
                    Timber.d("Host service is being started")

                    // Set default for queue title
                    queueTitle = getString(R.string.queue_title_default_value)

                    // Check intent for storage of queue settings
                    if (intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY) != null) {
                        queueTitle = intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY)
                    }
                    isQueueFairPlay = intent.getBooleanExtra(AppConstants.FAIR_PLAY_KEY, resources.getBoolean(R.bool.fair_play_default))
                    basePlaylistId = intent.getStringExtra(AppConstants.BASE_PLAYLIST_ID_KEY)

                    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    // Initialize playback state, allow play, pause, play/pause toggle and next
                    playbackState = playbackStateBuilder
                            .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE
                                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                    or PlaybackStateCompat.ACTION_PLAY
                                    or PlaybackStateCompat.ACTION_PAUSE)
                            .build()

                    // Initialize media session
                    mediaSession = MediaSessionCompat(baseContext, AppConstants.HOST_MEDIA_SESSION_TAG)
                    mediaSession.isActive = true
                    mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS)
                    mediaSession.setCallback(mediaSessionCallback)
                    mediaSession.setPlaybackState(playbackState)

                    // Build notification and start foreground service
                    val token = mediaSession.sessionToken
                    if (token != null) {
                        // Create intent for touching foreground notification
                        val pendingIntent = PendingIntent.getActivity(
                                this,
                                0,
                                Intent(this, HostActivity::class.java),
                                0)

                        // Setup media style with media session token and displaying actions in compact view
                        mediaStyle.setMediaSession(token)

                        // Build foreground notification
                        notificationBuilder = NotificationCompat.Builder(this, App.CHANNEL_ID)
                                .setContentTitle(String.format(getString(R.string.host_notification_content_text), queueTitle))
                                .setSmallIcon(R.drawable.app_notification_icon)
                                .setContentIntent(pendingIntent)
                                .setColorized(true)
                                .setOnlyAlertOnce(true)
                                .setShowWhen(false)

                        // Register to receive access token update events
                        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(
                                hostServiceBroadcastReceiver, IntentFilter(AppConstants.BR_INTENT_ACCESS_TOKEN_UPDATED))

                        // Start service in the foreground
                        startForeground(AppConstants.HOST_SERVICE_ID, notificationBuilder.build())

                        initHost()
                    } else {
                        Timber.e("Something went wrong initializing the media session")

                        stopSelf()
                        return START_NOT_STICKY
                    }

                    // Let app object know that a service has been started
                    App.hasServiceBeenStarted = true
                }
                AppConstants.ACTION_REQUEST_PLAY_PAUSE -> {
                    if (spotifyPlayer?.playbackState?.isPlaying!!) {
                        requestPause()
                    } else {
                        requestPlay()
                    }
                }
                AppConstants.ACTION_REQUEST_NEXT -> {
                    requestPlayNext()
                }
                else -> {
                    Timber.e("Not handling action: ${intent.action}")
                }
            }
        } else {
            Timber.e("Intent for onStartCommand was null")
        }
        return START_NOT_STICKY
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

    private fun startNearbyAdvertising(queueTitle: String) {
        // Create advertising options (strategy)
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()

        // Build name of host. See AppConstants.NEARBY_HOST_NAME_FORMAT
        val ownerName = when {
            hostUser.display_name.isNullOrEmpty() -> {
                val id = hostUser.id
                if (id.isNullOrEmpty()) {
                    getString(R.string.unknown)
                } else {
                    id
                }
            }
            else -> {
                hostUser.display_name
            }
        }
        val isFairplayCharacter = when (isQueueFairPlay) {
            true -> {
                AppConstants.FAIR_PLAY_TRUE_CHARACTER
            }
            false -> {
                AppConstants.FAIR_PLAY_FALSE_CHARACTER
            }
        }
        val hostName = String.format(AppConstants.NEARBY_HOST_NAME_FORMAT, queueTitle, ownerName, isFairplayCharacter)

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
                        Timber.e("Failed to start advertising the host")
                        stopSelf()
                    }
                })
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

    override fun authorizationFailed() {
        Timber.d("Host service is ending due to authorization failure")

        // Stop advertising and alert clients we have disconnected
        if (successfulAdvertisingFlag) {
            Timber.d("Stop advertising host")

            Nearby.getConnectionsClient(applicationContext).stopAdvertising()
            Nearby.getConnectionsClient(applicationContext).stopAllEndpoints()
        }

        listener?.closeHost()

        // Let app know that the service has ended
        App.hasServiceBeenStarted = false
    }

    override fun onDestroy() {
        Timber.d("Host service is ending")

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

    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Nearby.getConnectionsClient(applicationContext).acceptConnection(endpointId, mPayloadCallback)
        }

        override fun onConnectionResult(endPoint: String, connectionResolution: ConnectionResolution) {
            when (connectionResolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Timber.d("Established connection with a client ($endPoint), initiate client")

                    // Notify activity a client connected
                    if (listener != null) {
                        listener?.showClientConnected()
                    }
                    clientEndpoints.add(endPoint)
                    initiateNewClient(endPoint)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> Timber.d("Client connection rejected")
                ConnectionsStatusCodes.STATUS_ERROR -> Timber.d("Error during client connection")
                else -> TODO("not implemented")
            }
        }

        override fun onDisconnected(endPoint: String) {
            Timber.d("Client $endPoint disconnected")

            // Notify activity a client disconnected
            if (listener != null) {
                listener?.showClientDisconnected()
            }
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

            spotifyApi.getUserById(clientId)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            {
                                var user = it.body()
                                if (user == null) {
                                    user = UserPublic(
                                            "Error",
                                            clientId,
                                            emptyList(),
                                            "Error",
                                            "Error"
                                    )
                                }
                                handleSongRequest(SongRequestData(songUri, user))
                            },
                            {
                                val user = UserPublic(
                                        "Error",
                                        clientId,
                                        emptyList(),
                                        "Error",
                                        "Error"
                                )
                                handleSongRequest(SongRequestData(songUri, user))
                            })
                    .addTo(subscriptions)

        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        audioFocusState = focusChange

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.d("Gained audio focus")

                // If flagged to resume audio, request play
                if (resumeOnAudioFocus) {
                    requestPlay()
                    resumeOnAudioFocus = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Timber.d("Lost audio focus, pause playback")

                // If we're currently playing audio, pause
                if (spotifyPlayer?.playbackState?.isPlaying!!) {
                    requestPause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Timber.d("Lost audio focus, focus should return, pause playback")

                // If we're currently playing audio, flag to resume when we regain audio focus and pause
                if (spotifyPlayer?.playbackState?.isPlaying!!) {
                    resumeOnAudioFocus = true
                    requestPause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Timber.d("Lost audio focus, should lower volume")

                //TODO: Lower player volume
            }
            else -> {
                Timber.e("Not handling this case")
            }
        }
    }

    private fun initHost() {
        if (playlistOwnerUserId.isEmpty()) {
            Timber.d("Initializing Host (init player, create playlist, load base playlist if selected")

            if (AccessModel.getCurrentUser() == null) {
                spotifyApi.getCurrentUser()
                        .subscribeOn(Schedulers.io())
                        .subscribe { response ->
                            if (response.body() != null) {
                                hostUser = response.body()!!
                                playlistOwnerUserId = hostUser.id
                                initPlayer(AccessModel.getAccessToken())
                                createPlaylistForQueue()
                            } else {
                                Timber.e("Error retrieving current user")
                            }
                        }
                        .addTo(subscriptions)

            } else {
                hostUser = AccessModel.getCurrentUser()
                playlistOwnerUserId = hostUser.id
                initPlayer(AccessModel.getAccessToken())
                createPlaylistForQueue()
            }
        }
    }

    private fun notifyClientsQueueUpdated() {
        if (currentPlaylistIndex >= 0) {
            for (endpointId: String in clientEndpoints) {
                Nearby.getConnectionsClient(this).sendPayload(endpointId,
                        Payload.fromBytes(String.format(NearbyDevicesMessage.CURRENTLY_PLAYING_UPDATE.messageFormat,
                                currentPlaylistIndex.toString()).toByteArray()))
            }
        }
    }

    private fun notifyClientsTrackWasAdded(newTrackIndex: Int) {
        if (newTrackIndex >= 0) {
            for (endpointId: String in clientEndpoints) {
                Nearby.getConnectionsClient(this).sendPayload(endpointId,
                        Payload.fromBytes(String.format(NearbyDevicesMessage.NEW_TRACK_ADDED.messageFormat,
                                newTrackIndex.toString()).toByteArray()))
            }
        }
    }

    private fun notifyClientsHostDisconnecting() {
        for (endpoint: String in clientEndpoints) {
            Nearby.getConnectionsClient(this).sendPayload(endpoint,
                    Payload.fromBytes(AppConstants.HOST_DISCONNECT_MESSAGE.toByteArray()))
        }
    }

    private fun initiateNewClient(client: Any) {
        if (clientEndpoints.contains(client.toString()) && playlist.id != null && playlistOwnerUserId.isNotEmpty()) {
            Timber.d("Sending host ID, playlist id, and current playing index to new client")
            Nearby.getConnectionsClient(this).sendPayload(client.toString(), Payload.fromBytes(
                    String.format(NearbyDevicesMessage.INITIATE_CLIENT.messageFormat,
                            playlistOwnerUserId,
                            playlist.id,
                            currentPlaylistIndex).toByteArray()))
        }
    }

    private fun initPlayer(accessToken: String) {
        // Setup Spotify player
        val playerConfig = Config(this, accessToken, AppConstants.CLIENT_ID)
        spotifyPlayer = Spotify.getPlayer(playerConfig, this, object : SpotifyPlayer.InitializationObserver {
            override fun onInitialized(player: SpotifyPlayer) {
                Timber.d("Player initialized")

                // Retrieve audio manager for managing audio focus
                audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

                player.setConnectivityStatus(connectivityCallback,
                        getNetworkConnectivity(this@HostService))
                player.addConnectionStateCallback(this@HostService)
                player.addNotificationCallback(this@HostService)
            }

            override fun onError(error: Throwable) {
                Timber.e("ERROR: Could not initialize player: %1s", error.message)
            }
        })
    }

    fun requestPlay() {
        Timber.d("PLAY REQUEST")
        if (audioFocusState == AudioManager.AUDIOFOCUS_GAIN) {
            handlePlay()
        } else {
            if (requestAudioFocus()) {
                // We have audio focus, request play
                handlePlay()
            } else {
                // If we did not receive audio focus, flag to start playing when audio focus is gained
                resumeOnAudioFocus = true
            }
        }
    }

    private fun handlePlay() {
        if (spotifyPlayer?.playbackState != null) {
            if (audioDeliveryDoneFlag) {
                if (currentPlaylistIndex < playlistTracks.size) {
                    // If audio has previously been completed (or never started)
                    // start the playlist at the current index
                    Timber.d("Audio previously finished.\nStarting playlist from index: $currentPlaylistIndex")
                    spotifyPlayer?.playUri(this, playlist.uri, currentPlaylistIndex, 0)
                    audioDeliveryDoneFlag = false
                    incorrectMetaDataFlag = false
                } else {
                    Timber.d("Nothing to play")
                }
            } else {
                Timber.d("Resuming player")
                if (!spotifyPlayer?.playbackState?.isPlaying!!) {
                    spotifyPlayer?.resume(this)
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        Timber.d("Sending audio focus request")

        var audioFocusResult = -1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(this)
                    .build()
            audioFocusResult = audioManager.requestAudioFocus(request)
        } else {
            audioFocusResult = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }

        if (audioFocusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Timber.d("Audio focus was granted")
        } else {
            Timber.d("Audio focus was NOT granted")
        }

        return audioFocusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun requestPause() {
        Timber.d("PAUSE REQUEST")
        userRequestedPause = true
        spotifyPlayer?.pause(this)
    }

    fun requestPlayNext() {
        Timber.d("NEXT REQUEST")
        if (!audioDeliveryDoneFlag) {
            // Don't allow a skip when we're waiting on a new track to be queued
            spotifyPlayer?.skipToNext(this)
        } else {
            Timber.d("Can't go to next track")
        }
    }

    override fun onPlaybackEvent(playerEvent: PlayerEvent) {
        Timber.d("New playback event: %1s", playerEvent.name)

        when (playerEvent) {
            PlayerEvent.kSpPlaybackNotifyPlay -> {
                Timber.d("Player has started playing")

                // Started/resumed playing, reset flag for resume audio on focus
                resumeOnAudioFocus = false

                // Update session playback state
                mediaSession.setPlaybackState(playbackStateBuilder
                        .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1F)
                        .build())

                // Update notification builder action buttons for playing
                addActionsToNotificationBuilder(true)
                notificationManager.notify(AppConstants.HOST_SERVICE_ID, notificationBuilder.build())

                isPlaying = true
                notifyPlayStarted()
            }
            PlayerEvent.kSpPlaybackNotifyContextChanged -> {
            }
            PlayerEvent.kSpPlaybackNotifyPause -> {
                Timber.d("Player has paused")

                // Update session playback state
                mediaSession.setPlaybackState(playbackStateBuilder
                        .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0F)
                        .build())

                // Update notification builder with action buttons for paused if we're paused with tracks remaining
                if (currentPlaylistIndex < playlistTracks.size) {
                    addActionsToNotificationBuilder(false)
                    notificationManager.notify(AppConstants.HOST_SERVICE_ID, notificationBuilder.build())
                }

                // If meta data is incorrect we won't actually pause (unless user requested pause)
                isPlaying = false
                if (userRequestedPause || !incorrectMetaDataFlag) {
                    notifyPaused()
                    userRequestedPause = false
                }
            }
            PlayerEvent.kSpPlaybackNotifyTrackChanged -> {
                logMetaData()
            }
            PlayerEvent.kSpPlaybackNotifyMetadataChanged -> {
                logMetaData()
            }
            PlayerEvent.kSpPlaybackNotifyTrackDelivered,
            PlayerEvent.kSpPlaybackNotifyNext -> {
                // Track has changed, remove top track from queue list
                Timber.d("Player has moved to next track (next/track complete)")
                // If we are about to play the wrong song we need to skip twice and not increment current playing index
                // due to the handling of the queued track
                if (incorrectMetaDataFlag) {
                    Timber.d("Skipping queued placeholder, don't update index or notify we're skipping")

                    incorrectMetaDataFlag = false
                    spotifyPlayer?.skipToNext(this)
                } else {
                    if (songRequests.size > 0) {
                        songRequests.removeAt(0)
                    }
                    currentPlaylistIndex++
                    Timber.d("UPDATING CURRENT PLAYING INDEX TO: $currentPlaylistIndex")
                    notifyQueueChanged()

                    Timber.d("Updating notification")
                    if (currentPlaylistIndex < playlistTracks.size) {
                        showTrackInNotification(playlistTracks[currentPlaylistIndex].track, true)
                    } else {
                        clearTrackInfoFromNotification()
                    }
                }
            }
            PlayerEvent.kSpPlaybackNotifyAudioDeliveryDone -> {
                // Current queue playlist has finished
                Timber.d("Player has finished playing audio")
                audioDeliveryDoneFlag = true

                // Stop service if we're not bound and out of songs
                if (!isBound) {
                    Timber.d("Out of songs and not bound to host activity. Shutting down service.")
                    stopSelf()
                }
            }
            PlayerEvent.kSpPlaybackNotifyBecameActive -> {
                // Player became active
                Timber.d("Player is active")

                isPlayerActive = true
            }
            else -> {
            }
        }// Do nothing or future implementation
    }

    private fun logMetaData() {
        // Log previous/current/next tracks
        val metadata = spotifyPlayer?.metadata
        var previousTrack: String? = null
        var nextTrack: String? = null
        var currentTrack: String? = null
        if (metadata?.prevTrack != null) {
            previousTrack = metadata.prevTrack.name
        }
        if (metadata?.currentTrack != null) {
            currentTrack = metadata.currentTrack.name
        }
        if (metadata?.nextTrack != null) {
            nextTrack = metadata.nextTrack.name
        }
        Timber.i("META DATA:\nFinished/Skipped: " + previousTrack
                + "\nNow Playing : " + currentTrack
                + "\nNext Track: " + nextTrack)
    }

    override fun onPlaybackError(error: Error) {
        Timber.e("ERROR: New playback error: %1s", error.name)
    }

    override fun onSuccess() {
        Timber.d("Great Success!")
    }

    override fun onError(error: Error) {
        Timber.e("ERROR: Playback error received : Error - %1s", error.name)
    }

    /**
     * Registering for connectivity changes in Android does not actually deliver them to
     * us in the delivered intent.
     *
     * @param context Android context
     * @return Connectivity state to be passed to the SDK
     */
    private fun getNetworkConnectivity(context: Context): Connectivity {
        val connectivityManager: ConnectivityManager?
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (connectivityManager != null) {
            val activeNetwork = connectivityManager.activeNetworkInfo
            return if (activeNetwork != null && activeNetwork.isConnected) {
                Connectivity.fromNetworkType(activeNetwork.type)
            } else {
                Connectivity.OFFLINE
            }
        }
        return Connectivity.OFFLINE
    }

    // START CONNECTION CALLBACK METHODS
    override fun onLoggedIn() {
        Timber.d("Logged In! Player can be used")
        // TODO: Show loading screen until this call
    }

    override fun onLoggedOut() {
        Timber.d("Logged Out! Player can no longer be used")
    }

    override fun onLoginFailed(error: Error) {
        Timber.e("ERROR: Login Error: %1s", error.name)
        // TODO: Should probably try to log into player again
    }

    override fun onTemporaryError() {
        Timber.e("ERROR: Temporary Error")
    }

    override fun onConnectionMessage(s: String) {
        Timber.d("Connection Message: $s")
    }
    // END CONNECTION CALLBACK METHODS

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

    private fun notifyQueueChanged() {
        if (listener != null) {
            listener?.onQueueUpdated(createDisplayList(playlistTracks.subList(currentPlaylistIndex, playlistTracks.size)))
        }
        notifyClientsQueueUpdated()
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

    private fun createPlaylistForQueue() {
        Timber.d("Creating playlist for the SocialQ")
        spotifyApi.createSocialQPlaylist(playlistOwnerUserId)
                .subscribeOn(Schedulers.io())
                .subscribe({ response ->
                    if (response.body() == null) {
                        Timber.e("Created playlist returned null")
                        createPlaylistForQueue()
                    } else {
                        Timber.d("Successfully created playlist for queue")
                        playlist = response.body()!!

                        // If we have a base playlist ID load it up
                        if (basePlaylistId.isNotEmpty()) {
                            loadBasePlaylist(basePlaylistId)
                            basePlaylistId = ""
                        } else {
                            startNearbyAdvertising(queueTitle)
                        }
                    }
                }, {
                    // TODO: Stop after too many errors?
                    Timber.e(it)
                    createPlaylistForQueue()
                })
                .addTo(subscriptions)
    }

    private fun loadBasePlaylist(playlistId: String) {
        Timber.d("Loading base playlist with ID: $playlistId")

        wasBasePlaylistLoaded = true

        // TODO: Shouldn't assume success
        getBasePlaylistTracks(playlistId)
    }

    private fun getBasePlaylistTracks(playlistId: String, offset: Int = 0) {
        spotifyApi.getPlaylistTracks(playlistId, 50, offset)
                .subscribeOn(Schedulers.io())
                .subscribe { response ->
                    if (response.body() == null) {
                        // TODO: Try again?
                    } else {
                        val basePlaylistTracks = response.body()!!
                        for (playlistTrack in basePlaylistTracks.items) {
                            if (!playlistTrack.track.is_local) {
                                playlistTracks.add(playlistTrack)
                            }
                        }

                        if (basePlaylistTracks.next != null) {
                            val nextOffset = basePlaylistTracks.offset + basePlaylistTracks.items.size
                            getBasePlaylistTracks(playlistId, nextOffset)
                        } else {
                            // Shuffle entire track list
                            val randomGenerator = Random()
                            while (playlistTracks.size > 0) {
                                val trackIndexToAdd = randomGenerator.nextInt(playlistTracks.size)

                                Timber.d("123 - Adding to shuffled tracks")
                                shuffledTracks.add(playlistTracks.removeAt(trackIndexToAdd))
                            }
                            addPlaylistTracks()
                        }
                    }
                }
                .addTo(subscriptions)
    }

    private fun addPlaylistTracks() {
        // Adding with base user ensure host added tracks are sorted within the base playlist
        val baseUser = UserPublic(
                resources.getString(R.string.base_playlist),
                AppConstants.BASE_USER_ID,
                emptyList(),
                "",
                ""
        )

        // Add tracks (100 at a time) to playlist and add request date
        Timber.d("123 -  Shuffled tracks size = ${shuffledTracks.size}")
        while (shuffledTracks.size > 0) {
            val urisArray = JsonArray()
            // Can only add max 100 tracks
            for (i in 0..99) {
                if (shuffledTracks.size == 0) {
                    break
                }
                val track = shuffledTracks.removeAt(0)
                val requestData = SongRequestData(track.track.uri, baseUser)
                songRequests.add(requestData)

                urisArray.add(track.track.uri)
            }

            // TODO: Wait for success responses before sending more track to ensure order
            Timber.d("123 - Sending add request for $urisArray")
            spotifyApi.addTracksToPlaylist(
                    playlist.id,
                    FrySpotifyService.getAddTracksToPlaylistBody(urisArray)
            )
                    .subscribe({
                        Timber.d(it.body().toString())
                        if (shuffledTracks.isEmpty()) {
                            refreshPlaylist()
                        }
                    }, {
                        Timber.e(it)
                    })
                    .addTo(subscriptions)
        }
    }

    private fun handleSongRequest(songRequest: SongRequestData?) {
        if (songRequest != null && !songRequest.uri.isEmpty()) {
            Timber.d("Received request for URI: " + songRequest.uri + ", from User ID: " + songRequest.user.id)

            // Add track to request list
            songRequests.add(songRequest)

            val willNextSongBeWrong: Boolean
            // Add track and figure out if our next song will be wrong
            if (isQueueFairPlay) {
                willNextSongBeWrong = addNewTrackFairplay(songRequest)
            } else {
                willNextSongBeWrong = addNewTrack(songRequest)
            }

            // If already flagged for skip don't queue again, player seems to fix it's playlist position
            // when the original song queued is skipped (may not match the true "next" song)
            if (willNextSongBeWrong && !incorrectMetaDataFlag) {
                // If we changed the next track notify service next track will be incorrect
                incorrectMetaDataFlag = true
                spotifyPlayer?.queue(this, songRequest.uri)
            }

            if (newTrackIndex < 0 || newTrackIndex > playlistTracks.size) {
                Timber.e("Something went wrong, new track index is invalid")
            } else {
                pullNewTrack(newTrackIndex)
                newTrackIndex = -1
            }
        }
    }

    /**
     * Adds a song to end of the referenced playlist
     *
     * @param uri - id of the track to be added
     */
    private fun addTrackToPlaylist(uri: String) {
        addTrackToPlaylistPosition(uri, -1)
    }

    /**
     * Adds a song to the referenced playlist at the given position (or end if not specified)
     *
     * @param uri      - id of the track to be added
     * @param position - position of the track to be added (if less than 0, track placed at end of playlist)
     */
    private fun addTrackToPlaylistPosition(uri: String, position: Int) {

        val newTrackPosition = if (position < 0) null else position
        spotifyApi.addTrackToPlaylist(playlist.id, uri, newTrackPosition)
                .subscribe({
                    Timber.d(it.body().toString())
                }, {
                    Timber.e(it)
                })
                .addTo(subscriptions)
    }

    /**
     * Injects most recently added track to fairplay position
     *
     * @param songRequest - Song request containing requestee and track info
     * @return - Boolean flag for if the track was added at the next position
     */
    private fun addNewTrackFairplay(songRequest: SongRequestData): Boolean {
        // Position of new track needs to go before first repeat that doesn't have a song of the requestee inside
        // EX (Requestee = 3): 1 -> 2 -> 3 -> 1 -> 2 -> 1  New 3 track goes before 3rd track by 1
        // PLAYLIST RESULT   : 1 -> 2 -> 3 -> 1 -> 2 -> 3 -> 1
        var newTrackPosition = 0

        val clientRepeatHash = HashMap<String, Boolean>()

        // Start inspecting song requests
        while (newTrackPosition < songRequests.size) {
            val currentRequestUserId = songRequests[newTrackPosition].user.id

            // Base playlist track. Check if we can replace it
            if (currentRequestUserId == AppConstants.BASE_USER_ID) {
                // If player is not active we can add a track at index 0 (replace base playlist)
                // because we haven't started the playlist. Else don't cause the base playlist
                // track may currently be playing
                if (newTrackPosition == 0 && !isPlayerActive || newTrackPosition > 0) {
                    // We want to keep user tracks above base playlist tracks.  Use base playlist
                    // as a fall back.
                    break
                }
            }

            if (currentRequestUserId == songRequest.user.id) {
                // If we found a requestee track set open repeats to true (found requestee track)
                for (mapEntry in clientRepeatHash.entries) {
                    mapEntry.setValue(true)
                }
            } else {
                // Found a request NOT from the requestee client
                if (clientRepeatHash.containsKey(currentRequestUserId)) {
                    // Client already contained in hash (repeat)
                    if (clientRepeatHash[currentRequestUserId]!!) {
                        // If repeat contained requestee track (true flag) reset to false
                        clientRepeatHash[currentRequestUserId] = false
                    } else {
                        // Client already contained in hash (repeat) and does not have a requestee track
                        // We have a repeat with no requestee song in between
                        break
                    }
                } else {
                    // Add new client to the hash
                    clientRepeatHash[currentRequestUserId] = false
                }
            }
            newTrackPosition++
        }

        return injectTrackToPosition(newTrackPosition, songRequest)
    }

    /**
     * Injects most recently added track to position before base playlist (if it exists)
     *
     * @param songRequest - Song request containing requestee and track info
     * @return - Boolean flag for if the track was added at the next position
     */
    private fun addNewTrack(songRequest: SongRequestData): Boolean {
        if (wasBasePlaylistLoaded) {
            // Position of new track needs to go before first base playlist track

            // Start inspecting song requests
            var newTrackPosition = 0
            while (newTrackPosition < songRequests.size) {
                val currentRequestUserId = songRequests[newTrackPosition].user.id

                // Base playlist track. Check if we can replace it
                if (currentRequestUserId == AppConstants.BASE_USER_ID) {
                    // If player is not active we can add a track at index 0 (replace base playlist)
                    // because we haven't started the playlist. Else don't cause the base playlist
                    // track may currently be playing
                    if (newTrackPosition == 0 && !isPlayerActive || newTrackPosition > 0) {
                        // We want to keep user tracks above base playlist tracks.  Use base playlist
                        // as a fall back.
                        break
                    }
                }
                newTrackPosition++
            }

            return injectTrackToPosition(newTrackPosition, songRequest)
        } else {
            // Cache new track index
            newTrackIndex = playlistTracks.size

            addTrackToPlaylist(songRequest.uri)
            return songRequests.size == 2
        }
    }

    private fun injectTrackToPosition(newTrackPosition: Int, songRequest: SongRequestData): Boolean {
        if (newTrackPosition == songRequests.size) {
            // Cache new track index
            newTrackIndex = playlistTracks.size

            // No base playlist track found add track to end of playlist
            Timber.d("Adding track to end of playlist")
            addTrackToPlaylist(songRequest.uri)
            // Return true if the song being added is next (request size of 2)
            return songRequests.size == 1 || songRequests.size == 2
        } else if (newTrackPosition > songRequests.size) {
            // Should not be possible
            Timber.e("INVALID NEW TRACK POSITION INDEX")

            // Cache new track index (as invalid)
            newTrackIndex = -1
            return false
        } else {
            // If new track position is not equal or greater than song request size we need to move it
            // Inject song request data to new position
            songRequests.add(newTrackPosition, songRequests.removeAt(songRequests.size - 1))

            Timber.d("Adding new track at playlist index: " + (newTrackPosition + currentPlaylistIndex))
            addTrackToPlaylistPosition(songRequest.uri, newTrackPosition + currentPlaylistIndex)

            // Cache new track index
            newTrackIndex = newTrackPosition + currentPlaylistIndex

            // Return true if we're moving the added track to the "next" position
            return newTrackPosition == 1
        }
    }

    fun unfollowQueuePlaylist() {
        // Unfollow the playlist created for SocialQ
        if (playlistOwnerUserId != null) {
            Timber.d("Unfollowing playlist created for the SocialQ")

            spotifyApi.unfollowPlaylist(playlist.id)
                    .subscribe({
                        if (it.code() == 200) {
                            Timber.d("Successfully unfollowed playlist")
                        } else {
                            Timber.e("Error Unfollowing playlist")
                        }
                    }, {
                        Timber.e(it)
                    })
                    .addTo(subscriptions)

            shutdownQueue()
        }
    }

    private fun shutdownQueue() {
        notifyClientsHostDisconnecting()
        spotifyPlayer?.logout()
        try {
            Timber.d("Releasing spotify player resource")
            Spotify.awaitDestroyPlayer(this@HostService, 10000, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            e.printStackTrace()
            Timber.e("Error releasing spotify player resource, hard shutting down")
            Spotify.destroyPlayer(this@HostService)
        }

        listener?.closeHost()
        listener = null
    }


    private fun createDisplayList(trackList: List<PlaylistTrack>): List<ClientRequestData> {
        val displayList = ArrayList<ClientRequestData>()

        // TODO: This does not guarantee correct display.  If player does not play songs in
        // correct order, the incorrect users may be displayed
        var i = 0
        while (i < trackList.size && i < songRequests.size) {
            displayList.add(ClientRequestData(trackList[i], songRequests[i].user))
            i++
        }

        return displayList
    }

    fun savePlaylistAs(playlistName: String) {
        // Create body parameters for modifying playlist details
        val saveName = if (playlistName.isEmpty()) getString(R.string.default_playlist_name) else playlistName

        Timber.d("Saving playlist as: $saveName")
        spotifyApi.changePlaylistDetails(playlist.id, FrySpotifyService.getPlaylistDetailsBody(saveName))

        shutdownQueue()
    }

    fun hostRequestSong(uri: String) {
        if (uri.isNotEmpty()) {
            val user = UserPublic(
                    hostUser.display_name,
                    hostUser.id,
                    emptyList(),
                    hostUser.type,
                    hostUser.uri
            )
            handleSongRequest(SongRequestData(uri, user))
        }
    }

    fun requestInitiation() {
        listener?.initiateView(queueTitle, createDisplayList(playlistTracks.subList(currentPlaylistIndex, playlistTracks.size)), isPlaying)
    }

    override fun playlistRefreshComplete() {
        // Start advertising since base playlist load is complete
        startNearbyAdvertising(queueTitle)

        // If a base playlist has been loaded we should display the first track
        if (playlistTracks.size > 0) {
            addActionsToNotificationBuilder(false)
            showTrackInNotification(playlistTracks[0].track, true)
        }
        notifyQueueChanged()
    }

    override fun newTrackRetrievalComplete(newTrackIndex: Int) {
        if (listener != null) {
            listener?.onQueueUpdated(createDisplayList(playlistTracks.subList(currentPlaylistIndex, playlistTracks.size)))
        }

        if (playlistTracks.size - currentPlaylistIndex == 1) {
            addActionsToNotificationBuilder(false)
            showTrackInNotification(playlistTracks[currentPlaylistIndex].track, true)
        }

        notifyClientsTrackWasAdded(newTrackIndex)
    }

    private fun clearTrackInfoFromNotification() {
        mediaSession.setMetadata(null)

        // Update session playback state
        mediaSession.setPlaybackState(playbackStateBuilder
                .setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0F)
                .build())

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

}