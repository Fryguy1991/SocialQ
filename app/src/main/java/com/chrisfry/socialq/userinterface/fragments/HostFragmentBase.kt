package com.chrisfry.socialq.userinterface.fragments

import android.content.*
import android.os.*
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import android.widget.*
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.model.ClientRequestData
import com.chrisfry.socialq.model.SongRequestData
import com.chrisfry.socialq.services.PlayQueueService
import com.chrisfry.socialq.userinterface.adapters.HostTrackListAdapter
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration
import com.spotify.sdk.android.player.ConnectionStateCallback
import com.spotify.sdk.android.player.Error
import kaaes.spotify.webapi.android.models.Playlist
import kaaes.spotify.webapi.android.models.PlaylistTrack
import kaaes.spotify.webapi.android.models.UserPrivate
import java.util.ArrayList
import java.util.HashMap

abstract class HostFragmentBase : SpotifyFragment(), PlayQueueService.PlayQueueServiceListener, ConnectionStateCallback {
    companion object {
        private val TAG = HostFragmentBase::class.java.name
    }

    // Listener for fragment notifications
    var listener: BaseHostFragmentListener? = null

    // UI element references
    private lateinit var mNextButton: View
    private lateinit var mPlayPauseButton: ImageView

    // Track list elements
    private lateinit var mQueueList: RecyclerView
    private var mTrackDisplayAdapter: HostTrackListAdapter? = null

    // Spotify elements
    private var mPlayQueueService: PlayQueueService? = null
    protected lateinit var mCurrentUser: UserPrivate
    protected lateinit var mPlaylist: Playlist

    // String for title of the queue
    private var mQueueTitle: String? = ""
    // Flag to determine if the service is bound or not
    private var mIsServiceBound = false
    // Cached value for playing index (used to inform new clients)
    protected var mCachedPlayingIndex = 0
    // Boolean flag to store if queue should be "fair play"
    private var mIsQueueFairPlay: Boolean? = false
    // List containing client song requests
    private val mSongRequests = ArrayList<SongRequestData>()
    // Flag to cache if the player is playing or not
    private var mIsPlaying = false

    // Object for connecting to/from play queue service
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            Log.d(TAG, "Service Connected")
            val binder = iBinder as PlayQueueService.PlayQueueBinder
            mPlayQueueService = binder.service

            // Setup activity for callbacks
            mPlayQueueService?.addPlayQueueServiceListener(this@HostFragmentBase)

//            setupShortDemoQueue()
            setupLongDemoQueue()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mPlayQueueService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Host Fragment onCreate")
        super.onCreate(savedInstanceState)

        // Set fair play flag from intent (or default to app boolean default)
        mIsQueueFairPlay = arguments?.getBoolean(AppConstants.FAIR_PLAY_KEY, resources.getBoolean(R.bool.fair_play_default))
        // Retrieve queue title name
        mQueueTitle = arguments?.getString(AppConstants.QUEUE_TITLE_KEY, resources.getString(R.string.default_playlist_name))

        // Start service that will play and control queue
        if (context != null) {
            initSpotifyElements()

            val startPlayQueueIntent = Intent(context, PlayQueueService::class.java)
            startPlayQueueIntent.putExtra(AppConstants.SERVICE_PLAYLIST_ID_KEY, mPlaylist.id)
            context!!.startService(startPlayQueueIntent)

            // Bind to the service
            mIsServiceBound = context!!.bindService(startPlayQueueIntent, mServiceConnection, Context.BIND_AUTO_CREATE)

            // All logged in and good to go.  Start host connection.
            startHostConnection(mQueueTitle!!)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d(TAG, "Host Fragment onCreateView")
        val baseView = inflater.inflate(R.layout.host_screen, container, false)

        initUi(baseView)
        addListeners()
        setupQueueList()

        return baseView
    }

    private fun initUi(baseView: View) {
        // Initialize UI elements
        mNextButton = baseView.findViewById(R.id.btn_next)
        mPlayPauseButton = baseView.findViewById(R.id.btn_play_pause)
        mQueueList = baseView.findViewById(R.id.rv_queue_list_view)
    }

    private fun addListeners() {
        mNextButton.setOnClickListener {
            mPlayQueueService!!.requestPlayNext()
        }

        mPlayPauseButton.setOnClickListener {
            view -> handlePlayPause(view.contentDescription == "queue_playing")
        }
    }

    private fun initSpotifyElements() {
        mCurrentUser = mSpotifyService.me
        mPlaylist = createPlaylistForQueue()
    }

