package com.chrisf.socialq.userinterface.activities

import android.app.Activity
import android.content.*
import android.os.*
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.R
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.enums.RequestType
import com.chrisf.socialq.model.ClientRequestData
import com.chrisf.socialq.services.HostService
import com.chrisf.socialq.userinterface.App
import com.chrisf.socialq.userinterface.adapters.HostTrackListAdapter
import com.chrisf.socialq.userinterface.views.PlaybackControlView
import com.chrisf.socialq.userinterface.views.QueueItemDecoration
import kotlinx.android.synthetic.main.activity_host_screen.*
import org.jetbrains.anko.startActivity
import timber.log.Timber

open class HostActivity : ServiceActivity(), HostService.HostServiceListener, PlaybackControlView.IPlaybackControlListener {

    private lateinit var trackDisplayAdapter: HostTrackListAdapter

    private lateinit var hostService: HostService
    // Flag to determine if the service is bound or not
    private var isServiceBound = false

    // Object for connecting to/from play queue service
    private val hostServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            Timber.d("Host service Connected")
            val binder = iBinder as HostService.HostServiceBinder
            hostService = binder.getService()
            isServiceBound = true

            hostService.setPlayQueueServiceListener(this@HostActivity)

            // TODO: Might need this if activity is lost or recreated
//            if (title.isNullOrEmpty()) {
//                hostService.requestInitiation()
//            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            Timber.d("Host service disconnected")
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
        setContentView(R.layout.activity_host_screen)

        // Setup the app toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initUi()
        setupQueueList()
        startHostService()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initUi() {
        // Initialize UI
        addTrackButton.setOnClickListener(this)
        playbackControlView.setListener(this)
        playbackControlView.setOnClickListener(this)

        // Show queue title as activity title
        var queueName = intent.getStringExtra(QUEUE_NAME_KEY)
        if (queueName.isNullOrBlank()) queueName = getString(R.string.queue_title_default_value)
        title = queueName
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val requestType = RequestType.getRequestTypeFromRequestCode(requestCode)
        Timber.d("Received request type: $requestType")

        // Handle request result
        when (requestType) {
            RequestType.SEARCH_REQUEST -> if (resultCode == RESULT_OK) {
                val trackUri = data!!.getStringExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY)
                hostService.hostRequestSong(trackUri)
            }
            RequestType.SPOTIFY_AUTHENTICATION_REQUEST,
            RequestType.LOCATION_PERMISSION_REQUEST -> {
                Timber.e("Host activity should not receive $requestType")
            }
            RequestType.NONE -> {
                Timber.e("Unhandled request code: $requestCode")
            }
        }
    }

    private fun startHostService() {
        val startHostIntent = Intent(this, HostService::class.java)
        startHostIntent.putExtra(AppConstants.QUEUE_TITLE_KEY, intent.getStringExtra(QUEUE_NAME_KEY))
        startHostIntent.putExtra(AppConstants.FAIR_PLAY_KEY, intent.getBooleanExtra(IS_FAIRPLAY_KEY, resources.getBoolean(R.bool.fair_play_default)))
        startHostIntent.putExtra(AppConstants.BASE_PLAYLIST_ID_KEY, intent.getStringExtra(BASE_PLAYLIST_ID))

        // If we can't find a host service to bind to, start the host service then bind
        if (App.hasServiceBeenStarted) {
            Timber.d("Attempting to bind to host service")
            bindService(startHostIntent, hostServiceConnection, Context.BIND_ABOVE_CLIENT)
        } else {
            Timber.d("Starting and binding to host service")
            ContextCompat.startForegroundService(this, startHostIntent)
            bindService(startHostIntent, hostServiceConnection, Context.BIND_ABOVE_CLIENT)
        }
    }

    private fun stopHostService() {
        Timber.d("Unbinding from and stopping host service")

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
                Timber.d("Updating SocialQ playlist details")

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
        Timber.d("Destroying host activity")

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
        queueListRecyclerView.adapter = trackDisplayAdapter
        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        queueListRecyclerView.layoutManager = layoutManager
        queueListRecyclerView.addItemDecoration(QueueItemDecoration(applicationContext))
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
        loadingRoot.visibility = View.GONE
        queueListRecyclerView.visibility = View.VISIBLE

        when {
            songRequests.isEmpty() -> {
                playbackControlView.shrinkLayout()
                playbackControlView.visibility = View.GONE
                trackDisplayAdapter.updateAdapter(mutableListOf())
                emptyQueueText.visibility = View.VISIBLE
            }
            songRequests.size == 1 -> {
                playbackControlView.visibility = View.VISIBLE
                playbackControlView.displayRequest(songRequests[0])
                trackDisplayAdapter.updateAdapter(mutableListOf())
                emptyQueueText.visibility = View.GONE
            }
            songRequests.size > 1 -> {
                playbackControlView.visibility = View.VISIBLE
                playbackControlView.displayRequest(songRequests[0])
                trackDisplayAdapter.updateAdapter(songRequests.subList(1, songRequests.size))
                emptyQueueText.visibility = View.GONE
            }
        }
    }

    override fun closeHost() {
        stopHostService()
        finish()
    }

    override fun showClientConnected() {
        Toast.makeText(this, getString(R.string.client_joined), Toast.LENGTH_LONG).show()
    }

    override fun showClientDisconnected() {
        Toast.makeText(this, getString(R.string.client_disconnected), Toast.LENGTH_LONG).show()
    }

    override fun showLoadingScreen() {
        loadingRoot.visibility = View.VISIBLE

        emptyQueueText.visibility = View.GONE
        queueListRecyclerView.visibility = View.GONE
        playbackControlView.visibility = View.GONE
    }

    override fun initiateView(title: String, songRequests: List<ClientRequestData>, isPlaying: Boolean) {
        Timber.d("Re-initializing host view")

        this.title = title
        displayTrackList(songRequests)
        if (isPlaying) {
            onQueuePlay()
        } else {
            onQueuePause()
        }
    }

    override fun requestPlayPause() {
        hostService.requestPlayPauseToggle()
    }

    override fun requestSkip() {
        hostService.requestPlayNext()
    }

    companion object {
        private const val QUEUE_NAME_KEY = "queue_name"
        private const val IS_FAIRPLAY_KEY = "is_fairplay"
        private const val BASE_PLAYLIST_ID = "base_playlist"

        fun start(
                fromActivity: Activity,
                queueName: String = "",
                isFairplay: Boolean = false,
                basePlaylistId: String = ""
        ) {
            fromActivity.startActivity<HostActivity>(
                    QUEUE_NAME_KEY to queueName,
                    IS_FAIRPLAY_KEY to isFairplay,
                    BASE_PLAYLIST_ID to basePlaylistId
            )
        }
    }
}