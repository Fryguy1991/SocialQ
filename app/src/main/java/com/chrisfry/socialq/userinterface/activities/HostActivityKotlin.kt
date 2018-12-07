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
import com.chrisfry.socialq.model.ClientRequestData
import com.chrisfry.socialq.services.HostService
import com.chrisfry.socialq.userinterface.App
import com.chrisfry.socialq.userinterface.adapters.HostTrackListAdapter
import com.chrisfry.socialq.userinterface.adapters.IItemSelectionListener
import com.chrisfry.socialq.userinterface.adapters.SelectablePlaylistAdapter
import com.chrisfry.socialq.userinterface.views.QueueItemDecoration
import kaaes.spotify.webapi.android.models.PlaylistSimple

open class HostActivityKotlin : BaseActivity(), HostService.HostServiceListener,
        IItemSelectionListener<String> {
    private val TAG = HostActivityKotlin::class.java.name

    // UI element references
    private lateinit var nextButton: View
    private lateinit var playPauseButton: ImageView

    // Track list elements
    private lateinit var queueList: RecyclerView
    private lateinit var trackDisplayAdapter: HostTrackListAdapter

    private lateinit var hostService: HostService
    // Boolean flag to store if queue should be "fair play"
    private var isQueueFairPlay = false
    // Flag to determine if the service is bound or not
    private var isServiceBound = false
    // Reference to base playlist dialog
    private var basePlaylistDialog: AlertDialog? = null

    // Object for connecting to/from play queue service
    private val hostServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            Log.d(TAG, "Host service Connected")
            val binder = iBinder as HostService.HostServiceBinder

            hostService = binder.getService()
            hostService.setPlayQueueServiceListener(this@HostActivityKotlin)
            isServiceBound = true
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            Log.d(TAG, "Host service disconnected")
            unbindService(this)
            isServiceBound = false
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.host_screen)

        // Set fair play flag from intent (or default to app boolean default)
        isQueueFairPlay = intent.getBooleanExtra(AppConstants.FAIR_PLAY_KEY, resources.getBoolean(R.bool.fair_play_default))

        initUi()
        addListeners()
        setupQueueList()

        // Allow network operation in main thread
        val policy = StrictMode.ThreadPolicy.Builder()
                .permitAll().build()
        StrictMode.setThreadPolicy(policy)

        startHostService()
    }

    private fun initUi() {
        // Initialize UI elements
        nextButton = findViewById(R.id.btn_next)
        playPauseButton = findViewById(R.id.btn_play_pause)
        queueList = findViewById(R.id.rv_queue_list_view)

        // Show queue title as activity title
        title = intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY)

        // Stop soft keyboard from pushing UI up
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }

    private fun addListeners() {
        nextButton.setOnClickListener {
            hostService.requestPlayNext()
        }

        playPauseButton.setOnClickListener {
            view -> handlePlayPause(view.contentDescription == "queue_playing")
        }
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
                Log.e(TAG, "Unhandled request code")
            }
        }
    }

    private fun startHostService() {
        val startHostIntent = Intent(this, HostService::class.java)
        startHostIntent.putExtra(AppConstants.QUEUE_TITLE_KEY, intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY))
        startHostIntent.putExtra(AppConstants.FAIR_PLAY_KEY, intent.getBooleanExtra(AppConstants.FAIR_PLAY_KEY, resources.getBoolean(R.bool.fair_play_default)))

        // If we can't find a host service to bind to, start the host service then bind
        if (App.hasServiceBeenStarted) {
            Log.d(TAG, "Attempting to bind to host service")
            bindService(startHostIntent, hostServiceConnection, Context.BIND_AUTO_CREATE)
        } else {
            Log.d(TAG, "Starting and binding to host service")
            startService(startHostIntent)
            bindService(startHostIntent, hostServiceConnection, Context.BIND_AUTO_CREATE)
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

    override fun showBasePlaylistDialog(playlists: List<PlaylistSimple>) {
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

            hostService.noBasePlaylistSelected()
        }

        dialogBuilder.setOnCancelListener {
            Log.d(TAG, "User didn't complete base playlist dialog")

            hostService.noBasePlaylistSelected()
        }

        basePlaylistDialog = dialogBuilder.create()
        basePlaylistDialog!!.show()
    }

    override fun onDestroy() {
        // Unbind from the PlayQueueService
        if (isServiceBound) {
            hostService.removePlayQueueServiceListener()
            unbindService(hostServiceConnection)
            isServiceBound = false
        }

        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_screen_menu, menu)
        return true
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            playPauseButton.setImageDrawable(resources.getDrawable(R.drawable.play_button, this.theme))
        } else {
            playPauseButton.setImageDrawable(resources.getDrawable(R.drawable.play_button))
        }
        playPauseButton.contentDescription = "queue_paused"
    }

    override fun onQueuePlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            playPauseButton.setImageDrawable(resources.getDrawable(R.drawable.pause_button, this.theme))
        } else {
            playPauseButton.setImageDrawable(resources.getDrawable(R.drawable.pause_button))
        }
        playPauseButton.contentDescription = "queue_playing"
    }

    override fun onQueueUpdated(songRequests: List<ClientRequestData>) {
        // Display updated track list
        trackDisplayAdapter.updateAdapter(songRequests)
    }

    override fun closeHost() {
        stopHostService()
        finish()
    }

    override fun showClientConnected() {
        Toast.makeText(this, "A client has joined!", Toast.LENGTH_SHORT).show()
    }

    override fun showClientDisconnected() {
        Toast.makeText(this, "A client has disconnected!", Toast.LENGTH_SHORT).show()
    }

    /**
     * Item selection method for playlist ID in base playlist dialog
     *
     * @param selectedItem - ID of the playlist that was selected
     */
    override fun onItemSelected(selectedItem: String) {
        if (basePlaylistDialog != null && basePlaylistDialog!!.isShowing) {
            basePlaylistDialog!!.dismiss()
            hostService.basePlaylistSelected(selectedItem)
        }
    }

    override fun showLoadingScreen() {
        // TODO: Show a loading screen when service is doing long data retrieval
    }
}