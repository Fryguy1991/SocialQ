package com.chrisfry.socialq.userinterface.fragments


import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.userinterface.adapters.IItemSelectionListener
import com.chrisfry.socialq.userinterface.adapters.SelectableBasePlaylistAdapter
import com.chrisfry.socialq.userinterface.views.QueueItemDecoration
import com.chrisfry.socialq.utils.DisplayUtils
import kaaes.spotify.webapi.android.SpotifyCallback
import kaaes.spotify.webapi.android.SpotifyError
import kaaes.spotify.webapi.android.SpotifyService
import kaaes.spotify.webapi.android.models.Pager
import kaaes.spotify.webapi.android.models.PlaylistSimple
import retrofit.client.Response
import java.util.HashMap

/**
 * A simple [Fragment] subclass.
 *
 */
class NewQueueFragment : BaseLaunchFragment(), IItemSelectionListener<String> {
    // UI ELEMENTS
    private lateinit var startQueueButton: View
    private lateinit var fairplayCheckBox: CheckBox
    private lateinit var basePlaylistCheckBox: CheckBox
    private lateinit var queueTitleEditText: EditText
    private lateinit var basePlaylistRecyclerView: RecyclerView
    private lateinit var fairplayInfoButton: View
    private lateinit var basePlaylistInfoButton: View
    // Adapter for displaying base playlist
    private val basePlaylistAdapter = SelectableBasePlaylistAdapter()
    // Builder for information dialogs
    private lateinit var dialogBuilder: AlertDialog.Builder

    // SPOTIFY ELEMENTS
    private val currentUserPlaylists = mutableListOf<PlaylistSimple>()
    private var basePlaylistId = ""

    init {
        basePlaylistAdapter.setListener(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestSpotifyUser()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        if (container != null) {
            return inflater.inflate(R.layout.fragment_new_queue, container, false)
        } else {
            return null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        startQueueButton = view.findViewById(R.id.btn_start_queue)
        fairplayCheckBox = view.findViewById(R.id.cb_fairplay_checkbox)
        basePlaylistCheckBox = view.findViewById(R.id.cb_base_playlist_checkbox)
        queueTitleEditText = view.findViewById(R.id.et_queue_name)
        fairplayInfoButton = view.findViewById(R.id.iv_fairplay_info)
        basePlaylistInfoButton = view.findViewById(R.id.iv_base_playlist_info)
        basePlaylistRecyclerView = view.findViewById(R.id.rv_base_playlists)

        // Setup recycler view
        val layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        basePlaylistRecyclerView.layoutManager = layoutManager
        basePlaylistRecyclerView.addItemDecoration(QueueItemDecoration(context))
        basePlaylistRecyclerView.adapter = basePlaylistAdapter

        // Setup builder for dialogs
        val alertContext = context
        if (alertContext != null) {
            dialogBuilder = AlertDialog.Builder(alertContext)
                    .setPositiveButton(R.string.ok) { dialog, which ->
                        dialog.dismiss()
                    }
        }

        startQueueButton.setOnClickListener {
            if (hasLocationPermission()) {
                startSocialQ()
            }
        }

        fairplayInfoButton.setOnClickListener {
            dialogBuilder.setView(R.layout.dialog_fair_play).create().show()
        }

        basePlaylistInfoButton.setOnClickListener {
            dialogBuilder.setView(R.layout.dialog_base_playlist).create().show()
        }

        basePlaylistCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                basePlaylistRecyclerView.visibility = View.VISIBLE
                DisplayUtils.hideSoftKeyboard(activity)
            } else {
                basePlaylistAdapter.clearSelection()
                basePlaylistRecyclerView.scrollToPosition(0)
                basePlaylistRecyclerView.visibility = View.GONE
            }
        }
    }

    override fun userRetrieved() {
        // Retrieve user playlists for spinner display
        val options = HashMap<String, Any>()
        options[SpotifyService.LIMIT] = AppConstants.PLAYLIST_LIMIT

        val user = currentUser
        if (user != null) {
            spotifyApi.service.getPlaylists(user.id, options, userPlaylistsCallback)
        }
    }

    private fun startSocialQ() {
        var title = queueTitleEditText.text.toString()
        if (title.isEmpty()) {
            title = getString(R.string.queue_title_default_value)
        }

        val isFairPlay = fairplayCheckBox.isChecked

        // Navigate to the host activity
        val hostDirections = NewQueueFragmentDirections.actionNewQueueFragmentToHostActivity(isFairPlay, basePlaylistId)
        hostDirections.queueTitle = title
        findNavController().navigate(hostDirections)
    }

    override fun locationPermissionReceived() {
        startSocialQ()
    }

    override fun locationPermissionRejected() {
        // Do nothing, just don't start the host if we don't have location permission
        Log.d(TAG, "Not starting host due to lack of location permission")
    }

    override fun onItemSelected(selectedItem: String) {
        basePlaylistId = selectedItem
    }


    // Callback for retrieving host user's playlists
    private val userPlaylistsCallback = object : SpotifyCallback<Pager<PlaylistSimple>>() {
        override fun success(playlistsPager: Pager<PlaylistSimple>?, response: Response?) {
            if (playlistsPager != null) {
                if (playlistsPager.total == 0) {
                    Log.d(TAG, "User has no playlists to show")
                    // TODO: Disable base playlist CB?
                    return
                }

                if (playlistsPager.offset == 0) {
                    // Fresh retrieval
                    currentUserPlaylists.clear()
                }

                // If playlists don't have tracks don't show them
                for (playlist in playlistsPager.items) {
                    if (playlist.tracks.total > 0) {
                        currentUserPlaylists.add(playlist)
                    }
                }

                val nextPlaylistIndex = playlistsPager.items.size + playlistsPager.offset
                // Check if there are more than 50 playlists by the user. If so we should get the next 50
                if (nextPlaylistIndex < playlistsPager.total) {
                    val options = HashMap<String, Any>()
                    options[SpotifyService.LIMIT] = AppConstants.PLAYLIST_LIMIT
                    options[SpotifyService.OFFSET] = nextPlaylistIndex

                    val user = currentUser
                    if (user != null) {
                        spotifyApi.service.getPlaylists(user.id, options, this)
                    }
                } else {
                    val adapterContext = context
                    if (adapterContext != null) {
                        basePlaylistAdapter.updateAdapter(currentUserPlaylists)
                    }
                }
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            Log.e(TAG, spotifyError?.errorDetails?.message.toString())
            Log.e(TAG, "Failed to retrieve user playlists")
        }
    }
}
