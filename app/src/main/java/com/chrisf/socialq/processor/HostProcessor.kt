package com.chrisf.socialq.processor

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.lifecycle.Lifecycle
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.extensions.addTo
import com.chrisf.socialq.model.AccessModel
import com.chrisf.socialq.model.ClientRequestData
import com.chrisf.socialq.model.SongRequestData
import com.chrisf.socialq.model.spotify.Playlist
import com.chrisf.socialq.model.spotify.PlaylistTrack
import com.chrisf.socialq.model.spotify.UserPrivate
import com.chrisf.socialq.model.spotify.UserPublic
import com.chrisf.socialq.network.FrySpotifyService
import com.chrisf.socialq.processor.HostProcessor.HostAction
import com.chrisf.socialq.processor.HostProcessor.HostAction.*
import com.chrisf.socialq.processor.HostProcessor.HostState
import com.chrisf.socialq.processor.HostProcessor.HostState.*
import com.google.gson.JsonArray
import com.spotify.sdk.android.player.*
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class HostProcessor @Inject constructor(
        private val spotifyService: FrySpotifyService,
        lifecycle: Lifecycle?,
        subscriptions: CompositeDisposable
) : BaseProcessor<HostState, HostAction>(lifecycle, subscriptions),
        ConnectionStateCallback,
        Player.NotificationCallback,
        Player.OperationCallback,
        AudioManager.OnAudioFocusChangeListener {
    // Playlist object for the queue
    private lateinit var playlist: Playlist
    // Tracks from queue playlist
    private val playlistTracks = mutableListOf<PlaylistTrack>()

    // AUDIO ELEMENTS
    // Reference to system audio manager
    private lateinit var audioManager: AudioManager
    // Flag to resume playback when audio focus is returned
    private var resumeOnAudioFocus = false
    // Reference to current audio focus state
    private var audioFocusState = -1
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

    // QUEUE SORTING/FAIR PLAY ELEMENTS
    // Boolean flag to store if queue should be "fair play"
    private var isQueueFairPlay: Boolean = false
    // List containing client song requests
    private val songRequests = mutableListOf<SongRequestData>()
    // Flag for storing if a base playlist has been loaded
    private var wasBasePlaylistLoaded = false
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

    // TODO: Holy shit let's cut down the above list if we can


    override fun handleAction(action: HostAction) {
        when (action) {
            is ServiceStarted -> initiateHostService(action)
            is RequestTogglePlayPause -> handlePlaybackToggleRequest(action)
            is RequestNext -> handlePlaybackNextRequest(action)
            is TrackRequested -> handleTrackRequest(action)
            is ClientConnected -> initiateNewClient(action)
            is UnfollowPlaylist -> unfollowPlaylist(action)
            is UpdatePlaylistName -> updatePlaylistName(action)
            is HostTrackRequested -> handleHostTrackRequest(action)
            is AccessTokenUpdated -> onAccessTokenUpdated()
        }
    }

    override fun detach() {
        spotifyPlayer.logout()
        try {
            Timber.d("Releasing spotify player resource")
            Spotify.awaitDestroyPlayer(this, 10, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            e.printStackTrace()
            Timber.e("Error releasing spotify player resource, hard shutting down")
            Spotify.destroyPlayer(this)
        }
        super.detach()
    }

    private fun initiateHostService(action: ServiceStarted) {
        queueTitle = action.queueTitle
        isQueueFairPlay = action.isQueueFairPlay
        basePlaylistId = action.basePlaylistId
        initHost()
        initPlayer(action.context)
    }

    private fun handlePlaybackToggleRequest(action: RequestTogglePlayPause) {
        if (!spotifyPlayer.playbackState.isPlaying) {
            handlePlay()
        } else {
            handlePause()
        }
    }

    private fun handlePlay() {
        if (requestAudioFocus()) {
            if (audioDeliveryDoneFlag) {
                if (currentPlaylistIndex < playlistTracks.size) {
                    // If audio has previously been completed (or never started)
                    // start the playlist at the current index
                    Timber.d("Audio previously finished.\nStarting playlist from index: $currentPlaylistIndex")
                    spotifyPlayer.playUri(this, playlist.uri, currentPlaylistIndex, 0)
                    audioDeliveryDoneFlag = false
                    incorrectMetaDataFlag = false
                } else {
                    Timber.d("Nothing to play")
                }
                return
            }

            spotifyPlayer.resume(this)
        }
    }

    private fun handlePause() {
        userRequestedPause = true
        spotifyPlayer.pause(this)
    }

    private fun handlePlaybackNextRequest(action: RequestNext) {
        Timber.d("NEXT REQUEST")
        if (!audioDeliveryDoneFlag) {
            // Don't allow a skip when we're waiting on a new track to be queued
            spotifyPlayer.skipToNext(this)
        } else {
            Timber.d("Can't go to next track")
        }
    }

    private fun initiateNewClient(action: ClientConnected) {
        stateStream.accept(
                InitiateNewClient(
                        action.newClientId,
                        hostUser.id,
                        playlist.id,
                        currentPlaylistIndex
                )
        )
    }

    private fun unfollowPlaylist(action: UnfollowPlaylist) {
        spotifyService.unfollowPlaylist(playlist.id)
                .subscribeOn(Schedulers.io())
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
    }

    private fun updatePlaylistName(action: UpdatePlaylistName) {
        spotifyService.changePlaylistDetails(
                playlist.id,
                FrySpotifyService.getPlaylistDetailsBody(action.newPlaylistName)
        )
                .subscribeOn(Schedulers.io())
                .subscribe({
                    if (it.code() == 200) {
                        Timber.d("Successfully changed playlist name")
                    } else {
                        Timber.e("Error updating playlist name")
                    }
                }, {
                    Timber.e(it)
                })
                .addTo(subscriptions)
    }


    private fun refreshPlaylist() {
        Timber.d("Refreshing playlist")

        playlistTracks.clear()
        getPlaylistTracks(playlist.id)
    }

    private fun getPlaylistTracks(playlistId: String, offset: Int = 0) {
        spotifyService.getPlaylistTracks(playlistId, 50, offset)
                .subscribeOn(Schedulers.io())
                .subscribe { response ->
                    if (response.body() == null) {
                        // TODO: Try again?
                    } else {
                        val tracks = response.body()!!
                        playlistTracks.addAll(tracks.items)

                        if (tracks.next != null) {
                            val nextOffset = tracks.offset + tracks.items.size
                            getPlaylistTracks(playlistId, nextOffset)
                        } else {
                            stateStream.accept(QueueInitiationComplete(
                                    queueTitle,
                                    hostUser.display_name ?: "",
                                    hostUser.id,
                                    isQueueFairPlay,
                                    createDisplayList(playlistTracks.subList(currentPlaylistIndex, playlistTracks.size))
                            ))
                        }
                    }
                }
                .addTo(subscriptions)
    }

    private fun onAccessTokenUpdated() {
        spotifyPlayer.login(AccessModel.getAccessToken())
    }


    private fun initPlayer(context: Context) {
        // Setup Spotify player
        val playerConfig = Config(context, AccessModel.getAccessToken(), AppConstants.CLIENT_ID)
        spotifyPlayer = Spotify.getPlayer(playerConfig, this, object : SpotifyPlayer.InitializationObserver {
            override fun onInitialized(player: SpotifyPlayer) {
                Timber.d("Player initialized")

                // Retrieve audio manager for managing audio focus
                audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                player.setConnectivityStatus(connectivityCallback,
                        getNetworkConnectivity(context))
                player.addConnectionStateCallback(this@HostProcessor)
                player.addNotificationCallback(this@HostProcessor)
            }

            override fun onError(error: Throwable) {
                Timber.e("ERROR: Could not initialize player: %1s", error.message)
            }
        })
    }

    private fun initHost() {
        Timber.d("Initializing Host (init player, create playlist, load base playlist if selected")

        if (AccessModel.getCurrentUser() == null) {
            spotifyService.getCurrentUser()
                    .subscribeOn(Schedulers.io())
                    .subscribe { response ->
                        if (response.body() != null) {
                            hostUser = response.body()!!
                            createPlaylistForQueue()
                        } else {
                            Timber.e("Error retrieving current user")
                        }
                    }
                    .addTo(subscriptions)

        } else {
            hostUser = AccessModel.getCurrentUser()
            createPlaylistForQueue()
        }
    }

    private fun createPlaylistForQueue() {
        Timber.d("Creating playlist for the SocialQ")
        spotifyService.createSocialQPlaylist(hostUser.id)
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
                            stateStream.accept(QueueInitiationComplete(
                                    queueTitle,
                                    hostUser.display_name ?: "",
                                    hostUser.id,
                                    isQueueFairPlay,
                                    emptyList()))
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

        getBasePlaylistTracks(playlistId)
    }

    private fun getBasePlaylistTracks(playlistId: String, offset: Int = 0) {
        spotifyService.getPlaylistTracks(playlistId, 50, offset)
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
        // TODO: Should avoid string constancts here
        val baseUser = UserPublic(
                "Base Playlist",
                AppConstants.BASE_USER_ID,
                emptyList(),
                "",
                ""
        )

        // Add tracks (100 at a time) to playlist and add request date
        var addRequestCount = 0
        var successfulAdds = 0
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
            addRequestCount++
            spotifyService.addTracksToPlaylist(
                    playlist.id,
                    FrySpotifyService.getAddTracksToPlaylistBody(urisArray)
            )
                    .subscribeOn(Schedulers.io())
                    .subscribe({
                        Timber.d(it.body().toString())
                        successfulAdds++
                        // TODO: This will cause base playlist to not load correctly if one of the
                        // add requests fails. Need to implement a more elegant solution.
                        if (successfulAdds == addRequestCount) {
                            refreshPlaylist()
                        }
                    }, {
                        Timber.e(it)
                    })
                    .addTo(subscriptions)
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

    private fun handleHostTrackRequest(action: HostTrackRequested) {
        handleTrackRequest(TrackRequested(hostUser.id, action.trackUri))
    }

    private fun handleTrackRequest(action: TrackRequested) {

        if (!action.trackUri.isEmpty()) {
            Timber.d("Received request for URI: " + action.trackUri + ", from User ID: " + action.userId)

            spotifyService.getUserById(action.userId)
                    .subscribeOn(Schedulers.io())
                    .subscribe({
                        val user = it.body()

                        if (user != null) {
                            val songRequest = SongRequestData(action.trackUri, user)
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
                                spotifyPlayer.queue(this, songRequest.uri)
                            }
                        }
                    }, {
                        Timber.e("Could not retrieve user for song request")
                        Timber.e(it)
                    })
                    .addTo(subscriptions)
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

        var newTrackPosition = if (position < 0) null else position
        spotifyService.addTrackToPlaylist(playlist.id, uri, newTrackPosition)
                .subscribeOn(Schedulers.io())
                .subscribe({
                    if (newTrackPosition == null) {
                        newTrackPosition = playlistTracks.size
                    }

                    pullNewTrack(newTrackPosition!!)
                }, {
                    Timber.e(it)
                })
                .addTo(subscriptions)
    }

    private fun pullNewTrack(newTrackIndex: Int) {
        Timber.d("Pulling newly added track")

        spotifyService.getPlaylistTracks(playlist.id, 1, newTrackIndex)
                .subscribeOn(Schedulers.io())
                .subscribe { response ->
                    if (response.body() != null) {
                        val pager = response.body()!!
                        playlistTracks.add(newTrackIndex, pager.items[0])
                        stateStream.accept(
                                TrackAdded(
                                        newTrackIndex,
                                        newTrackIndex == currentPlaylistIndex,
                                        spotifyPlayer.playbackState.isPlaying,
                                        createDisplayList(playlistTracks.subList(
                                                currentPlaylistIndex,
                                                playlistTracks.size)
                                        )
                                )
                        )
                    }
                }
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
            addTrackToPlaylist(songRequest.uri)
            return songRequests.size == 2
        }
    }

    private fun injectTrackToPosition(newTrackPosition: Int, songRequest: SongRequestData): Boolean {
        if (newTrackPosition == songRequests.size) {
            // No base playlist track found add track to end of playlist
            Timber.d("Adding track to end of playlist")
            addTrackToPlaylist(songRequest.uri)
            // Return true if the song being added is next (request size of 2)
            return songRequests.size == 1 || songRequests.size == 2
        } else if (newTrackPosition > songRequests.size) {
            // Should not be possible
            Timber.e("INVALID NEW TRACK POSITION INDEX")
            return false
        } else {
            // If new track position is not equal or greater than song request size we need to move it
            // Inject song request data to new position
            songRequests.add(newTrackPosition, songRequests.removeAt(songRequests.size - 1))

            Timber.d("Adding new track at playlist index: " + (newTrackPosition + currentPlaylistIndex))
            addTrackToPlaylistPosition(songRequest.uri, newTrackPosition + currentPlaylistIndex)

            // Return true if we're moving the added track to the "next" position
            return newTrackPosition == 1
        }
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

    // START AUDIO FOCUS METHODS
    override fun onAudioFocusChange(focusChange: Int) {
        audioFocusState = focusChange

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.d("Gained audio focus")

                // If flagged to resume audio, request play
                if (resumeOnAudioFocus) {
                    handlePlay()
                    resumeOnAudioFocus = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Timber.d("Lost audio focus, pause playback")

                // If we're currently playing audio, pause
                if (spotifyPlayer.playbackState.isPlaying) {
                    handlePause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Timber.d("Lost audio focus, focus should return, pause playback")

                // If we're currently playing audio, flag to resume when we regain audio focus and pause
                if (spotifyPlayer.playbackState.isPlaying) {
                    resumeOnAudioFocus = true
                    handlePause()
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
    // END AUDIO FOCUS METHODS

    // START SPOTIFY PLAYER CALLBACK METHODS
    override fun onPlaybackEvent(playerEvent: PlayerEvent) {
        Timber.d("New playback event: %1s", playerEvent.name)

        when (playerEvent) {
            PlayerEvent.kSpPlaybackNotifyPlay -> {
                Timber.d("Player has started playing")

                // Started/resumed playing, reset flag for resume audio on focus
                resumeOnAudioFocus = false

                stateStream.accept(PlaybackResumed)
            }
            PlayerEvent.kSpPlaybackNotifyContextChanged -> {
            }
            PlayerEvent.kSpPlaybackNotifyPause -> {
                Timber.d("Player has paused")

                // If meta data is incorrect we won't actually pause (unless user requested pause)
                if (userRequestedPause || !incorrectMetaDataFlag) {
                    stateStream.accept(PlaybackPaused(currentPlaylistIndex < playlistTracks.size))
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
                    spotifyPlayer.skipToNext(this)
                } else {
                    if (songRequests.size > 0) {
                        songRequests.removeAt(0)
                    }
                    currentPlaylistIndex++
                    Timber.d("UPDATING CURRENT PLAYING INDEX TO: $currentPlaylistIndex")
                    stateStream.accept(
                            PlaybackNext(
                                    createDisplayList(playlistTracks.subList(currentPlaylistIndex, playlistTracks.size)),
                                    currentPlaylistIndex,
                                    queueTitle
                            )
                    )
                }
            }
            PlayerEvent.kSpPlaybackNotifyAudioDeliveryDone -> {
                // Current queue playlist has finished
                Timber.d("Player has finished playing audio")
                audioDeliveryDoneFlag = true

                stateStream.accept(AudioDeliveryDone)
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
    // END SPOTIFY PLAYER CALLBACK METHODS

    // START SPOTIFY PLAYER OPTERATION CALLBACK METHODS
    override fun onSuccess() {
        Timber.d("Great Success!")
    }

    override fun onError(error: Error) {
        Timber.e("ERROR: Playback error received : Error - %1s", error.name)
    }
    // END SPOTIFY PLAYER CALLBACK METHODS

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

    sealed class HostState {
        data class QueueInitiationComplete(
                val queueTitle: String,
                val hostDisplayName: String,
                val hostId: String,
                val isFairPlay: Boolean,
                val requestDataList: List<ClientRequestData>
        ) : HostState()

        object PlaybackResumed : HostState()
        data class PlaybackPaused(val tracksRemaining: Boolean) : HostState()
        data class PlaybackNext(
                val trackRequestData: List<ClientRequestData>,
                val currentPlaylistIndex: Int,
                val queueTitle: String
        ) : HostState()

        object AudioDeliveryDone : HostState()
        data class TrackAdded(
                val newTrackIndex: Int,
                val needDisplay: Boolean,
                val isPlaying: Boolean,
                val trackRequestData: List<ClientRequestData>
        ) : HostState()

        data class InitiateNewClient(
                val newClientId: String,
                val hostUserId: String,
                val playlistId: String,
                val currentPlaylistIndex: Int
        ) : HostState()
    }

    sealed class HostAction {
        // TODO: See if we can avoid passing context
        data class ServiceStarted(
                val context: Context,
                val queueTitle: String,
                val isQueueFairPlay: Boolean,
                val basePlaylistId: String
        ) : HostAction()

        object AccessTokenUpdated : HostAction()
        object RequestTogglePlayPause : HostAction()
        object RequestNext : HostAction()
        data class TrackRequested(
                val userId: String,
                val trackUri: String
        ) : HostAction()

        data class HostTrackRequested(val trackUri: String) : HostAction()
        data class ClientConnected(val newClientId: String) : HostAction()
        object UnfollowPlaylist : HostAction()
        data class UpdatePlaylistName(val newPlaylistName: String) : HostAction()
    }
}