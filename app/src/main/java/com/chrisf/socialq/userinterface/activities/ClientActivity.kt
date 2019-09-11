package com.chrisf.socialq.userinterface.activities

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.R
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.enums.RequestType
import com.chrisf.socialq.model.spotify.PlaylistTrack
import com.chrisf.socialq.services.ClientService
import com.chrisf.socialq.userinterface.App
import com.chrisf.socialq.userinterface.adapters.BasicTrackListAdapter
import com.chrisf.socialq.userinterface.views.QueueItemDecoration
import kotlinx.android.synthetic.main.activity_client_screen.*
import org.jetbrains.anko.startActivity
import timber.log.Timber

open class ClientActivity : ServiceActivity(), ClientService.ClientServiceListener {

    // UI ELEMENTS
    // Queue display elements
    private lateinit var trackDisplayAdapter: BasicTrackListAdapter

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
        setContentView(R.layout.activity_client_screen)

        // Setup the app toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Get queue title from intent
        val titleString = intent?.getStringExtra(AppConstants.QUEUE_TITLE_KEY)
        hostQueueTitle = if (titleString.isNullOrBlank()) {
            getString(R.string.queue_title_default_value)
        } else {
            titleString
        }

        // Ensure we receive an endpoint before trying to start service
        val endpointString = intent?.getStringExtra(AppConstants.ND_ENDPOINT_ID_EXTRA_KEY)
        if (!App.hasServiceBeenStarted && endpointString.isNullOrBlank()) {
            Timber.e("Error, trying to start client with invalid endpointId")
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // Object for connecting to/from client service
    private val clientServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            Timber.d("Client service Connected")
            val binder = iBinder as ClientService.ClientServiceBinder

            clientService = binder.getService()
            clientService.setClientServiceListener(this@ClientActivity)
            isServiceBound = true

            if (hostEnpointId.isEmpty()) {
                // TODO: We probably still need this service call
//                clientService.requestInitiation()
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            Timber.d("Client service disconnected")
            unbindService(this)
            isServiceBound = false
            finish()
        }
    }

    private fun initUi() {
        playbackControlView.hideControls()
        playbackControlView.hideUser()

        addTrackButton.setOnClickListener(this)

        // Show queue title as activity title
        title = hostQueueTitle
    }

