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
import com.chrisf.socialq.extensions.filterEmissions
import com.chrisf.socialq.model.ClientRequestData
import com.chrisf.socialq.services.HostService
import com.chrisf.socialq.userinterface.App
import com.chrisf.socialq.userinterface.adapters.HostTrackListAdapter
import com.chrisf.socialq.userinterface.views.PlaybackControlEvent.PlayPauseButtonTouched
import com.chrisf.socialq.userinterface.views.PlaybackControlEvent.SkipButtonTouched
import com.chrisf.socialq.userinterface.views.QueueItemDecoration
import com.jakewharton.rxbinding3.view.clicks
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_host_screen.*
import timber.log.Timber

open class HostActivity : ServiceActivity(), HostService.HostServiceListener {

    private lateinit var trackDisplayAdapter: HostTrackListAdapter

    private lateinit var hostService: HostService

    // Flag to determine if the service is bound or not
    private var isServiceBound = false
    // Name of the queue (or null)
    private val queueName: String? by lazy { intent.getStringExtra(QUEUE_NAME_KEY) }

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
        addTrackButton.clicks()
            .filterEmissions()
            .subscribe { startSearchActivity() }
            .addTo(subscriptions)

        playbackControlView.eventStream
            .filterEmissions()
            .subscribe { event ->
                when (event) {
                    PlayPauseButtonTouched -> hostService.requestPlayPauseToggle()
                    SkipButtonTouched -> hostService.requestPlayNext()
                }
            }
            .addTo(subscriptions)

        // Show queue title as activity title
        title = queueName ?: getString(R.string.queue_title_default_value)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Handle request result
        when (requestCode) {
            SEARCH_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    val trackUri = data?.getStringExtra(SearchActivity.SEARCH_RESULT_TRACK_EXTRA_KEY) ?: return
                    hostService.hostRequestSong(trackUri)
                }
            }
        }
    }

    private fun startHostService() {
        val startHostIntent = Intent(this, HostService::class.java)
        startHostIntent.putExtra(AppConstants.QUEUE_TITLE_KEY, queueName)
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
            .setTitle(R.string.close_host_dialog_title)
            .setMessage(R.string.close_host_dialog_description)
            .setPositiveButton(R.string.close_host_dialog_close_button) { dialog, _ ->
                hostService.unfollowQueuePlaylist()
                dialog.dismiss()
            }
            .setNeutralButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton(R.string.close_host_dialog_close_and_save_button) { dialog, _ ->
                hostService.savePlaylistAs(queueName)
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
        trackDisplayAdapter = HostTrackListAdapter()
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

    companion object {
        private const val QUEUE_NAME_KEY = "queue_name"
        private const val IS_FAIRPLAY_KEY = "is_fairplay"
        private const val BASE_PLAYLIST_ID = "base_playlist"

        fun start(
            fromActivity: Activity,
            queueName: String,
            isFairplay: Boolean = false,
            basePlaylistId: String = ""
        ) {
            val intent = Intent(fromActivity, HostActivity::class.java).apply {
                putExtra(QUEUE_NAME_KEY, queueName)
                putExtra(IS_FAIRPLAY_KEY, isFairplay)
                putExtra(BASE_PLAYLIST_ID, basePlaylistId)
            }
            fromActivity.startActivity(intent)
        }
    }
}