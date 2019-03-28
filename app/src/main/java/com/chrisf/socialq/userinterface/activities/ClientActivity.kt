package com.chrisf.socialq.userinterface.activities

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.os.StrictMode
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.R
import com.chrisf.socialq.business.AppConstants
import com.chrisf.socialq.enums.RequestType
import com.chrisf.socialq.services.ClientService
import com.chrisf.socialq.userinterface.App
import com.chrisf.socialq.userinterface.adapters.BasicTrackListAdapter
import com.chrisf.socialq.userinterface.views.PlaybackControlView
import com.chrisf.socialq.userinterface.views.QueueItemDecoration
import kaaes.spotify.webapi.android.models.PlaylistTrack

open class ClientActivity : ServiceActivity(), ClientService.ClientServiceListener {
    private val TAG = ClientActivity::class.java.name

    // UI ELEMENTS
    // Queue display elements
    private lateinit var queueList: RecyclerView
    private lateinit var trackDisplayAdapter: BasicTrackListAdapter
    // Button for adding a new track
    private lateinit var addButton: View
    // View for displaying currently playing track
    private lateinit var playbackControlView: PlaybackControlView

    // Reference to host endpoint ID
    private var hostEnpointId = ""
    // Reference to host queue title
    private var hostQueueTitle = ""

