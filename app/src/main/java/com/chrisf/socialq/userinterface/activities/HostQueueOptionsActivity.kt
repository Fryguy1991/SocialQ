package com.chrisf.socialq.userinterface.activities

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.ActivityComponent
import com.chrisf.socialq.extensions.filterEmissions
import com.chrisf.socialq.processor.HostQueueOptionsProcessor
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsAction
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsAction.*
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsState
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsState.*
import com.chrisf.socialq.userinterface.adapters.PlaylistAdapter
import com.chrisf.socialq.userinterface.views.QueueItemDecoration
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.checkedChanges
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_new_queue.*

class HostQueueOptionsActivity : BaseActivity<HostQueueOptionsState, HostQueueOptionsAction, HostQueueOptionsProcessor>() {
    override val FRAGMENT_HOLDER_ID = View.NO_ID

    private lateinit var basePlaylistAdapter: PlaylistAdapter

    override fun resolveDependencies(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    override fun handleState(state: HostQueueOptionsState) {
        when (state) {
            is DisplayBasePlaylists -> updatePlaylistDisplay(state)
            is ShowBasePlaylistInfoDialog -> showAlertDialog(state.binding)
            is ShowFairPlayInfoDialog -> showAlertDialog(state.binding)
            is NavigateToHostActivity -> navigateToHostActivity(state)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_new_queue)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupViews()
        actionStream.accept(ViewCreated)
    }

    private fun setupViews() {
        // Setup recycler view
        basePlaylistAdapter = PlaylistAdapter(resources)
        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        basePlaylistRecyclerView.layoutManager = layoutManager
        basePlaylistRecyclerView.addItemDecoration(QueueItemDecoration(this))
        basePlaylistRecyclerView.adapter = basePlaylistAdapter

        basePlaylistAdapter.playlistSelections
            .map { BasePlaylistSelected(it) }
            .subscribe(actionStream)
            .addTo(subscriptions)

        basePlaylistCheckbox.checkedChanges()
            .skipInitialValue()
            .subscribe { isChecked ->
                basePlaylistRecyclerView.visibility = if (isChecked) View.VISIBLE else View.GONE

                if (isChecked == false) {
                    actionStream.accept(BasePlaylistSelected(""))
                    basePlaylistAdapter.deselectPlaylist()
                } else {
                    hideKeyboard()
                }
            }
            .addTo(subscriptions)

        Observable.mergeArray(
            startQueueButton.clicks().map {
                StartQueueClick(
                    queueName = queueNameField.text.toString(),
                    isFairPlay = fairplayCheckbox.isChecked
                )
            },
            fairplayInfoIcon.clicks().map { FairPlayInfoButtonTouched },
            basePlaylistInfoIcon.clicks().map { BasePlaylistInfoButtonTouched }
        ).filterEmissions()
            .subscribe (actionStream)
            .addTo(subscriptions)
    }

    private fun updatePlaylistDisplay(state: DisplayBasePlaylists) {
        basePlaylistAdapter.updateAdapter(state.playlists)
    }

    private fun navigateToHostActivity(state: NavigateToHostActivity) {
        HostActivity.start(this, state.queueTitle, state.isFairPlay, state.basePlaylistId)
        finish()
    }
}