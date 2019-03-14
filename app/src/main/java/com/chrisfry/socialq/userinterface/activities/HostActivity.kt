package com.chrisfry.socialq.userinterface.activities

import android.content.*
import android.os.*
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.RequestType
import com.chrisfry.socialq.model.ClientRequestData
import com.chrisfry.socialq.services.HostService
import com.chrisfry.socialq.userinterface.App
import com.chrisfry.socialq.userinterface.adapters.HostTrackListAdapter
import com.chrisfry.socialq.userinterface.views.PlaybackControlView
import com.chrisfry.socialq.userinterface.views.QueueItemDecoration

open class HostActivity : ServiceActivity(), HostService.HostServiceListener, PlaybackControlView.IPlaybackControlListener {
    private val TAG = HostActivity::class.java.name

    // UI ELEMENTS
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var addButton: View
    private lateinit var playbackControlView: PlaybackControlView
    // Track list elements
    private lateinit var queueList: RecyclerView
    private lateinit var trackDisplayAdapter: HostTrackListAdapter

    private lateinit var hostService: HostService
    // Boolean flag to store if queue should be "fair play"
    private var isQueueFairPlay = false
    // Flag to determine if the service is bound or not
    private var isServiceBound = false

    // Object for connecting to/from play queue service
    private val hostServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            Log.d(TAG, "Host service Connected")
            val binder = iBinder as HostService.HostServiceBinder
            hostService = binder.getService()
            isServiceBound = true

            hostService.setPlayQueueServiceListener(this@HostActivity)

            if (title.isNullOrEmpty()) {
                hostService.requestInitiation()
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            Log.d(TAG, "Host service disconnected")
            unbindService(this)
            isServiceBound = false
            finish()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (intent != null) {
            when (intent.action) {
                AppConstants.ACTION_NOTIFICATION_SEARCH -> {
                    // If receiving a notification search request start search activity
                    startSearchActivity()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.host_screen)

        // Setup the app toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.app_toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set fair play flag from intent (or default to app boolean default)
        isQueueFairPlay = intent.getBooleanExtra(AppConstants.FAIR_PLAY_KEY, resources.getBoolean(R.bool.fair_play_default))

        initUi()
        setupQueueList()

        // Allow network operation in main thread
        val policy = StrictMode.ThreadPolicy.Builder()
                .permitAll().build()
        StrictMode.setThreadPolicy(policy)

        startHostService()
    }

    override fun onNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun initUi() {
        // Initialize UI
        rootLayout = findViewById(R.id.cl_host_layout_base)
        queueList = findViewById(R.id.rv_queue_list_view)
        playbackControlView = findViewById(R.id.cv_playback_control_view)
        addButton = findViewById(R.id.btn_add_track)

        addButton.setOnClickListener(this)
        playbackControlView.setListener(this)
        playbackControlView.setOnClickListener(this)

        // Show queue title as activity title
        title = intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY)

        // Stop soft keyboard from pushing UI up
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val requestType = RequestType.getRequestTypeFromRequestCode(requestCode)
        Log.d(TAG, "Received request type: $requestType")

        // Handle request result
        when (requestType) {
            RequestType.SEARCH_REQUEST -> if (resultCode == RESULT_OK) {
                val trackUri = data!!.getStringExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY)
                hostService.hostRequestSong(trackUri)
            }
            RequestType.SPOTIFY_AUTHENTICATION_REQUEST,
            RequestType.LOCATION_PERMISSION_REQUEST -> {
                Log.e(TAG, "Host activity should not receive $requestType")
            }
            RequestType.NONE -> {
                Log.e(TAG, "Unhandled request code: $requestCode")
            }
        }
    }

    private fun startHostService() {
        val startHostIntent = Intent(this, HostService::class.java)
        startHostIntent.putExtra(AppConstants.QUEUE_TITLE_KEY, intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY))
        startHostIntent.putExtra(AppConstants.FAIR_PLAY_KEY, intent.getBooleanExtra(AppConstants.FAIR_PLAY_KEY, resources.getBoolean(R.bool.fair_play_default)))
        startHostIntent.putExtra(AppConstants.BASE_PLAYLIST_ID_KEY, intent.getStringExtra(AppConstants.BASE_PLAYLIST_ID_KEY))