    override fun handleOnBackPressed(): Boolean {

        val dialogBuilder = AlertDialog.Builder(context!!)
        dialogBuilder.setTitle(getString(R.string.close_host_dialog_title))

        // Inflate content view and get references to UI elements
        val contentView = layoutInflater.inflate(R.layout.save_playlist_dialog, null)
        val playlistNameEditText = contentView.findViewById<EditText>(R.id.et_save_playlist_name)
        val savePlaylistCheckbox = contentView.findViewById<CheckBox>(R.id.cb_save_playlist)

        dialogBuilder.setView(contentView)

        // If save playlist box is checked, enable edit text for playlist name
        // If save playlist box is unchecked, disabled edit text and clear field value
        savePlaylistCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
            playlistNameEditText.isEnabled = isChecked

            if (!isChecked) {
                playlistNameEditText.setText("")
            }
        }

        dialogBuilder.setPositiveButton(R.string.confirm) { dialog, which ->
            Log.d(TAG, "User selected to close the host")

            if (savePlaylistCheckbox.isChecked()) {
                Log.d(TAG, "Updating SocialQ playlist details")

                val playlistName = playlistNameEditText.getText().toString()

                // Create body parameters for modifying playlist details
                val playlistParameters = HashMap<String, Any>()
                playlistParameters["name"] = if (playlistName.isEmpty()) getString(R.string.default_playlist_name) else playlistName

                mSpotifyService.changePlaylistDetails(mCurrentUser!!.id, mPlaylist!!.id, playlistParameters)
            } else {
                unfollowQueuePlaylist()
            }

            listener?.hostShutDown()
            dialog.dismiss()
        }

        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which ->
            Log.d(TAG, "User selected to keep the host open")
            dialog.dismiss()
        }

        dialogBuilder.create().show()
        return true
    }

    override fun onResume() {
        Log.d(TAG, "Host Fragment Resumed")
        super.onResume()

        // Refresh track display list
        if (mTrackDisplayAdapter != null) {
            mTrackDisplayAdapter!!.updateAdapter(createDisplayList(mPlaylist.tracks.items.subList(mCachedPlayingIndex, mPlaylist.tracks.items.size)))
        }

        // Ensure our play/pause button loads into the right state
        if (mIsPlaying) {
            onQueuePlay()
        } else {
            onQueuePause()
        }

        // Fragment is now visible, show the queue title
        listener?.showHostTitle()
    }


    private fun createPlaylistForQueue(): Playlist {
        // Create body parameters for new playlist
        val playlistParameters = HashMap<String, Any>()
        playlistParameters["name"] = getString(R.string.default_playlist_name)
        playlistParameters["public"] = true
        playlistParameters["collaborative"] = false
        playlistParameters["description"] = "Playlist created by the SocialQ App."

        Log.d(TAG, "Creating playlist for the SocialQ")
        return mSpotifyService.createPlaylist(mCurrentUser.id, playlistParameters)
    }

    protected fun handleSongRequest(songRequest: SongRequestData?) {
        if (songRequest != null && !songRequest.uri.isEmpty()) {
            Log.d(TAG, "Received request for URI: " + songRequest.uri + ", from User ID: " + songRequest.user.id)

            // Add track to request list
            mSongRequests.add(songRequest)

            // Don't need to worry about managing the queue if fairplay is off
            if (mIsQueueFairPlay == true) {
                if (injectNewTrack(songRequest)) {
                    // If we changed the next track notify service meta data is out of sync
                    mPlayQueueService!!.notifyServiceMetaDataIsStale()
                } else {
                    mPlayQueueService!!.notifyServiceQueueHasChanged()
                }
            } else {
                addTrackToPlaylist(songRequest.uri)
                if (mSongRequests.size == 2) {
                    // If we changed the next track notify service meta data is out of sync
                    mPlayQueueService!!.notifyServiceMetaDataIsStale()
                } else {
                    mPlayQueueService!!.notifyServiceQueueHasChanged()
                }
            }
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
        mSpotifyService.addTracksToPlaylist(mCurrentUser!!.id, mPlaylist!!.id, queryParameters, bodyParameters)
    }

    /**
     * Injects most recently added track to fairplay position
     *
     * @param songRequest - Song request containing requestee and track info
     * @return - Boolean flag for if the track was added at the next position
     */
    private fun injectNewTrack(songRequest: SongRequestData): Boolean {
        // Position of new track needs to go before first repeat that doesn't have a song of the requestee inside
        // EX (Requestee = 3): 1 -> 2 -> 3 -> 1 -> 2 -> 1  New 3 track goes before 3rd track by 1
        // PLAYLIST RESULT   : 1 -> 2 -> 3 -> 1 -> 2 -> 3 -> 1
        var newTrackPosition: Int

        // Only run check for song injection if there are 3 or more requests tracked
        if (mSongRequests.size > 2) {
            val clientRepeatHash = HashMap<String, Boolean>()

            // Start inspecting song requests
            newTrackPosition = 0
            while (newTrackPosition < mSongRequests.size) {
                val currentRequestUserId = mSongRequests[newTrackPosition].user.id

                if (currentRequestUserId == songRequest.user.id) {
                    // If we found a requestee track set open repeats to true (found requestee track)
                    for (mapEntry in clientRepeatHash.entries) {
                        mapEntry.setValue(true)
                    }
                } else {
                    // Found a request NOT from the requestee client
                    if (clientRepeatHash.containsKey(currentRequestUserId)) {
                        // Client already contained in hash (repeat)
                        if (clientRepeatHash[currentRequestUserId] == true) {
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
        } else {
            // If not enough requests set new track position so new track will be placed at the end of the list
            newTrackPosition = mSongRequests.size
        }

        if (newTrackPosition == mSongRequests.size) {
            // No repeat found (or list too small) add track to end of playlist
            Log.d(TAG, "Adding track to end of playlist")
            addTrackToPlaylist(songRequest.uri)
            // Return true if the song being added is next (request size of 2)
            return mSongRequests.size == 1 || mSongRequests.size == 2
        } else if (newTrackPosition > mSongRequests.size) {
            // Should not be possible
            Log.e(TAG, "INVALID NEW TRACK POSITION INDEX")
            return false
        } else {
            // If new track position is not equal or greater than song request size we need to move it
            // Inject song request data to new position
            mSongRequests.add(newTrackPosition, mSongRequests.removeAt(mSongRequests.size - 1))

            Log.d(TAG, "Adding new track at playlist index: " + (newTrackPosition + mCachedPlayingIndex))
            addTrackToPlaylistPosition(songRequest.uri, newTrackPosition + mCachedPlayingIndex)

            // Return true if we're moving the added track to the "next" position
            return newTrackPosition == 1
        }
    }

    private fun refreshPlaylist() {
        mPlaylist = mSpotifyService.getPlaylist(mCurrentUser.id, mPlaylist.id)
    }

    private fun unfollowQueuePlaylist() {
        // Unfollow the playlist created for SocialQ
            Log.d(TAG, "Unfollowing playlist created for the SocialQ")

            mSpotifyService.unfollowPlaylist(mCurrentUser.id, mPlaylist.id)
    }

    override fun onLoggedIn() {
        Log.d(TAG, "User logged in")
    }

    override fun onLoggedOut() {
        Log.d(TAG, "User logged out")
    }

    override fun onLoginFailed(error: Error) {
        Log.d(TAG, "Login failed")
    }

    override fun onTemporaryError() {
        Log.d(TAG, "Temporary error occurred")
    }

    override fun onConnectionMessage(message: String) {
        Log.d(TAG, "Received connection message: $message")
    }

    override fun onDestroy() {
        Log.d(TAG, "Host Fragment Destroyed")

        // Unbind from the PlayQueueService
        if (mIsServiceBound) {
            context?.unbindService(mServiceConnection)
            mIsServiceBound = false
        }

        // Remove HostActivity as a listener to PlayQueueService and stop the service
        if (mPlayQueueService != null) {
            mPlayQueueService!!.removePlayQueueServiceListener(this)
            mPlayQueueService!!.stopSelf()
        }
        super.onDestroy()
    }

    private fun setupQueueList() {
        mTrackDisplayAdapter = HostTrackListAdapter(context!!)
        mQueueList.adapter = mTrackDisplayAdapter
        val layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        mQueueList.layoutManager = layoutManager
        mQueueList.addItemDecoration(QueueItemDecoration(context!!))
    }

    private fun handlePlayPause(isPlaying: Boolean) {
        if (isPlaying) {
            mPlayQueueService!!.requestPause()
        } else {
            mPlayQueueService!!.requestPlay()
        }
    }

    override fun onQueueNext(currentPlayingIndex: Int) {
        if (mSongRequests.size > 0) {
            mSongRequests.removeAt(0)
        }

        mCachedPlayingIndex = currentPlayingIndex

        // Refresh playlist and update UI
        refreshPlaylist()
        if (currentPlayingIndex >= mPlaylist.tracks.items.size) {
            mTrackDisplayAdapter!!.updateAdapter(ArrayList())
        } else {
            mTrackDisplayAdapter!!.updateAdapter(createDisplayList(mPlaylist.tracks.items.subList(currentPlayingIndex, mPlaylist.tracks.items.size)))
        }

        // Notify clients queue has been updated
        notifyClientsQueueUpdated(currentPlayingIndex)
    }

    override fun onQueuePause() {
        mIsPlaying = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mPlayPauseButton.setImageDrawable(resources.getDrawable(R.drawable.play_button, activity?.theme))
        } else {
            mPlayPauseButton.setImageDrawable(resources.getDrawable(R.drawable.play_button))
        }
        mPlayPauseButton.contentDescription = "queue_paused"
    }

    override fun onQueuePlay() {
        mIsPlaying = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mPlayPauseButton.setImageDrawable(resources.getDrawable (R.drawable.pause_button, activity?.theme))
        } else {
            mPlayPauseButton.setImageDrawable(resources.getDrawable(R.drawable.pause_button))
        }
        mPlayPauseButton.contentDescription = "queue_playing"
    }

    override fun onQueueUpdated() {
        // Refresh playlist and update UI
        refreshPlaylist()
        mTrackDisplayAdapter!!.updateAdapter(createDisplayList(mPlaylist.tracks.items.subList(mCachedPlayingIndex, mPlaylist.tracks.items.size)))

        notifyClientsQueueUpdated(mCachedPlayingIndex)
    }


    private fun createDisplayList(trackList: List<PlaylistTrack>): List<ClientRequestData> {
        val displayList = ArrayList<ClientRequestData>()

        // TODO: This does not guarantee correct display.  If player does not play songs in
        // correct order, the incorrect users may be displayed
        var i = 0
        while (i < trackList.size && i < mSongRequests.size) {
            displayList.add(ClientRequestData(trackList[i], mSongRequests[i].user))
            i++
        }

        return displayList
    }

    private fun setupShortDemoQueue() {
        val shortQueueString = "spotify:track:0p8fUOBfWtGcaKGiD9drgJ," +
                "spotify:track:6qtg4gz3DhqOHL5BHtSQw8," +
                "spotify:track:57bgtoPSgt236HzfBOd8kj," +
                "spotify:track:7lGh1Dy02c5C0j3tj9AVm3"


        val queryParameters = HashMap<String, Any>()
        queryParameters["uris"] = shortQueueString
        val bodyParameters = HashMap<String, Any>()

        mSpotifyService.addTracksToPlaylist(mCurrentUser.id, mPlaylist.id, queryParameters, bodyParameters)

        // String for testing fair play (simulate a user name)
        val userId = "fake_user"
        //        String userId = mCurrentUser.id;

        mSongRequests.add(SongRequestData("spotify:track:0p8fUOBfWtGcaKGiD9drgJ", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:6qtg4gz3DhqOHL5BHtSQw8", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:57bgtoPSgt236HzfBOd8kj", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:7lGh1Dy02c5C0j3tj9AVm3", mCurrentUser))

        mPlayQueueService!!.notifyServiceQueueHasChanged()
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

        mSpotifyService.addTracksToPlaylist(mCurrentUser.id, mPlaylist.id, queryParameters, bodyParameters)


        // String for testing fair play (simulate a user name)
        //        String userId = "fake_user";
        //        String userId = mCurrentUser.id;

        mSongRequests.add(SongRequestData("spotify:track:0p8fUOBfWtGcaKGiD9drgJ", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:6qtg4gz3DhqOHL5BHtSQw8", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:57bgtoPSgt236HzfBOd8kj", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:4VbDJMkAX3dWNBdn3KH6Wx", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:2jnvdMCTvtdVCci3YLqxGY", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:419qOkEdlmbXS1GRJEMntC", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:1jvqZQtbBGK5GJCGT615ao", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:6cG3kY60HMcFqiZN8frkXF", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:0dqrAmrvQ6fCGNf5T8If5A", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:0wHNrrefyaeVewm4NxjxrX", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:1hh4GY1zM7SUAyM3a2ziH5", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:5Cl9GDb0AyQnppRr6q7ldb", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:7D180Q77XAEP7atBLmMTgK", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:2uxL6E8Yq0Psc1V9uBtC4F", mCurrentUser))
        mSongRequests.add(SongRequestData("spotify:track:7lGh1Dy02c5C0j3tj9AVm3", mCurrentUser))

        mPlayQueueService!!.notifyServiceQueueHasChanged()
    }

    // Abstract methods to be implemented by classes through inheritance
    abstract fun initiateNewClient(client: Any)
    protected abstract fun startHostConnection(queueTitle: String)
    protected abstract fun notifyClientsQueueUpdated(currentPlayingIndex: Int)

    // Interface for sending events back to the activity
    interface BaseHostFragmentListener {
        fun hostShutDown()

        fun showHostTitle()
    }
}