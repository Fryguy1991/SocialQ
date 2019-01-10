package com.chrisfry.socialq.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.NearbyDevicesMessage
import com.chrisfry.socialq.enums.PayloadTransferUpdateStatus
import com.chrisfry.socialq.model.AccessModel
import com.chrisfry.socialq.model.ClientRequestData
import com.chrisfry.socialq.model.SongRequestData
import com.chrisfry.socialq.userinterface.App
import com.chrisfry.socialq.userinterface.activities.HostActivity
import com.chrisfry.socialq.utils.ApplicationUtils
import com.chrisfry.socialq.utils.DisplayUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.gson.JsonArray
import com.spotify.sdk.android.player.*
import kaaes.spotify.webapi.android.SpotifyCallback
import kaaes.spotify.webapi.android.SpotifyError
import kaaes.spotify.webapi.android.SpotifyService
import kaaes.spotify.webapi.android.models.*
import retrofit.client.Response
import java.io.IOException
import java.lang.Exception
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class HostService : SpotifyAccessService(), ConnectionStateCallback, Player.NotificationCallback,
        Player.OperationCallback {
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
    // Reference to notification manager
    private lateinit var notificationManager: NotificationManager
    // Builder for foreground notification
    private lateinit var notificationBuilder: NotificationCompat.Builder
    // Reference to notification layouts
    private lateinit var notificationLayout: RemoteViews
    private lateinit var notificationLayoutExpanded: RemoteViews
    // Reference to media session
    private lateinit var mediaSession: MediaSessionCompat
    // Reference to meta data builder
    private val metaDataBuilder = MediaMetadataCompat.Builder()
    // Reference to playback state and it's builder
    private lateinit var playbackState: PlaybackStateCompat
    private val playbackStateBuilder = PlaybackStateCompat.Builder()

    // NEARBY CONNECTION ELEMENTS
    // List of the client endpoints that are currently connected to the host service
    private var clientEndpoints = ArrayList<String>()
    // Flag indicating success of advertising (used during activity destruction)
    private var successfulAdvertisingFlag = false

    // PLAYER ELEMENTS
    // Member player object used for playing audio
    private lateinit var spotifyPlayer: SpotifyPlayer
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

    // List of user's playlist
    private val currentUserPlaylists = mutableListOf<PlaylistSimple>()
    // Title of the SocialQ
    private lateinit var queueTitle: String
    // ID of the base playlist to load
    private var basePlaylistId: String = ""
    // User object of the host's Spotify account
    private lateinit var hostUser: UserPublic

    // Callback for successful/failed player connection
    private val connectivityCallback = object : Player.OperationCallback {
        override fun onSuccess() {
            Log.d(TAG, "Success!")
        }

        override fun onError(error: Error) {
            Log.e(TAG, "ERROR: $error")
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Host service is being started")

        // Set defaults for queue settings
        queueTitle = getString(R.string.queue_title_default_value)
        isQueueFairPlay = resources.getBoolean(R.bool.fair_play_default)

        // If intent is not null we can check it for storage of queue settings
        if (intent != null) {
            if (intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY) != null) {
                queueTitle = intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY)
            }

            isQueueFairPlay = intent.getBooleanExtra(AppConstants.FAIR_PLAY_KEY, resources.getBoolean(R.bool.fair_play_default))
        }

        // Start service in the foreground
        val notificationIntent = Intent(this, HostActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Initialize playback state, allow play, pause and next
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
            notificationBuilder = NotificationCompat.Builder(this, App.CHANNEL_ID)
                    .setContentTitle(String.format(getString(R.string.host_notification_content_text), queueTitle))
                    .setSmallIcon(R.drawable.notification_icon)
                    .setContentIntent(pendingIntent)
                    .setColorized(true)
                    .setOnlyAlertOnce(true)
                    .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(token))
                    .setShowWhen(false)

            startForeground(AppConstants.HOST_SERVICE_ID, notificationBuilder.build())
        } else {
            Log.e(TAG, "Something went wrong initializing the media session")

            stopSelf()
            return START_NOT_STICKY
        }

        // Request authorization code for Spotify
        requestHostAuthorization()

        // Let app object know that a service has been started
        App.hasServiceBeenStarted = true

        return START_NOT_STICKY
    }

    private fun startNearbyAdvertising(queueTitle: String) {
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
                        successfulAdvertisingFlag = true

                        checkIfUserHasPlaylists()
                    }
                })
                .addOnFailureListener(object : OnFailureListener {
                    override fun onFailure(p0: Exception) {
                        Log.e(TAG, "Failed to start advertising the host")
                        stopSelf()
                    }
                })
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "Host service is being bound")
        isBound = true
        return hostServiceBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Host service is completely unbound")
        isBound = false
        return true
    }

    override fun onRebind(intent: Intent?) {
        Log.d(TAG, "Host service is being rebound")
        isBound = true
    }

    override fun onDestroy() {
        Log.d(TAG, "Host service is ending")

        // Stop advertising and alert clients we have disconnected
        if (successfulAdvertisingFlag) {
            Nearby.getConnectionsClient(applicationContext).stopAdvertising()
            Nearby.getConnectionsClient(applicationContext).stopAllEndpoints()
        }

        // Let app know that the service has ended
        App.hasServiceBeenStarted = false

        super.onDestroy()
    }

    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Nearby.getConnectionsClient(applicationContext).acceptConnection(endpointId, mPayloadCallback)
        }

        override fun onConnectionResult(endPoint: String, connectionResolution: ConnectionResolution) {
            when (connectionResolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Established connection with a client ($endPoint), initiate client")

                    // Notify activity a client connected
                    if (listener != null) {
                        listener?.showClientConnected()
                    }
                    clientEndpoints.add(endPoint)
                    initiateNewClient(endPoint)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> Log.d(TAG, "Client connection rejected")
                ConnectionsStatusCodes.STATUS_ERROR -> Log.d(TAG, "Error during client connection")
                else -> TODO("not implemented")
            }
        }

        override fun onDisconnected(endPoint: String) {
            Log.d(TAG, "Client $endPoint disconnected")

            // Notify activity a client disconnected
            if (listener != null) {
                listener?.showClientDisconnected()
            }
            clientEndpoints.remove(endPoint)
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
                    NearbyDevicesMessage.QUEUE_UPDATE,
                    NearbyDevicesMessage.INITIATE_CLIENT,
                    NearbyDevicesMessage.HOST_DISCONNECTING -> {
                        Log.e(TAG, "Hosts should not receive $payloadType messages")
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
        val matcher = Pattern.compile(AppConstants.FULL_SONG_REQUEST_REGEX).matcher(payloadString)
        // Ensure a proper format has been sent for the track request
        if (matcher.find()) {
            // Extract track URI and client ID
            val songUri = matcher.group(1)
            val clientId = matcher.group(2)

            return SongRequestData(songUri, spotifyService.getUser(clientId))
        }
        return SongRequestData("", UserPublic())
    }

    override fun initSpotifyElements(accessToken: String) {
        super.initSpotifyElements(accessToken)

        if (playlistOwnerUserId.isEmpty()) {
            Log.d(TAG, "Receiving Spotify access for the first time. Retrieve current user, " +
                    "create playlist and check for user playlist for base")
            hostUser = spotifyService.me
            playlistOwnerUserId = hostUser.id
            startNearbyAdvertising(queueTitle)
            initPlayer(AccessModel.getAccessToken())
        } else {
            Log.d(TAG, "Updating player's access token")
            spotifyPlayer.login(accessToken)
        }
    }


    private fun checkIfUserHasPlaylists() {
        Log.d(TAG, "Pulling playlist data for ${hostUser.display_name}")

        val options = HashMap<String, Any>()
        options[SpotifyService.LIMIT] = AppConstants.PLAYLIST_LIMIT

        if (playlistOwnerUserId != null) {
            spotifyService.getPlaylists(playlistOwnerUserId, options, userPlaylistsCallback)
        }
    }

    private fun notifyClientsQueueUpdated() {
        if (currentPlaylistIndex >= 0) {
            for (endpointId: String in clientEndpoints) {
                Nearby.getConnectionsClient(this).sendPayload(endpointId,
                        Payload.fromBytes(String.format(NearbyDevicesMessage.QUEUE_UPDATE.messageFormat,
                                currentPlaylistIndex.toString()).toByteArray()))
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
            Log.d(TAG, "Sending host ID, playlist id, and current playing index to new client")
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
                Log.d(TAG, "Player initialized")
                player.setConnectivityStatus(connectivityCallback,
                        getNetworkConnectivity(this@HostService))
                player.addConnectionStateCallback(this@HostService)
                player.addNotificationCallback(this@HostService)
            }

            override fun onError(error: Throwable) {
                Log.e(TAG, "ERROR: Could not initialize player: " + error.message)
            }
        })
    }

    fun requestPlay() {
        Log.d(TAG, "PLAY REQUEST")
        if (spotifyPlayer.playbackState != null) {
            if (audioDeliveryDoneFlag) {
                if (currentPlaylistIndex < playlistTracks.size) {
                    // If audio has previously been completed (or never started)
                    // start the playlist at the current index
                    Log.d(TAG, "Audio previously finished.\nStarting playlist from index: $currentPlaylistIndex")
                    spotifyPlayer.playUri(this, playlist.uri, currentPlaylistIndex, 0)
                    audioDeliveryDoneFlag = false
                    incorrectMetaDataFlag = false
                } else {
                    Log.d(TAG, "Nothing to play")
                }
            } else {
                Log.d(TAG, "Resuming player")
                if (!spotifyPlayer.playbackState.isPlaying) {
                    spotifyPlayer.resume(this)
                }
            }
        }
    }

    fun requestPause() {
        Log.d(TAG, "PAUSE REQUEST")
        userRequestedPause = true
        spotifyPlayer.pause(this)
    }

    fun requestPlayNext() {
        Log.d(TAG, "NEXT REQUEST")
        if (!audioDeliveryDoneFlag) {
            // Don't allow a skip when we're waiting on a new track to be queued
            spotifyPlayer.skipToNext(this)
        } else {
            Log.d(TAG, "Can't go to next track")
        }
    }

    override fun onPlaybackEvent(playerEvent: PlayerEvent) {
        Log.d(TAG, "New playback event: " + playerEvent.name)

        when (playerEvent) {
            PlayerEvent.kSpPlaybackNotifyPlay -> {
                Log.d(TAG, "Player has started playing")

                // Update session playback state
                mediaSession.setPlaybackState(playbackStateBuilder
                        .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1F)
                        .build())
                isPlaying = true
                notifyPlayStarted()
            }
            PlayerEvent.kSpPlaybackNotifyContextChanged -> {
            }
            PlayerEvent.kSpPlaybackNotifyPause -> {
                Log.d(TAG, "Player has paused")

                // Update session playback state
                mediaSession.setPlaybackState(playbackStateBuilder
                        .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0F)
                        .build())

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
                Log.d(TAG, "Player has moved to next track (next/track complete)")
                // If we are about to play the wrong song we need to skip twice and not increment current playing index
                // due to the handling of the queued track
                if (incorrectMetaDataFlag) {
                    Log.d(TAG, "Skipping queued placeholder, don't update index or notify we're skipping")

                    incorrectMetaDataFlag = false
                    spotifyPlayer.skipToNext(this)
                } else {
                    if (songRequests.size > 0) {
                        songRequests.removeAt(0)
                    }
                    currentPlaylistIndex++
                    Log.d(TAG, "UPDATING CURRENT PLAYING INDEX TO: $currentPlaylistIndex")
                    notifyQueueChanged()

                    Log.d(TAG, "Updating notification")
                    if (currentPlaylistIndex < playlistTracks.size) {
                        showTrackInNotification(playlistTracks[currentPlaylistIndex].track)
                    } else {
                        // TODO: Don't show track info anymore in notification/session metadata
                    }
                }
            }
            PlayerEvent.kSpPlaybackNotifyAudioDeliveryDone -> {
                // Current queue playlist has finished
                Log.d(TAG, "Player has finished playing audio")
                audioDeliveryDoneFlag = true

                // Stop service if we're not bound and out of songs
                if (!isBound) {
                    Log.d(TAG, "Out of songs and not bound to host activity. Shutting down service.")
                    stopSelf()
                }
            }
            PlayerEvent.kSpPlaybackNotifyBecameActive -> {
                // Player became active
                Log.d(TAG, "Player is active")

                isPlayerActive = true
            }
            else -> {
            }
        }// Do nothing or future implementation
    }

    private fun logMetaData() {
        // Log previous/current/next tracks
        val metadata = spotifyPlayer.metadata
        var previousTrack: String? = null
        var nextTrack: String? = null
        var currentTrack: String? = null
        if (metadata.prevTrack != null) {
            previousTrack = metadata.prevTrack.name
        }
        if (metadata.currentTrack != null) {
            currentTrack = metadata.currentTrack.name
        }
        if (metadata.nextTrack != null) {
            nextTrack = metadata.nextTrack.name
        }
        Log.i(TAG, "META DATA:\nFinished/Skipped: " + previousTrack
                + "\nNow Playing : " + currentTrack
                + "\nNext Track: " + nextTrack)
    }

    override fun onPlaybackError(error: Error) {
        Log.e(TAG, "ERROR: New playback error: " + error.name)
    }

    override fun onSuccess() {
        Log.d(TAG, "Great Success!")
    }

    override fun onError(error: Error) {
        Log.e(TAG, "ERROR: Playback error received : Error - " + error.name)
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
        Log.d(TAG, "Logged In! Player can be used")
        // TODO: Show loading screen until this call
    }

    override fun onLoggedOut() {
        Log.d(TAG, "Logged Out! Player can no longer be used")
    }

    override fun onLoginFailed(error: Error) {
        Log.e(TAG, "ERROR: Login Error: " + error.name)
        // TODO: Should probably try to log into player again
    }

    override fun onTemporaryError() {
        Log.e(TAG, "ERROR: Temporary Error")
    }

    override fun onConnectionMessage(s: String) {
        Log.d(TAG, "Connection Message: $s")
    }
    // END CONNECTION CALLBACK METHODS

    // Inner interface used to cast listeners for service events
    interface HostServiceListener {

        fun onQueuePause()

        fun onQueuePlay()

        fun onQueueUpdated(songRequests: List<ClientRequestData>)

        fun showBasePlaylistDialog(playlists: List<PlaylistSimple>)

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
        // Create body parameters for new playlist
        val playlistParameters = HashMap<String, Any>()
        playlistParameters["name"] = getString(R.string.default_playlist_name)
        playlistParameters["public"] = true
        playlistParameters["collaborative"] = false
        playlistParameters["description"] = "Playlist created by the SocialQ App."

        Log.d(TAG, "Creating playlist for the SocialQ")
        spotifyService.createPlaylist(playlistOwnerUserId, playlistParameters, createPlaylistCallback)
    }

    private fun loadBasePlaylist(playlistId: String) {
        Log.d(TAG, "Loading base playlist with ID: $playlistId")

        wasBasePlaylistLoaded = true
        val basePlaylist = spotifyService.getPlaylist(playlistOwnerUserId, playlistId)

        // Adding with base user ensure host added tracks are sorted within the base playlist
        val baseUser = UserPublic()
        baseUser.id = AppConstants.BASE_USER_ID
        baseUser.display_name = resources.getString(R.string.base_playlist)

        // Retrieve all tracks
        val playlistTracks = ArrayList<PlaylistTrack>()
        // Can only retrieve 100 tracks at a time
        var i = 0
        while (i < basePlaylist.tracks.total) {
            val iterationQueryParameters = HashMap<String, Any>()
            iterationQueryParameters[SpotifyService.OFFSET] = i
            iterationQueryParameters[SpotifyService.MARKET] = AppConstants.PARAM_FROM_TOKEN

            // Retrieve max next 100 tracks
            val iterationTracks = spotifyService.getPlaylistTracks(playlistOwnerUserId, playlistId, iterationQueryParameters)

            // Ensure tracks are playable in our market and not local before adding
            for (track in iterationTracks.items) {
                if (track.track.is_playable != null && track.track.is_playable && !track.is_local) {
                    playlistTracks.add(track)
                }
            }

            i += 100
        }

        // Shuffle entire track list
        val shuffledTracks = ArrayList<PlaylistTrack>()
        val randomGenerator = Random()
        while (playlistTracks.size > 0) {
            val trackIndexToAdd = randomGenerator.nextInt(playlistTracks.size)

            shuffledTracks.add(playlistTracks.removeAt(trackIndexToAdd))
        }

        // Add tracks (100 at a time) to playlist and add request date
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
            val queryParameters = HashMap<String, Any>()
            val bodyParameters = HashMap<String, Any>()
            bodyParameters["uris"] = urisArray

            // Add max 100 tracks to the playlist
            spotifyService.addTracksToPlaylist(playlistOwnerUserId, playlist.id, queryParameters, bodyParameters)
        }

        refreshPlaylist()
    }

    private fun handleSongRequest(songRequest: SongRequestData?) {
        if (songRequest != null && !songRequest.uri.isEmpty()) {
            Log.d(TAG, "Received request for URI: " + songRequest.uri + ", from User ID: " + songRequest.user.id)

            // Add track to request list
            songRequests.add(songRequest)

            val willNextSongBeWrong: Boolean
            // Add track and figure out if our next song will be wrong
            if (isQueueFairPlay) {
                willNextSongBeWrong = addNewTrackFairplay(songRequest)
            } else {
                willNextSongBeWrong = addNewTrack(songRequest)
            }

            if (willNextSongBeWrong) {
                // If we changed the next track notify service next track will be incorrect
                incorrectMetaDataFlag = true
                spotifyPlayer.queue(this, songRequest.uri)
            }
            refreshPlaylist()
        }
    }

    /**
     * Adds a song to end of the referenced playlist
     *
     * @param uri - uri of the track to be added
     */
    private fun addTrackToPlaylist(uri: String) {
        addTrackToPlaylistPosition(uri, -1)
    }

    /**
     * Adds a song to the referenced playlist at the given position (or end if not specified)
     *
     * @param uri      - uri of the track to be added
     * @param position - position of the track to be added (if less than 0, track placed at end of playlist)
     */
    private fun addTrackToPlaylistPosition(uri: String, position: Int) {
        val queryParameters = HashMap<String, Any>()
        queryParameters["uris"] = uri
        if (position >= 0) {
            queryParameters["position"] = position
        }
        val bodyParameters = HashMap<String, Any>()

        // Add song to queue playlist
        spotifyService.addTracksToPlaylist(playlistOwnerUserId, playlist.id, queryParameters, bodyParameters)
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
            addTrackToPlaylist(songRequest.uri)
            return songRequests.size == 2
        }
    }

    private fun injectTrackToPosition(newTrackPosition: Int, songRequest: SongRequestData): Boolean {
        if (newTrackPosition == songRequests.size) {
            // No base playlist track found add track to end of playlist
            Log.d(TAG, "Adding track to end of playlist")
            addTrackToPlaylist(songRequest.uri)
            // Return true if the song being added is next (request size of 2)
            return songRequests.size == 1 || songRequests.size == 2
        } else if (newTrackPosition > songRequests.size) {
            // Should not be possible
            Log.e(TAG, "INVALID NEW TRACK POSITION INDEX")
            return false
        } else {
            // If new track position is not equal or greater than song request size we need to move it
            // Inject song request data to new position
            songRequests.add(newTrackPosition, songRequests.removeAt(songRequests.size - 1))

            Log.d(TAG, "Adding new track at playlist index: " + (newTrackPosition + currentPlaylistIndex))
            addTrackToPlaylistPosition(songRequest.uri, newTrackPosition + currentPlaylistIndex)

            // Return true if we're moving the added track to the "next" position
            return newTrackPosition == 1
        }
    }

    fun unfollowQueuePlaylist() {
        // Unfollow the playlist created for SocialQ
        if (playlistOwnerUserId != null) {
            Log.d(TAG, "Unfollowing playlist created for the SocialQ")

            spotifyService.unfollowPlaylist(playlistOwnerUserId, playlist.id, playlistCloseCallback)
        }
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

    /**
     * Item selection method for playlist ID in base playlist dialog
     *
     * @param selectedItem - ID of the playlist that was selected
     */
    fun basePlaylistSelected(playlistId: String) {
        basePlaylistId = playlistId
        createPlaylistForQueue()
    }

    fun savePlaylistAs(playlistName: String) {
        // Create body parameters for modifying playlist details
        val saveName = if (playlistName.isEmpty()) getString(R.string.default_playlist_name) else playlistName
        val playlistParameters = HashMap<String, Any>()
        playlistParameters["name"] = saveName

        Log.d(TAG, "Saving playlist as: $saveName")
        spotifyService.changePlaylistDetails(playlistOwnerUserId, playlist.id, playlistParameters, playlistCloseCallback)
    }

    fun noBasePlaylistSelected() {
        createPlaylistForQueue()
    }

    fun hostRequestSong(uri: String) {
        if (uri.isNotEmpty()) {
            handleSongRequest(SongRequestData(uri, hostUser))
        }
    }

    fun requestInitiation() {
        listener?.initiateView(queueTitle, createDisplayList(playlistTracks.subList(currentPlaylistIndex, playlistTracks.size)), isPlaying)
    }

    override fun playlistRefreshComplete() {
        notifyQueueChanged()
    }

    private fun showTrackInNotification(trackToShow: Track) {
        // Update metadata for media session
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, trackToShow.album?.name)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, trackToShow.name)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, trackToShow.name)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, DisplayUtils.getTrackArtistString(trackToShow))

        // Attempt to update album art in notification and metadata
        if (trackToShow.album.images.size > 0) {
            try {
                val url = URL(trackToShow.album.images[0].url)
                // Retrieve album art bitmap
                val albumArtBitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())

                // Set bitmap data for lock screen display
                metaDataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArtBitmap)
                // Set bitmap data for notification
                notificationBuilder.setLargeIcon(albumArtBitmap)
            } catch (exception: IOException) {
                Log.e(TAG, "Error retrieving image bitmap: ${exception.message.toString()}")
                System.out.println(exception)
            }
        }
        mediaSession.setMetadata(metaDataBuilder.build())

        // Update notification data
        notificationBuilder.setContentTitle(trackToShow.name)
        notificationBuilder.setContentText(DisplayUtils.getTrackArtistString(trackToShow))

        // Display updated notification
        notificationManager.notify(AppConstants.HOST_SERVICE_ID, notificationBuilder.build())
    }

    // Callback for creating playlist
    private val createPlaylistCallback = object : SpotifyCallback<Playlist>() {
        override fun success(playlist: Playlist?, response: Response?) {
            if (playlist != null) {
                Log.d(TAG, "Successfully created playlist for queue")
                this@HostService.playlist = playlist

                // If we have a base playlist ID load it up and start player
                if (basePlaylistId.isNotEmpty()) {
                    loadBasePlaylist(basePlaylistId)
                    basePlaylistId = ""
                }
                return
            }
            Log.e(TAG, "Created playlist returned null. Try again")
            createPlaylistForQueue()
        }

        override fun failure(spotifyError: SpotifyError?) {
            Log.e(TAG, spotifyError?.errorDetails?.message.toString())
            Log.e(TAG, "Failed to create playlist. Try again")
            createPlaylistForQueue()
            // TODO: Should stop trying after so many failures
        }
    }

    // Callback for retrieving host user's playlists
    private val userPlaylistsCallback = object : SpotifyCallback<Pager<PlaylistSimple>>() {
        override fun success(playlistsPager: Pager<PlaylistSimple>?, response: Response?) {
            if (playlistsPager != null) {
                if (playlistsPager.total == 0) {
                    Log.d(TAG, "User has no playlists to show")
                    createPlaylistForQueue()
                    return
                }

                if (playlistsPager.offset == 0) {
                    // Fresh retrieval
                    currentUserPlaylists.clear()
                }

                currentUserPlaylists.addAll(playlistsPager.items)


                // Check if there are more than 50 playlists by the user. If so we should get the next 50
                if (currentUserPlaylists.size < playlistsPager.total) {
                    val options = HashMap<String, Any>()
                    options[SpotifyService.LIMIT] = AppConstants.PLAYLIST_LIMIT
                    options[SpotifyService.OFFSET] = currentUserPlaylists.size

                    spotifyService.getPlaylists(playlistOwnerUserId, options, this)
                } else {
                    if (listener != null) {
                        listener?.showBasePlaylistDialog(currentUserPlaylists)
                    }
                }
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            Log.e(TAG, spotifyError?.errorDetails?.message.toString())
            Log.e(TAG, "Failed to retrieve user playlists")
        }
    }

    private val playlistCloseCallback = object : SpotifyCallback<Result>() {
        override fun success(result: Result?, response: Response?) {
            Log.d(TAG, "Successfully unfollowed/changed playlist")

            Log.d(TAG, "Notifying connected clients we are shutting down")
            notifyClientsHostDisconnecting()

            Log.d(TAG, "Shutting down player before service closes")
            spotifyPlayer.logout()
            try {
                Log.d(TAG, "Releasing spotify player resource")
                Spotify.awaitDestroyPlayer(this@HostService, 10000, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                e.printStackTrace()
                Log.e(TAG, "Error releasing spotify player resource, hard shutting down")
                Spotify.destroyPlayer(this@HostService)
            }

            if (listener != null) {
                listener?.closeHost()
            }
            listener = null
        }

        override fun failure(spotifyError: SpotifyError?) {
            Log.e(TAG, spotifyError?.errorDetails?.message.toString())
            Log.e(TAG, "Failed to unfollow/change playlist")
        }
    }

    private fun setupShortDemoQueue() {
        val shortQueueString = "spotify:track:0p8fUOBfWtGcaKGiD9drgJ," +
                "spotify:track:6qtg4gz3DhqOHL5BHtSQw8," +
                "spotify:track:57bgtoPSgt236HzfBOd8kj," +
                "spotify:track:7lGh1Dy02c5C0j3tj9AVm3"


        val queryParameters = HashMap<String, Any>()
        queryParameters["uris"] = shortQueueString
        val bodyParameters = HashMap<String, Any>()

        spotifyService.addTracksToPlaylist(playlistOwnerUserId, playlist.id, queryParameters, bodyParameters)

        // String for testing fair play (simulate a user name)
        val userId = "fake_user"
        //        String userId = currentUser.id;

        songRequests.add(SongRequestData("spotify:track:0p8fUOBfWtGcaKGiD9drgJ", hostUser))
        songRequests.add(SongRequestData("spotify:track:6qtg4gz3DhqOHL5BHtSQw8", hostUser))
        songRequests.add(SongRequestData("spotify:track:57bgtoPSgt236HzfBOd8kj", hostUser))
        songRequests.add(SongRequestData("spotify:track:7lGh1Dy02c5C0j3tj9AVm3", hostUser))
    }

    private fun setupLongDemoQueue() {
        val longQueueString = "spotify:track:0p8fUOBfWtGcaKGiD9drgJ," +
                "spotify:track:6qtg4gz3DhqOHL5BHtSQw8," +
                "spotify:track:57bgtoPSgt236HzfBOd8kj," +
                "spotify:track:4VbDJMkAX3dWNBdn3KH6Wx," +
                "spotify:track:2jnvdMCTvtdVCci3YLqxGY," +
                "spotify:track:419qOkEdlmbXS1GRJEMntC," +
                "spotify:track:1jvqZQtbBGK5GJCGT615ao," +
                "spotify:track:6cG3kY60HMcFqiZN8frkXF," +
                "spotify:track:0dqrAmrvQ6fCGNf5T8If5A," +
                "spotify:track:0wHNrrefyaeVewm4NxjxrX," +
                "spotify:track:1hh4GY1zM7SUAyM3a2ziH5," +
                "spotify:track:5Cl9GDb0AyQnppRr6q7ldb," +
                "spotify:track:7D180Q77XAEP7atBLmMTgK," +
                "spotify:track:2uxL6E8Yq0Psc1V9uBtC4F," +
                "spotify:track:7lGh1Dy02c5C0j3tj9AVm3"

        val queryParameters = HashMap<String, Any>()
        queryParameters["uris"] = longQueueString
        val bodyParameters = HashMap<String, Any>()

        spotifyService.addTracksToPlaylist(playlistOwnerUserId, playlist.id, queryParameters, bodyParameters)


        // String for testing fair play (simulate a user name)
        //        String userId = "fake_user";
        //        String userId = currentUser.id;

        songRequests.add(SongRequestData("spotify:track:0p8fUOBfWtGcaKGiD9drgJ", hostUser))
        songRequests.add(SongRequestData("spotify:track:6qtg4gz3DhqOHL5BHtSQw8", hostUser))
        songRequests.add(SongRequestData("spotify:track:57bgtoPSgt236HzfBOd8kj", hostUser))
        songRequests.add(SongRequestData("spotify:track:4VbDJMkAX3dWNBdn3KH6Wx", hostUser))
        songRequests.add(SongRequestData("spotify:track:2jnvdMCTvtdVCci3YLqxGY", hostUser))
        songRequests.add(SongRequestData("spotify:track:419qOkEdlmbXS1GRJEMntC", hostUser))
        songRequests.add(SongRequestData("spotify:track:1jvqZQtbBGK5GJCGT615ao", hostUser))
        songRequests.add(SongRequestData("spotify:track:6cG3kY60HMcFqiZN8frkXF", hostUser))
        songRequests.add(SongRequestData("spotify:track:0dqrAmrvQ6fCGNf5T8If5A", hostUser))
        songRequests.add(SongRequestData("spotify:track:0wHNrrefyaeVewm4NxjxrX", hostUser))
        songRequests.add(SongRequestData("spotify:track:1hh4GY1zM7SUAyM3a2ziH5", hostUser))
        songRequests.add(SongRequestData("spotify:track:5Cl9GDb0AyQnppRr6q7ldb", hostUser))
        songRequests.add(SongRequestData("spotify:track:7D180Q77XAEP7atBLmMTgK", hostUser))
        songRequests.add(SongRequestData("spotify:track:2uxL6E8Yq0Psc1V9uBtC4F", hostUser))
        songRequests.add(SongRequestData("spotify:track:7lGh1Dy02c5C0j3tj9AVm3", hostUser))
    }
}