package com.chrisf.socialq.processor

import android.content.res.Resources
import androidx.lifecycle.Lifecycle
import com.chrisf.socialq.R
import com.chrisf.socialq.model.spotify.PlaylistSimple
import com.chrisf.socialq.network.SpotifyApi
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsAction
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsAction.*
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsState
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsState.DisplayBasePlaylists
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsState.NavigateToHostActivity
import com.chrisf.socialq.userinterface.common.AlertDialogBinding
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

class HostQueueOptionsProcessor @Inject constructor(
    private val resources: Resources,
    private val spotifyService: SpotifyApi,
    lifecycle: Lifecycle,
    subscriptions: CompositeDisposable
) : BaseProcessor<HostQueueOptionsState, HostQueueOptionsAction>(lifecycle, subscriptions) {

    private val loadedPlaylists = mutableListOf<PlaylistSimple>()
    private var morePlaylistsToLoad = true

    private var selectedPlaylistId = ""

    override fun handleAction(action: HostQueueOptionsAction) {
        when (action) {
            is BasePlaylistSelected -> selectedPlaylistId = action.playlistId
            is BasePlaylistInfoButtonTouched -> handleBasePlaylistInfoButtonTouched()
            is FairPlayInfoButtonTouched -> handleFairPlayInfoButtonTouched()
            is RequestMorePlaylists -> loadMorePlaylists()
            is StartQueueClick -> handleStartQueueClick(action)
            is ViewCreated -> loadUserPlaylists()
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
        val queueName = if (action.queueName.isBlank()) {
           resources.getString(R.string.queue_title_default_value)
        } else {
            action.queueName
        }
        stateStream.accept(NavigateToHostActivity(queueName, action.isFairPlay, selectedPlaylistId))
    }

    private fun handleBasePlaylistInfoButtonTouched() {
        stateStream.accept(
            HostQueueOptionsState.ShowBasePlaylistInfoDialog(
                AlertDialogBinding(
                    title = resources.getString(R.string.base_playlist),
                    message = resources.getString(R.string.base_playlist_description),
                    isCancelable = true,
                    positiveButtonText = resources.getString(R.string.ok)
                )
            )
        )
    }

    private fun handleFairPlayInfoButtonTouched() {
        stateStream.accept(
            HostQueueOptionsState.ShowFairPlayInfoDialog(
                AlertDialogBinding(
                    title = resources.getString(R.string.fair_play),
                    message = resources.getString(R.string.fair_play_description),
                    isCancelable = true,
                    positiveButtonText = resources.getString(R.string.ok)
                )
            )
        )
    }

    sealed class HostQueueOptionsState {
        data class DisplayBasePlaylists(val playlists: List<PlaylistSimple>) : HostQueueOptionsState()

        data class ShowBasePlaylistInfoDialog(
            val binding: AlertDialogBinding<HostQueueOptionsAction>
        ) : HostQueueOptionsState()

        data class ShowFairPlayInfoDialog(
            val binding: AlertDialogBinding<HostQueueOptionsAction>
        ) : HostQueueOptionsState()

        data class NavigateToHostActivity(
            val queueTitle: String,
            val isFairPlay: Boolean,
            val basePlaylistId: String
        ) : HostQueueOptionsState()
    }

    sealed class HostQueueOptionsAction {
        data class BasePlaylistSelected(val playlistId: String) : HostQueueOptionsAction()

        object BasePlaylistInfoButtonTouched : HostQueueOptionsAction()

        object FairPlayInfoButtonTouched : HostQueueOptionsAction()

        object RequestMorePlaylists : HostQueueOptionsAction()

        data class StartQueueClick(
            val queueName: String,
            val isFairPlay: Boolean
        ) : HostQueueOptionsAction()

        object ViewCreated : HostQueueOptionsAction()
    }
}