    // Reference to client service
    private lateinit var clientService: ClientService
    // Flag for detecting if the service is bound
    private var isServiceBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.client_screen)

        // Setup the app toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.app_toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Allow network operation in main thread
        val policy = StrictMode.ThreadPolicy.Builder()
                .permitAll().build()
        StrictMode.setThreadPolicy(policy)

        // Get queue title from intent
        val titleString = intent?.getStringExtra(AppConstants.QUEUE_TITLE_KEY)
        if (titleString.isNullOrEmpty()) {
            hostQueueTitle = getString(R.string.queue_title_default_value)
        } else {
            hostQueueTitle = titleString
        }

        // Ensure we receive an endpoint before trying to start service
        val endpointString = intent?.getStringExtra(AppConstants.ND_ENDPOINT_ID_EXTRA_KEY)
        if (!App.hasServiceBeenStarted && endpointString.isNullOrEmpty()) {
            Log.e(TAG, "Error, trying to start client with invalid endpointId")
            finish()
            return
        }

        if (!endpointString.isNullOrEmpty()) {
            hostEnpointId = endpointString
        }

        initUi()
        setupQueueList()

        startClientService()
    }

    // Object for connecting to/from client service
    private val clientServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            Log.d(TAG, "Client service Connected")
            val binder = iBinder as ClientService.ClientServiceBinder

            clientService = binder.getService()
            clientService.setClientServiceListener(this@ClientActivity)
            isServiceBound = true

            if (hostEnpointId.isNullOrEmpty()) {
                clientService.requestInitiation()
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            Log.d(TAG, "Client service disconnected")
            unbindService(this)
            isServiceBound = false
            finish()
        }
    }

    private fun initUi() {
        // Initialize UI elements
        queueList = findViewById(R.id.rv_queue_list_view)
        addButton = findViewById(R.id.btn_add_track)
        playbackControlView = findViewById(R.id.cv_playback_control_view)
        playbackControlView.hideControls()
        playbackControlView.hideUser()

        addButton.setOnClickListener(this)

        // Show queue title as activity title
        title = hostQueueTitle
    }

    private fun startClientService() {
        val startClientIntent = Intent(this, ClientService::class.java)
        startClientIntent.putExtra(AppConstants.ND_ENDPOINT_ID_EXTRA_KEY, hostEnpointId)
        startClientIntent.putExtra(AppConstants.QUEUE_TITLE_KEY, hostQueueTitle)

        // If we can't find a client service to bind to, start the client service then bind
        if (App.hasServiceBeenStarted) {
            Log.d(TAG, "Attempting to bind to client service")
            bindService(startClientIntent, clientServiceConnection, Context.BIND_AUTO_CREATE)
        } else {
            Log.d(TAG, "Starting and binding to client service")
            ContextCompat.startForegroundService(this, startClientIntent)
            bindService(startClientIntent, clientServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun stopClientService() {
        Log.d(TAG, "Unbinding from and stopping client service")

        if (isServiceBound) {
            unbindService(clientServiceConnection)
            isServiceBound = false
        }

        val stopClientIntent = Intent(this, ClientService::class.java)
        stopService(stopClientIntent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val requestType = RequestType.getRequestTypeFromRequestCode(requestCode)
        Log.d(TAG, "Received request type: $requestType")

        // Handle request result
        when (requestType) {
            RequestType.SEARCH_REQUEST -> if (resultCode == RESULT_OK) {
                val trackUri = data!!.getStringExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY)
                if (trackUri != null && !trackUri.isEmpty()) {
                    Log.d(TAG, "Client adding track to queue playlist")
                    clientService.sendTrackToHost(trackUri)
                }
            }
            RequestType.SPOTIFY_AUTHENTICATION_REQUEST,
            RequestType.LOCATION_PERMISSION_REQUEST -> {
                Log.e(TAG, "Client activity should not receive $requestType")
            }
            RequestType.NONE -> {
                Log.e(TAG, "Unhandled request code: $requestCode")
            }
        }
    }

    override fun onBackPressed() {
        val dialogBuilder = AlertDialog.Builder(this, R.style.AppDialog)

        val contentView = layoutInflater.inflate(R.layout.client_exit_dialog, null)
        val followCheckbox = contentView.findViewById<CheckBox>(R.id.cb_follow_playlist)

        dialogBuilder.setView(contentView)

        dialogBuilder.setPositiveButton(R.string.confirm) { dialog, which ->
            Log.d(TAG, "User chose to leave the queue")
            dialog.dismiss()

            // If follow is checked follow playlist with client user
            if (followCheckbox.isChecked) {
                Log.d(TAG, "User chose to follow the playlist")
                clientService.followPlaylist()
            } else {
                clientService.requestDisconnect()
            }
        }

        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which ->
            Log.d(TAG, "User chose to remain in the queue")
            dialog.dismiss()
        }

        dialogBuilder.create().show()
    }

    override fun onDestroy() {
        Log.d(TAG, "Client activity is being destroyed")

        if (isServiceBound) {
            clientService.removeClientServiceListener()
            unbindService(clientServiceConnection)
            isServiceBound = false
        }

        super.onDestroy()
    }

    private fun setupQueueList() {
        trackDisplayAdapter = BasicTrackListAdapter()
        queueList.adapter = trackDisplayAdapter
        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        queueList.layoutManager = layoutManager
        queueList.addItemDecoration(QueueItemDecoration(applicationContext))
    }

    override fun onQueueUpdated(queueTracks: List<PlaylistTrack>) {
        displayTrackList(queueTracks)
    }

    private fun displayTrackList(queueTracks: List<PlaylistTrack>) {
        when {
            queueTracks.size < 0 -> {
                Log.e(TAG, "Error invalid song request list sent")
            }
            queueTracks.isEmpty() -> {
                playbackControlView.shrinkLayout()
                playbackControlView.visibility = View.GONE
                trackDisplayAdapter.updateAdapter(mutableListOf())
            }
            queueTracks.size == 1 -> {
                playbackControlView.visibility = View.VISIBLE
                playbackControlView.displayTrack(queueTracks[0])
                trackDisplayAdapter.updateAdapter(mutableListOf())
            }
            queueTracks.size > 1 -> {
                playbackControlView.visibility = View.VISIBLE
                playbackControlView.displayTrack(queueTracks[0])
                trackDisplayAdapter.updateAdapter(queueTracks.subList(1, queueTracks.size))
            }
        }
    }

    override fun showHostDisconnectDialog() {
        val dialogBuilder = AlertDialog.Builder(this, R.style.AppDialog)
        dialogBuilder.setView(R.layout.host_disconnected_dialog)

        dialogBuilder.setPositiveButton(R.string.yes) { dialog, which ->
            Log.d(TAG, "User chose to follow the playlist")
            dialog.dismiss()

            // If yes, follow the Spotify playlist
            clientService.followPlaylist()
        }

        dialogBuilder.setNegativeButton(R.string.no) { dialog, which ->
            Log.d(TAG, "User chose not to follow the playlist")
            dialog.dismiss()
            closeClient()
        }

        dialogBuilder.setOnCancelListener {
            Log.d(TAG, "User did not complete follow playlist dialog")
            closeClient()
        }

        dialogBuilder.create().show()
    }

    override fun showLoadingScreen() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun initiateView(queueTitle: String, trackList: List<PlaylistTrack>) {
        displayTrackList(trackList)
        title = queueTitle
    }

    override fun closeClient() {
        clientService.removeClientServiceListener()
        stopClientService()
        finish()
    }

    override fun failedToConnect() {
        Toast.makeText(this, R.string.toast_failed_to_connect_to_host, Toast.LENGTH_LONG).show()
        closeClient()
    }
}