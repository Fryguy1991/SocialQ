package com.chrisf.socialq.processor

import androidx.lifecycle.Lifecycle
import com.chrisf.socialq.R
import com.chrisf.socialq.enums.NearbyDevicesMessage
import com.chrisf.socialq.enums.NearbyDevicesMessage.*
import com.chrisf.socialq.model.spotify.PlaylistTrack
import com.chrisf.socialq.model.spotify.UserPrivate
import com.chrisf.socialq.network.SpotifyApi
import com.chrisf.socialq.processor.ClientProcessor.ClientAction
import com.chrisf.socialq.processor.ClientProcessor.ClientAction.*
import com.chrisf.socialq.processor.ClientProcessor.ClientState
import com.chrisf.socialq.processor.ClientProcessor.ClientState.*
import com.google.android.gms.nearby.connection.Payload
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.lang.NumberFormatException
import java.util.regex.Pattern
import javax.inject.Inject

class ClientProcessor @Inject constructor(
        private val spotifyService: SpotifyApi,
        lifecycle: Lifecycle?,
        subscriptions: CompositeDisposable
) : BaseProcessor<ClientState, ClientAction>(lifecycle, subscriptions) {

    // Host Values
    private lateinit var hostEndpoint: String
    private lateinit var queueTitle: String

    // Nearby Connection Values
    private var successfulConnectionFlag = false
    private var hostReconnectCount = 0
    private var hostDisconnect = false
    private var isBeingInitiated = false

    // Spotify Values
    private lateinit var currentUser: UserPrivate
    private var currentlyPlayingIndex = 0
    private lateinit var playlistId: String
    private val playlistTracks = mutableListOf<PlaylistTrack>()

    override fun handleAction(action: ClientAction) {
        when (action) {
            is ServiceStarted -> handleClientServiceStarted(action)
            ConnectionToHostFailed -> handleHostConnectionFailure()
            ConnectionToHostSuccessful -> handleHostConnectionSuccessful()
            HostRejectedConnection -> handleHostRejectedConnection()
            is HandleHostPayload -> handleHostPayload(action)
            is ClientSelectedATrack -> handleClientTrackSelection(action)
            ClientRequestedPlaylistFollow -> handleClientFollowRequest()
            ClientRequestedDisconnect -> stateStream.accept(CloseClient)
        }
    }

    private fun handleClientServiceStarted(action: ServiceStarted) {
        if (action.hostEndpoint.isNullOrEmpty()) {
            Timber.e("Not a valid endpoint, stopping service")
            stateStream.accept(ShutdownService(R.string.toast_failed_to_connect_to_host))
            return
        } else {
            stateStream.accept(DisplayLoading)

            hostEndpoint = action.hostEndpoint
            queueTitle = action.queueTitle

            spotifyService.getCurrentUser()
                    .subscribeOn(Schedulers.io())
                    .subscribe({
                        val user = it.body()
                        if (user != null) {
                            currentUser = user
                            stateStream.accept(ConnectToHost(action.hostEndpoint))
                        }
                    }, {
                        Timber.e("Failed to retrieve current user")
                        stateStream.accept(ShutdownService(R.string.toast_failed_to_connect_to_host))
                    })
                    .addTo(subscriptions)
        }
    }

    private fun handleHostConnectionFailure() {
        hostReconnectCount++

        if (hostDisconnect) {
            return
        }

        if (hostReconnectCount > 3) {
            if (successfulConnectionFlag) {
                stateStream.accept(ShowHostDisconnect)
            } else {
                stateStream.accept(ShutdownService(R.string.toast_failed_to_connect_to_host))
            }
        } else {
            stateStream.accept(ConnectToHost(hostEndpoint))
        }
    }

    private fun handleHostConnectionSuccessful() {
        successfulConnectionFlag = true
        hostReconnectCount = 0
    }

    private fun handleHostRejectedConnection() {
        stateStream.accept(ShutdownService(R.string.toast_failed_to_connect_to_host))
    }

    private fun handleHostPayload(action: HandleHostPayload) {
        val payloadBytes = action.payload.asBytes()
        if (payloadBytes == null) {
            Timber.e("Invalid payload")
            return
        }

        val payloadString = String(payloadBytes)
        if (payloadString.isEmpty()) {
            Timber.e("Invalid payload")
            return
        }

        val payloadType = getMessageTypeFromPayload(payloadString)

        Timber.d("$payloadType payload received from host")
        when (payloadType) {
            CURRENTLY_PLAYING_UPDATE -> handleCurrentlyPlayingUpdatePayload(payloadString)
            SONG_REQUEST -> throw IllegalStateException("Should not receive new song request payload as client")
            INITIATE_CLIENT -> handleInitiateClientPayload(payloadString)
            HOST_DISCONNECTING -> handleHostDisconnectingPayload()
            NEW_TRACK_ADDED -> handleNewTrackAddedPayload(payloadString)
            INVALID -> Timber.e("Invalid payload was sent to client")
        }
    }

    /**
     * Determines the payload type from the given payload string
     *
     * @param payload - String of the payload who's type we are determining
     * @return Enum type of the payload (see NearbyDevicesMessage)
     */
    private fun getMessageTypeFromPayload(payload: String): NearbyDevicesMessage {
        for (enumValue in NearbyDevicesMessage.values()) {
            val matcher = Pattern.compile(enumValue.regex).matcher(payload)
            if (matcher.matches()) {
                return enumValue
            }
        }
        return NearbyDevicesMessage.INVALID
    }

    private fun handleInitiateClientPayload(payloadString: String) {
        val regexMatcher = Pattern.compile(INITIATE_CLIENT.regex).matcher(payloadString)

        Timber.d("Started Client initiation")
        isBeingInitiated = true
        if (regexMatcher.find()) {
            val hostId = regexMatcher.group(1)
            val playlistId = (regexMatcher.group(2))

            if (playlistId.isNotEmpty()) {
                this.playlistId = playlistId
                playlistTracks.clear()
                retrievePlaylistTracks(playlistId)
            }

            try {
                currentlyPlayingIndex = (regexMatcher.group(3).toInt())
            } catch (ex: NumberFormatException) {
                Timber.e("Invalid index was sent")
                currentlyPlayingIndex = -1
            }
        } else {
            Timber.e("Something went wrong. Regex failed matching for $INITIATE_CLIENT")
            isBeingInitiated = false
        }
    }

    private fun retrievePlaylistTracks(playlistId: String, offset: Int = 0) {
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
                            retrievePlaylistTracks(playlistId, nextOffset)
                        } else {
                            isBeingInitiated = false
                            stateStream.accept(DisplayTracks(playlistTracks.subList(currentlyPlayingIndex, playlistTracks.size)))
                            stateStream.accept(ClientInitiationComplete(queueTitle))
                        }
                    }
                }
                .addTo(subscriptions)
    }

    private fun handleNewTrackAddedPayload(payloadString: String) {
        val regexMatcher = Pattern.compile(NEW_TRACK_ADDED.regex).matcher(payloadString)

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
            Timber.e("Something went wrong. Regex failed matching for $NEW_TRACK_ADDED")
        }
    }

    private fun pullNewTrack(newTrackIndex: Int) {
        Timber.d("Pulling newly added track")

        spotifyService.getPlaylistTracks(playlistId, 1, newTrackIndex)
                .subscribeOn(Schedulers.io())
                .subscribe { response ->
                    if (response.body() != null) {
                        val pager = response.body()!!
                        playlistTracks.add(newTrackIndex, pager.items[0])
                        stateStream.accept(DisplayTracks(playlistTracks.subList(currentlyPlayingIndex, playlistTracks.size)))
                    }
                }
                .addTo(subscriptions)
    }

    private fun handleCurrentlyPlayingUpdatePayload(payloadString: String) {
        val regexMatcher = Pattern.compile(CURRENTLY_PLAYING_UPDATE.regex).matcher(payloadString)

        // Host is notifying us that the currently playing index has changed
        if (regexMatcher.find()) {
            try {
                currentlyPlayingIndex = regexMatcher.group(1).toInt()
                // Don't interrupt client initiation
                if (!isBeingInitiated) {
                    if (currentlyPlayingIndex < 0 || currentlyPlayingIndex > playlistTracks.size) {
                        Timber.e("Invalid index was sent")
                    } else {
                        stateStream.accept(DisplayTracks(playlistTracks.subList(currentlyPlayingIndex, playlistTracks.size)))
                    }
                }
            } catch (exception: NumberFormatException) {
                Timber.e("Invalid index was sent")
                currentlyPlayingIndex = -1
            }
        } else {
            Timber.e("Something went wrong. Regex failed matching for $CURRENTLY_PLAYING_UPDATE")
        }
    }

    private fun handleHostDisconnectingPayload() {
        Timber.d("Host indicated it is shutting down")
        hostDisconnect = true
        stateStream.accept(ShowHostDisconnect)
    }

    private fun handleClientTrackSelection(action: ClientSelectedATrack) {
        if (action.trackUri.isEmpty()) {
            Timber.d("Can't build track request for URI: ${action.trackUri}, user ID: ${currentUser.id}")
            return
        } else {
            val payloadString = String.format(NearbyDevicesMessage.SONG_REQUEST.messageFormat, action.trackUri, currentUser.id)
            stateStream.accept(SendPayloadToHost(hostEndpoint, Payload.fromBytes(payloadString.toByteArray())))
        }
    }

    private fun handleClientFollowRequest() {
        spotifyService.followPlaylist(playlistId)
                .subscribeOn(Schedulers.io())
                .subscribe({
                    Timber.d("Successfully followed playlist")

                    if (!hostDisconnect) {
                        successfulConnectionFlag = false
                    }
                    stateStream.accept(CloseClient)
                }, {
                    Timber.e("Failed to follow playlist")
                    Timber.e(it)
                    stateStream.accept(CloseClient)
                })
                .addTo(subscriptions)
    }

    sealed class ClientState {
        data class ShutdownService(val messageResourceId: Int) : ClientState()
        data class ConnectToHost(val endpoint: String) : ClientState()
        object ShowHostDisconnect : ClientState()
        data class DisplayTracks(val trackList: List<PlaylistTrack>) : ClientState()
        data class SendPayloadToHost(
                val endpoint: String,
                val payload: Payload
        ) : ClientState()

        object CloseClient : ClientState()
        data class ClientInitiationComplete(val queueTitle: String) : ClientState()
        object DisplayLoading : ClientState()
    }

    sealed class ClientAction {
        data class ServiceStarted(
                val queueTitle: String,
                val hostEndpoint: String?
        ) : ClientAction()

        object ConnectionToHostSuccessful : ClientAction()
        object ConnectionToHostFailed : ClientAction()
        object HostRejectedConnection : ClientAction()
        data class HandleHostPayload(val payload: Payload) : ClientAction()
        object ClientRequestedPlaylistFollow : ClientAction()
        data class ClientSelectedATrack(val trackUri: String) : ClientAction()
        object ClientRequestedDisconnect : ClientAction()
    }
}