    private fun startClientService() {
        val startClientIntent = Intent(this, ClientService::class.java)
        startClientIntent.putExtra(AppConstants.ND_ENDPOINT_ID_EXTRA_KEY, hostEnpointId)
        startClientIntent.putExtra(AppConstants.QUEUE_TITLE_KEY, hostQueueTitle)

        // If we can't find a client service to bind to, start the client service then bind
        if (App.hasServiceBeenStarted) {
            Timber.d("Attempting to bind to client service")
            bindService(startClientIntent, clientServiceConnection, Context.BIND_AUTO_CREATE)
        } else {
            Timber.d("Starting and binding to client service")
            ContextCompat.startForegroundService(this, startClientIntent)
            bindService(startClientIntent, clientServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun stopClientService() {
        Timber.d("Unbinding from and stopping client service")

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
        Timber.d("Received request type: $requestType")

        // Handle request result
        when (requestType) {
            RequestType.SEARCH_REQUEST -> if (resultCode == RESULT_OK) {
                val trackUri = data!!.getStringExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY)
                if (trackUri != null && !trackUri.isEmpty()) {
                    Timber.d("Client adding track to queue playlist")
                    clientService.sendTrackToHost(trackUri)
                }
            }
            RequestType.SPOTIFY_AUTHENTICATION_REQUEST,
            RequestType.LOCATION_PERMISSION_REQUEST -> {
                Timber.e("Client activity should not receive $requestType")
            }
            RequestType.NONE -> {
                Timber.e("Unhandled request code: $requestCode")
            }
        }
    }

    override fun onBackPressed() {
        val dialogBuilder = AlertDialog.Builder(this, R.style.AppDialog)

        val contentView = layoutInflater.inflate(R.layout.client_exit_dialog, null)
        val followCheckbox = contentView.findViewById<CheckBox>(R.id.cb_follow_playlist)

        dialogBuilder.setView(contentView)

        dialogBuilder.setPositiveButton(R.string.confirm) { dialog, which ->
            Timber.d("User chose to leave the queue")
            dialog.dismiss()

            // If follow is checked follow playlist with client user
            if (followCheckbox.isChecked) {
                Timber.d("User chose to follow the playlist")
                clientService.followPlaylist()
            } else {
                clientService.requestDisconnect()
            }
        }

        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which ->
            Timber.d("User chose to remain in the queue")
            dialog.dismiss()
        }

        dialogBuilder.create().show()
    }

    override fun onDestroy() {
        Timber.d("Client activity is being destroyed")

        if (isServiceBound) {
            clientService.removeClientServiceListener()
            unbindService(clientServiceConnection)
            isServiceBound = false
        }

        super.onDestroy()
    }

    private fun setupQueueList() {
        trackDisplayAdapter = BasicTrackListAdapter()
        queueListRecyclerView.adapter = trackDisplayAdapter
        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        queueListRecyclerView.layoutManager = layoutManager
        queueListRecyclerView.addItemDecoration(QueueItemDecoration(applicationContext))
    }

    override fun onQueueUpdated(queueTracks: List<PlaylistTrack>) {
        displayTrackList(queueTracks)
    }

    private fun displayTrackList(queueTracks: List<PlaylistTrack>) {
        loadingRoot.visibility = View.GONE
        queueListRecyclerView.visibility = View.VISIBLE

        when {
            queueTracks.isEmpty() -> {
                playbackControlView.shrinkLayout()
                playbackControlView.visibility = View.GONE
                trackDisplayAdapter.updateAdapter(mutableListOf())

                emptyQueueText.visibility = View.VISIBLE
            }
            queueTracks.size == 1 -> {
                playbackControlView.visibility = View.VISIBLE
                playbackControlView.displayTrack(queueTracks[0])
                trackDisplayAdapter.updateAdapter(mutableListOf())

                emptyQueueText.visibility = View.GONE
            }
            queueTracks.size > 1 -> {
                playbackControlView.visibility = View.VISIBLE
                playbackControlView.displayTrack(queueTracks[0])
                trackDisplayAdapter.updateAdapter(queueTracks.subList(1, queueTracks.size))

                emptyQueueText.visibility = View.GONE
            }
        }
    }

    override fun showHostDisconnectDialog() {
        val dialogBuilder = AlertDialog.Builder(this, R.style.AppDialog)
        dialogBuilder.setView(R.layout.host_disconnected_dialog)

        dialogBuilder.setPositiveButton(R.string.yes) { dialog, which ->
            Timber.d("User chose to follow the playlist")
            dialog.dismiss()

            // If yes, follow the Spotify playlist
            clientService.followPlaylist()
        }

        dialogBuilder.setNegativeButton(R.string.no) { dialog, which ->
            Timber.d("User chose not to follow the playlist")
            dialog.dismiss()
            closeClient()
        }

        dialogBuilder.setOnCancelListener {
            Timber.d("User did not complete follow playlist dialog")
            closeClient()
        }

        dialogBuilder.create().show()
    }

    override fun showLoadingScreen() {
        loadingRoot.visibility = View.VISIBLE

        emptyQueueText.visibility = View.GONE
        queueListRecyclerView.visibility = View.GONE
        playbackControlView.visibility = View.GONE
    }

    override fun initiateView(queueTitle: String) {
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

    companion object {
        fun start(
                fromActivity: Activity,
                hostEndpointId: String,
                hostQueueName: String = ""
        ) {
            fromActivity.startActivity<ClientActivity>(
                    AppConstants.QUEUE_TITLE_KEY to hostQueueName,
                    AppConstants.ND_ENDPOINT_ID_EXTRA_KEY to hostEndpointId
            )
        }
    }
}