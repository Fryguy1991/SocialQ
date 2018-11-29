package com.chrisfry.socialq.userinterface.activities

import android.content.*
import android.os.*
import android.util.Log
import android.view.Menu
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.RequestType
import com.chrisfry.socialq.model.AccessModel
import com.chrisfry.socialq.model.ClientRequestData
import com.chrisfry.socialq.model.SongRequestData
import com.chrisfry.socialq.services.PlayQueueService
import com.chrisfry.socialq.userinterface.adapters.HostTrackListAdapter
import com.chrisfry.socialq.userinterface.adapters.IItemSelectionListener
import com.chrisfry.socialq.userinterface.adapters.SelectablePlaylistAdapter
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration
import com.google.gson.JsonArray
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationResponse
import kaaes.spotify.webapi.android.SpotifyService
import kaaes.spotify.webapi.android.models.PlaylistSimple
import kaaes.spotify.webapi.android.models.PlaylistTrack
import kaaes.spotify.webapi.android.models.UserPublic
import java.util.*

abstract class HostActivityKotlin : BaseActivity(), PlayQueueService.PlayQueueServiceListener,
        IItemSelectionListener<String> {
    private val TAG = HostActivityKotlin::class.java.name

    // UI element references
    private var mNextButton: View? = null
    private var mPlayPauseButton: ImageView? = null

    // Track list elements
    private var mQueueList: RecyclerView? = null
    private var mTrackDisplayAdapter: HostTrackListAdapter? = null

    private var mPlayQueueService: PlayQueueService? = null

    // Flag to determine if the service is bound or not
    private var mIsServiceBound = false
    // Cached value for playing index (used to inform new clients)
    protected var mCachedPlayingIndex = 0
    // Boolean flag to store if queue should be "fair play"
    private var mIsQueueFairPlay: Boolean = false
    // List containing client song requests
    private val mSongRequests = ArrayList<SongRequestData>()
    // Reference to base playlist dialog
    private var mBasePlaylistDialog: AlertDialog? = null
    // Reference to base playlist ID for loading when service is connected
    private var mBasePlaylistId = ""
    // Flag for storing if the player has been activated
    private var mIsPlayerActive = false
    // Flag for storing if a base playlist has been loaded
    private var mWasBasePlaylistLoaded = false

    // Object for connecting to/from play queue service
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            Log.d(TAG, "Service Connected")
            val binder = iBinder as PlayQueueService.PlayQueueBinder
            mPlayQueueService = binder.service

            // Setup activity for callbacks
            mPlayQueueService!!.addPlayQueueServiceListener(this@HostActivityKotlin)

            setupQueueList()

            // Load base playlist if one was selected
            if (!mBasePlaylistId.isEmpty()) {
                loadBasePlaylist(mBasePlaylistId)
            }
            //            setupShortDemoQueue();
            //            setupLongDemoQueue();
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mPlayQueueService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.host_screen)

        // Set fair play flag from intent (or default to app boolean default)
        mIsQueueFairPlay = intent.getBooleanExtra(AppConstants.FAIR_PLAY_KEY, resources.getBoolean(R.bool.fair_play_default))

        initUi()
        addListeners()

        // Allow network operation in main thread
        val policy = StrictMode.ThreadPolicy.Builder()
                .permitAll().build()
        StrictMode.setThreadPolicy(policy)

        accessScopes = arrayOf("user-read-private", "streaming", "playlist-modify-private", "playlist-read-private")

        // Request access token from Spotify
        Log.d(TAG, "Requesting access token from Spotify")
        requestNewAccessToken()
    }

    private fun initUi() {
        // Initialize UI elements
        mNextButton = findViewById(R.id.btn_next)
        mPlayPauseButton = findViewById(R.id.btn_play_pause)
        mQueueList = findViewById(R.id.rv_queue_list_view)

        // Show queue title as activity title
        title = intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY)

        // Stop soft keyboard from pushing UI up
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }

    private fun addListeners() {
        mNextButton!!.setOnClickListener { mPlayQueueService!!.requestPlayNext() }

        mPlayPauseButton!!.setOnClickListener { view -> handlePlayPause(view.contentDescription == "queue_playing") }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val requestType = RequestType.getRequestTypeFromRequestCode(requestCode)
        Log.d(TAG, "Received request type: $requestType")

        // Handle request result
        when (requestType) {
            RequestType.SPOTIFY_AUTHENTICATION_REQUEST -> {
                val response = AuthenticationClient.getResponse(resultCode, data)
                if (response.type == AuthenticationResponse.Type.TOKEN) {
                    if (mPlayQueueService == null) {
                        Log.d(TAG, "First access token granted.  Initialize play queue service and start host connection")

                        // Show dialog for selecting base playlist if user has playlists to show
                        val options = HashMap<String, Any>()
                        options[SpotifyService.LIMIT] = AppConstants.SPOTIFY_SEARCH_LIMIT
                        val playlistPager = mSpotifyService.getPlaylists(mCurrentUser!!.id, options)
                        if (playlistPager.items.size > 0) {
                            showBasePlaylistDialog(playlistPager.items)
                        } else {
                            // If no existing Spotify playlists don't show dialog and create one from scratch
                            createPlaylistForQueue()
                            startPlayQueueService()
                        }
                    } else {
                        Log.d(TAG, "New access token granted.  Update service access token")

                        // Update service with new access token
                        mPlayQueueService!!.notifyServiceAccessTokenChanged(response.accessToken)
                    }
                } else {
                    Log.d(TAG, "Authentication Response: " + response.error)
                    Toast.makeText(this@HostActivityKotlin, getString(R.string.toast_authentication_error_host), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            RequestType.SEARCH_REQUEST -> if (resultCode == RESULT_OK) {
                val trackUri = data!!.getStringExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY)
                handleSongRequest(SongRequestData(trackUri, mCurrentUser!!))
            }
            RequestType.NONE -> Log.e(TAG, "Unhandled request code: $requestCode")
        }// Do nothing.  Host activity should not handle BT events and if we got NONE back something is wrong
    }

    private fun startPlayQueueService() {
        // Start service that will play and control queue
        val startPlayQueueIntent = Intent(this, PlayQueueService::class.java)
        startPlayQueueIntent.putExtra(AppConstants.SERVICE_PLAYLIST_ID_KEY, mPlaylist!!.id)
        startService(startPlayQueueIntent)

        // Bind activity to service
        mIsServiceBound = bindService(startPlayQueueIntent, mServiceConnection, Context.BIND_AUTO_CREATE)

        // All logged in and good to go.  Start host connection.
        startHostConnection(intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY))
    }

    override fun onBackPressed() {

        val dialogBuilder = AlertDialog.Builder(this)
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
            if (savePlaylistCheckbox.isChecked) {
                Log.d(TAG, "Updating SocialQ playlist details")

                val playlistName = playlistNameEditText.text.toString()

                // Create body parameters for modifying playlist details
                val playlistParameters = HashMap<String, Any>()
                playlistParameters["name"] = if (playlistName.isEmpty()) getString(R.string.default_playlist_name) else playlistName

                mSpotifyService.changePlaylistDetails(mCurrentUser!!.id, mPlaylist!!.id, playlistParameters)
            } else {
                unfollowQueuePlaylist()
            }

            dialog.dismiss()
            super.onBackPressed()
        }

        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which ->
            // Don't actually want to close the queue
            dialog.dismiss()
        }

        dialogBuilder.create().show()
    }

    private fun showBasePlaylistDialog(playlists: List<PlaylistSimple>) {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle(getString(R.string.select_base_playlist))

        // Inflate content view and get references to UI elements
        val contentView = layoutInflater.inflate(R.layout.base_playlist_dialog, null)
        val playlistList = contentView.findViewById<RecyclerView>(R.id.rv_playlist_list)

        // Add recycler view item decoration
        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        playlistList.layoutManager = layoutManager
        playlistList.addItemDecoration(QueueItemDecoration(applicationContext))

        // Setup list adapter
        val playlistAdapter = SelectablePlaylistAdapter()
        playlistAdapter.listener = this
        playlistAdapter.updateAdapter(playlists)
        playlistList.adapter = playlistAdapter

        dialogBuilder.setView(contentView)

        dialogBuilder.setNeutralButton(R.string.fresh_playlist) { dialog, which ->
            dialog.dismiss()
            Log.d(TAG, "User selected not to use a base playlist")
            createPlaylistForQueue()
            startPlayQueueService()
        }

        dialogBuilder.setOnCancelListener {
            Log.d(TAG, "User didn't complete base playlist dialog")
            createPlaylistForQueue()
            startPlayQueueService()
        }

        mBasePlaylistDialog = dialogBuilder.create()
        mBasePlaylistDialog!!.show()
    }

    private fun createPlaylistForQueue() {
        // Create body parameters for new playlist
        val playlistParameters = HashMap<String, Any>()
        playlistParameters["name"] = getString(R.string.default_playlist_name)
        playlistParameters["public"] = true
        playlistParameters["collaborative"] = false
        playlistParameters["description"] = "Playlist created by the SocialQ App."

        Log.d(TAG, "Creating playlist for the SocialQ")
        mPlaylist = mSpotifyService.createPlaylist(mCurrentUser!!.id, playlistParameters)
    }

    private fun loadBasePlaylist(playlistId: String) {
        Log.d(TAG, "Loading base playlist with ID: $playlistId")

        mWasBasePlaylistLoaded = true
        val basePlaylist = mSpotifyService.getPlaylist(mCurrentUser!!.id, playlistId)

        // Adding with base user ensure host added tracks are sorted within the base playlist
        val baseUser = UserPublic()
        baseUser.id = AppConstants.BASE_USER_ID
        baseUser.display_name = resources.getString(R.string.base_playlist)

        // Retrieve all tracks
        val playlistTracks = ArrayList<PlaylistTrack>()
        // Can only retrieve 100 tracks at a time
        run {
            var i = 0
            while (i < basePlaylist.tracks.total) {
                val iterationQueryParameters = HashMap<String, Any>()
                iterationQueryParameters["offset"] = i

                // Retrieve max next 100 tracks
                val iterationTracks = mSpotifyService.getPlaylistTracks(mCurrentUser!!.id, playlistId, iterationQueryParameters)

                playlistTracks.addAll(iterationTracks.items)
                i += 100
            }
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
                // Can't add local tracks (local to playlist owner's device)
                if (!track.is_local) {
                    val requestData = SongRequestData(track.track.uri, baseUser)
                    mSongRequests.add(requestData)

                    urisArray.add(track.track.uri)
                }
            }
            val queryParameters = HashMap<String, Any>()
            val bodyParameters = HashMap<String, Any>()
            bodyParameters["uris"] = urisArray

            // Add max 100 tracks to the playlist
            mSpotifyService.addTracksToPlaylist(mCurrentUser!!.id, mPlaylist!!.id, queryParameters, bodyParameters)
        }
        mPlayQueueService!!.notifyServiceQueueHasChanged()
    }

    protected fun handleSongRequest(songRequest: SongRequestData?) {
        if (songRequest != null && !songRequest.uri.isEmpty()) {
            Log.d(TAG, "Received request for URI: " + songRequest.uri + ", from User ID: " + songRequest.user.id)

            // Add track to request list
            mSongRequests.add(songRequest)

            val willNextSongBeWrong: Boolean
            // Add track and figure out if our next song will be wrong
            if (mIsQueueFairPlay) {
                willNextSongBeWrong = addNewTrackFairplay(songRequest)
            } else {
                willNextSongBeWrong = addNewTrack(songRequest)
            }

            if (willNextSongBeWrong) {
                // If we changed the next track notify service next track will be incorrect
                mPlayQueueService!!.notifyServiceMetaDataIsStale(songRequest.uri)
            } else {
                mPlayQueueService!!.notifyServiceQueueHasChanged()
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
    private fun addNewTrackFairplay(songRequest: SongRequestData): Boolean {
        // Position of new track needs to go before first repeat that doesn't have a song of the requestee inside
        // EX (Requestee = 3): 1 -> 2 -> 3 -> 1 -> 2 -> 1  New 3 track goes before 3rd track by 1
        // PLAYLIST RESULT   : 1 -> 2 -> 3 -> 1 -> 2 -> 3 -> 1
        var newTrackPosition = 0

        val clientRepeatHash = HashMap<String, Boolean>()

        // Start inspecting song requests
        while (newTrackPosition < mSongRequests.size) {
            val currentRequestUserId = mSongRequests[newTrackPosition].user.id

            // Base playlist track. Check if we can replace it
            if (currentRequestUserId == AppConstants.BASE_USER_ID) {
                // If player is not active we can add a track at index 0 (replace base playlist)
                // because we haven't started the playlist. Else don't cause the base playlist
                // track may currently be playing
                if (newTrackPosition == 0 && !mIsPlayerActive || newTrackPosition > 0) {
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
        if (mWasBasePlaylistLoaded) {
            // Position of new track needs to go before first base playlist track

            // Start inspecting song requests
            var newTrackPosition = 0
            while (newTrackPosition < mSongRequests.size) {
                val currentRequestUserId = mSongRequests[newTrackPosition].user.id

                // Base playlist track. Check if we can replace it
                if (currentRequestUserId == AppConstants.BASE_USER_ID) {
                    // If player is not active we can add a track at index 0 (replace base playlist)
                    // because we haven't started the playlist. Else don't cause the base playlist
                    // track may currently be playing
                    if (newTrackPosition == 0 && !mIsPlayerActive || newTrackPosition > 0) {
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
            return mSongRequests.size == 2
        }
    }

    private fun injectTrackToPosition(newTrackPosition: Int, songRequest: SongRequestData): Boolean {
        if (newTrackPosition == mSongRequests.size) {
            // No base playlist track found add track to end of playlist
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

    private fun unfollowQueuePlaylist() {
        // Unfollow the playlist created for SocialQ
        if (mCurrentUser != null && mPlaylist != null) {
            Log.d(TAG, "Unfollowing playlist created for the SocialQ")

            mSpotifyService.unfollowPlaylist(mCurrentUser!!.id, mPlaylist!!.id)
            mPlaylist = null
        }
    }

    override fun onDestroy() {
        // Unbind from the PlayQueueService
        if (mIsServiceBound) {
            unbindService(mServiceConnection)
            mIsServiceBound = false
        }

        // Remove HostActivity as a listener to PlayQueueService and stop the service
        if (mPlayQueueService != null) {
            mPlayQueueService!!.removePlayQueueServiceListener(this)
            mPlayQueueService!!.stopSelf()
        }

        // This should trigger access request thread to end if it is running
        AccessModel.reset()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_screen_menu, menu)
        return true
    }

    private fun setupQueueList() {
        mTrackDisplayAdapter = HostTrackListAdapter(applicationContext)
        mQueueList!!.adapter = mTrackDisplayAdapter
        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        mQueueList!!.layoutManager = layoutManager
        mQueueList!!.addItemDecoration(QueueItemDecoration(applicationContext))
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

        if (currentPlayingIndex >= mPlaylist!!.tracks.total) {
            mTrackDisplayAdapter!!.updateAdapter(ArrayList())
        } else {
            mTrackDisplayAdapter!!.updateAdapter(createDisplayList(mPlaylistTracks.subList(currentPlayingIndex, mPlaylist!!.tracks.total)))
        }

        // Notify clients queue has been updated
        notifyClientsQueueUpdated(currentPlayingIndex)
    }

    override fun onQueuePause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mPlayPauseButton!!.setImageDrawable(resources.getDrawable(R.drawable.play_button, this.theme))
        } else {
            mPlayPauseButton!!.setImageDrawable(resources.getDrawable(R.drawable.play_button))
        }
        mPlayPauseButton!!.contentDescription = "queue_paused"
    }

    override fun onQueuePlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mPlayPauseButton!!.setImageDrawable(resources.getDrawable(R.drawable.pause_button, this.theme))
        } else {
            mPlayPauseButton!!.setImageDrawable(resources.getDrawable(R.drawable.pause_button))
        }
        mPlayPauseButton!!.contentDescription = "queue_playing"
    }

    override fun onQueueUpdated() {
        // Refresh playlist and update UI
        refreshPlaylist()

        mTrackDisplayAdapter!!.updateAdapter(createDisplayList(mPlaylistTracks.subList(mCachedPlayingIndex, mPlaylist!!.tracks.total)))

        notifyClientsQueueUpdated(mCachedPlayingIndex)
    }

    override fun onPlayerActive() {
        // Change flag that allows new tracks to be added at the very beginning of the playlist
        mIsPlayerActive = true
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

    /**
     * Item selection method for playlist ID in base playlist dialog
     *
     * @param selectedItem - ID of the playlist that was selected
     */
    override fun onItemSelected(selectedItem: String) {
        if (mBasePlaylistDialog != null && mBasePlaylistDialog!!.isShowing) {
            mBasePlaylistDialog!!.dismiss()
            createPlaylistForQueue()
            startPlayQueueService()
            mBasePlaylistId = selectedItem
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

        mSpotifyService.addTracksToPlaylist(mCurrentUser!!.id, mPlaylist!!.id, queryParameters, bodyParameters)

        // String for testing fair play (simulate a user name)
        val userId = "fake_user"
        //        String userId = mCurrentUser.id;

        mSongRequests.add(SongRequestData("spotify:track:0p8fUOBfWtGcaKGiD9drgJ", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:6qtg4gz3DhqOHL5BHtSQw8", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:57bgtoPSgt236HzfBOd8kj", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:7lGh1Dy02c5C0j3tj9AVm3", mCurrentUser!!))

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

        mSpotifyService.addTracksToPlaylist(mCurrentUser!!.id, mPlaylist!!.id, queryParameters, bodyParameters)


        // String for testing fair play (simulate a user name)
        //        String userId = "fake_user";
        //        String userId = mCurrentUser.id;

        mSongRequests.add(SongRequestData("spotify:track:0p8fUOBfWtGcaKGiD9drgJ", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:6qtg4gz3DhqOHL5BHtSQw8", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:57bgtoPSgt236HzfBOd8kj", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:4VbDJMkAX3dWNBdn3KH6Wx", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:2jnvdMCTvtdVCci3YLqxGY", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:419qOkEdlmbXS1GRJEMntC", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:1jvqZQtbBGK5GJCGT615ao", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:6cG3kY60HMcFqiZN8frkXF", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:0dqrAmrvQ6fCGNf5T8If5A", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:0wHNrrefyaeVewm4NxjxrX", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:1hh4GY1zM7SUAyM3a2ziH5", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:5Cl9GDb0AyQnppRr6q7ldb", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:7D180Q77XAEP7atBLmMTgK", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:2uxL6E8Yq0Psc1V9uBtC4F", mCurrentUser!!))
        mSongRequests.add(SongRequestData("spotify:track:7lGh1Dy02c5C0j3tj9AVm3", mCurrentUser!!))

        mPlayQueueService!!.notifyServiceQueueHasChanged()
    }

    abstract fun initiateNewClient(client: Any)

    protected abstract fun startHostConnection(queueTitle: String)

    protected abstract fun notifyClientsQueueUpdated(currentPlayingIndex: Int)
}