package com.chrisf.socialq.processor

import android.content.res.Resources
import androidx.lifecycle.Lifecycle
import com.chrisf.socialq.R
import com.chrisf.socialq.extensions.addTo
import com.chrisf.socialq.model.spotify.PlaylistSimple
import com.chrisf.socialq.network.FrySpotifyService
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsAction
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsAction.*
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsState
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsState.DisplayBasePlaylists
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsState.NavigateToHostActivity
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

class HostQueueOptionsProcessor @Inject constructor(
        private val resources: Resources,
        private val spotifyService: FrySpotifyService,
        lifecycle: Lifecycle,
        subscriptions: CompositeDisposable
) : BaseProcessor<HostQueueOptionsState, HostQueueOptionsAction>(lifecycle, subscriptions) {

    private val loadedPlaylists = mutableListOf<PlaylistSimple>()
    private var morePlaylistsToLoad = true

    private var selectedPlaylistId = ""

    override fun handleAction(action: HostQueueOptionsAction) {
        when (action) {
            ViewCreated -> loadUserPlaylists()
            RequestMorePlaylists -> loadMorePlaylists()
            is BasePlaylistSelected -> selectedPlaylistId = action.playlistId
            is StartQueueClick -> handleStartQueueClick(action)
        }
    }

    private fun loadUserPlaylists(offset: Int = 0) {
        spotifyService.getCurrentUsersPlaylists(offset = offset)
                .subscribeOn(Schedulers.io())
                .subscribe({
                    if (it.isSuccessful && it.body() != null) {
                        loadedPlaylists.addAll(it.body()!!.items)
                        stateStream.accept(DisplayBasePlaylists(loadedPlaylists.toList()))

                        morePlaylistsToLoad = it.body()!!.next != null
                    } else {
                        Timber.e("Failed to retrieve user playlists")
                    }
                }, {
                    Timber.e(it)
                })
                .addTo(subscriptions)
    }

    private fun loadMorePlaylists() {
        if (morePlaylistsToLoad) {
            loadUserPlaylists(loadedPlaylists.size)
        }
    }

    private fun handleStartQueueClick(action: StartQueueClick) {
        val queueName = action.queueName ?: resources.getString(R.string.queue_title_default_value)
        stateStream.accept(NavigateToHostActivity(queueName, action.isFairPlay, selectedPlaylistId))
    }

    sealed class HostQueueOptionsState {
        data class DisplayBasePlaylists(val playlists: List<PlaylistSimple>) : HostQueueOptionsState()
        data class NavigateToHostActivity(
                val queueTitle: String,
                val isFairPlay: Boolean,
                val basePlaylistId: String
        ) : HostQueueOptionsState()
    }

    sealed class HostQueueOptionsAction {
        object ViewCreated : HostQueueOptionsAction()
        object RequestMorePlaylists : HostQueueOptionsAction()
        data class StartQueueClick(
                val queueName: String?,
                val isFairPlay: Boolean
        ) : HostQueueOptionsAction()

        data class BasePlaylistSelected(val playlistId: String) : HostQueueOptionsAction()
    }
}