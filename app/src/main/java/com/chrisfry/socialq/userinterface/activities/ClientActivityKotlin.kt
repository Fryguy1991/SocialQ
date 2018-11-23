package com.chrisfry.socialq.userinterface.activities

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.RequestType
import com.chrisfry.socialq.userinterface.adapters.BasicTrackListAdapter
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationResponse

abstract class ClientActivityKotlin : BaseActivity() {
    private val TAG = ClientActivityKotlin::class.java.name

    // Elements for queue display
    private var mQueueList: RecyclerView? = null
    private var mTrackDisplayAdapter: BasicTrackListAdapter? = null

    // Spotify API elements
    protected var mHostUserId: String? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.client_screen)

        // Allow network operation in main thread
        val policy = StrictMode.ThreadPolicy.Builder()
                .permitAll().build()
        StrictMode.setThreadPolicy(policy)

        // Set scopes required for client
        accessScopes = arrayOf("user-read-private")

        Log.d(TAG, "Requesting access token from Spotify")
        requestNewAccessToken()

        initUi()
        setupQueueList()
    }

    private fun initUi() {
        // Initialize UI elements
        mQueueList = findViewById(R.id.rv_queue_list_view)

        // Show queue title as activity title
        title = intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY)
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
                    if (mHostUserId == null) {
                        Log.d(TAG, "First access token granted.  Connect to host")
                        connectToHost()
                    }
                }
            }
            RequestType.SEARCH_REQUEST -> if (resultCode == RESULT_OK) {
                val trackUri = data!!.getStringExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY)
                if (trackUri != null && !trackUri.isEmpty() && !mCurrentUser!!.id!!.isEmpty()) {
                    Log.d(TAG, "Client adding track to queue playlist")
                    sendTrackToHost(buildSongRequestMessage(trackUri, mCurrentUser!!.id))
                }
            }
            else -> Log.e(TAG, "Unhandled request code: $requestCode")
        }
    }

    override fun onBackPressed() {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle(R.string.close_client_dialog_title)

        val contentView = layoutInflater.inflate(R.layout.client_exit_dialog, null)
        val followCheckbox = contentView.findViewById<CheckBox>(R.id.cb_follow_playlist)

        dialogBuilder.setView(contentView)

        dialogBuilder.setPositiveButton(R.string.confirm) { dialog, which ->
            Log.d(TAG, "User chose to leave the queue")
            dialog.dismiss()

            // If follow is checked follow playlist with client user
            if (followCheckbox.isChecked) {
                Log.d(TAG, "User chose to follow the playlist")
                mSpotifyService.followPlaylist(mCurrentUser!!.id, mPlaylist!!.id)
            }
            disconnectClient()
            super.onBackPressed()
        }

        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which ->
            Log.d(TAG, "User chose to remain in the queue")
            dialog.dismiss()
        }

        dialogBuilder.create().show()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_screen_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.search_fragment -> {
                val searchIntent = Intent(this, SearchActivity::class.java)
                startActivityForResult(searchIntent, RequestType.SEARCH_REQUEST.requestCode)
                return true
            }
            else ->
                // Do nothing
                return false
        }
    }

    private fun setupQueueList() {
        mTrackDisplayAdapter = BasicTrackListAdapter()
        mQueueList!!.adapter = mTrackDisplayAdapter
        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        mQueueList!!.layoutManager = layoutManager
        mQueueList!!.addItemDecoration(QueueItemDecoration(applicationContext))
    }

    protected fun updateQueue(currentPlayingIndex: Int) {
        if (currentPlayingIndex >= 0) {
            refreshPlaylist()
            mTrackDisplayAdapter!!.updateAdapter(mPlaylistTracks.subList(currentPlayingIndex, mPlaylist!!.tracks.items.size))
        }
    }

    protected fun setupQueuePlaylistOnConnection(playlistId: String) {
        mPlaylist = mSpotifyService.getPlaylist(mHostUserId, playlistId)
    }

    protected fun showHostDisconnectedFollowPlaylistDialog() {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle(R.string.close_client_host_disconnect_dialog_title)
        dialogBuilder.setView(R.layout.host_disconnected_dialog)

        dialogBuilder.setPositiveButton(R.string.yes) { dialog, which ->
            Log.d(TAG, "User chose to follow the playlist")
            dialog.dismiss()

            // If yes, follow the Spotify playlist
            mSpotifyService.followPlaylist(mCurrentUser!!.id, mPlaylist!!.id)
        }

        dialogBuilder.setNegativeButton(R.string.no) { dialog, which ->
            Log.d(TAG, "User chose not to follow the playlist")
            dialog.dismiss()
            finish()
        }

        dialogBuilder.setOnCancelListener(DialogInterface.OnCancelListener {
            Log.d(TAG, "User did not complete follow playlist dialog")
            finish()
        })

        dialogBuilder.create().show()
    }

    protected abstract fun sendTrackToHost(requestMessage: String?)

    protected abstract fun connectToHost()

    protected abstract fun disconnectClient()

    private fun buildSongRequestMessage(trackUri: String?, userId: String?): String? {
        if (trackUri != null && userId != null && !trackUri.isEmpty() && !userId.isEmpty()) {
            return String.format(AppConstants.SONG_REQUEST_MESSAGE_FORMAT, trackUri, userId)
        }
        Log.d(TAG, "Can't build track request for URI: $trackUri, user ID: $userId")
        return null
    }
}