        // If we can't find a host service to bind to, start the host service then bind
        if (App.hasServiceBeenStarted) {
            Log.d(TAG, "Attempting to bind to host service")
            bindService(startHostIntent, hostServiceConnection, Context.BIND_ABOVE_CLIENT)
        } else {
            Log.d(TAG, "Starting and binding to host service")
            ContextCompat.startForegroundService(this, startHostIntent)
            bindService(startHostIntent, hostServiceConnection, Context.BIND_ABOVE_CLIENT)
        }
    }

    private fun stopHostService() {
        Log.d(TAG, "Unbinding from and stopping host service")

        if (isServiceBound) {
            unbindService(hostServiceConnection)
            isServiceBound = false
        }

        val stopHostIntent = Intent(this, HostService::class.java)
        stopService(stopHostIntent)
    }

    override fun onBackPressed() {

        val dialogBuilder = AlertDialog.Builder(this)

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

                hostService.savePlaylistAs(playlistNameEditText.text.toString())
            } else {
                hostService.unfollowQueuePlaylist()
            }

            dialog.dismiss()
        }

        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which ->
            // Don't actually want to close the queue
            dialog.dismiss()
        }

        dialogBuilder.create().show()
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying host activity")

        // Unbind from the PlayQueueService
        if (isServiceBound) {
            hostService.removePlayQueueServiceListener()
            unbindService(hostServiceConnection)
            isServiceBound = false
        }

        super.onDestroy()
    }

    private fun setupQueueList() {
        trackDisplayAdapter = HostTrackListAdapter(applicationContext)
        queueList.adapter = trackDisplayAdapter
        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        queueList.layoutManager = layoutManager
        queueList.addItemDecoration(QueueItemDecoration(applicationContext))
    }

    private fun handlePlayPause(isPlaying: Boolean) {
        if (isPlaying) {
            hostService.requestPause()
        } else {
            hostService.requestPlay()
        }
    }

    override fun onQueuePause() {
        playbackControlView.setPaused()
    }

    override fun onQueuePlay() {
        playbackControlView.setPlaying()
    }

    override fun onQueueUpdated(songRequests: List<ClientRequestData>) {
        // Display updated track list
        displayTrackList(songRequests)
    }

    private fun displayTrackList(songRequests: List<ClientRequestData>) {
        when {
            songRequests.size < 0 -> {
                Log.e(TAG, "Error invalid song request list sent")
            }
            songRequests.isEmpty() -> {
                playbackControlView.shrinkLayout()
                playbackControlView.visibility = View.GONE
                trackDisplayAdapter.updateAdapter(mutableListOf())
            }
            songRequests.size == 1 -> {
                playbackControlView.visibility = View.VISIBLE
                playbackControlView.displayRequest(songRequests[0])
                trackDisplayAdapter.updateAdapter(mutableListOf())
            }
            songRequests.size > 1 -> {
                playbackControlView.visibility = View.VISIBLE
                playbackControlView.displayRequest(songRequests[0])
                trackDisplayAdapter.updateAdapter(songRequests.subList(1, songRequests.size))
            }
        }
    }

    override fun closeHost() {
        stopHostService()
        finish()
    }

    override fun showClientConnected() {
        Toast.makeText(this, getString(R.string.client_joined), Toast.LENGTH_SHORT).show()
    }

    override fun showClientDisconnected() {
        Toast.makeText(this, getString(R.string.client_disconnected), Toast.LENGTH_SHORT).show()
    }

    override fun showLoadingScreen() {
        // TODO: Show a loading screen when service is doing long data retrieval
    }

    override fun initiateView(title: String, songRequests: List<ClientRequestData>, isPlaying: Boolean) {
        Log.d(TAG, "Re-initializing host view")

        this.title = title
        displayTrackList(songRequests)
        if (isPlaying) {
            onQueuePlay()
        } else {
            onQueuePause()
        }
    }

    override fun requestPlayPause(isPlaying: Boolean) {
        handlePlayPause(isPlaying)
    }

    override fun requestSkip() {
        hostService.requestPlayNext()
    }
}