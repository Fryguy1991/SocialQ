package com.chrisf.socialq.userinterface.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.ActivityComponent
import com.chrisf.socialq.extensions.addTo
import com.chrisf.socialq.processor.HostQueueOptionsProcessor
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsAction
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsAction.*
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsState
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsState.DisplayBasePlaylists
import com.chrisf.socialq.processor.HostQueueOptionsProcessor.HostQueueOptionsState.NavigateToHostActivity
import com.chrisf.socialq.userinterface.activities.HostQueueOptionsActivity.InfoClick.BasePlaylistInfoClick
import com.chrisf.socialq.userinterface.activities.HostQueueOptionsActivity.InfoClick.FairplayInfoClick
import com.chrisf.socialq.userinterface.adapters.PlaylistAdapter
import com.chrisf.socialq.userinterface.views.QueueItemDecoration
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.checkedChanges
import kotlinx.android.synthetic.main.activity_new_queue.*
import java.util.concurrent.TimeUnit

class HostQueueOptionsActivity : BaseActivity<HostQueueOptionsState, HostQueueOptionsAction, HostQueueOptionsProcessor>() {
    override val FRAGMENT_HOLDER_ID = View.NO_ID

    private lateinit var basePlaylistAdapter: PlaylistAdapter

    private var alertDialog: AlertDialog? = null

    override fun resolveDependencies(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    override fun handleState(state: HostQueueOptionsState) {
        when (state) {
            is DisplayBasePlaylists -> updatePlaylistDisplay(state)
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
                .subscribe { actionStream.accept(BasePlaylistSelected(it)) }
                .addTo(subscriptions)

        basePlaylistCheckbox.checkedChanges()
                .skipInitialValue()
                .subscribe { isChecked ->
                    basePlaylistRecyclerView.visibility = if (isChecked) View.VISIBLE else View.GONE

                    if (isChecked == false) {
                        actionStream.accept(BasePlaylistSelected(""))
                        basePlaylistAdapter.deselectPlaylist()
                    }
                }
                .addTo(subscriptions)

        startQueueButton.clicks()
                .throttleFirst(300, TimeUnit.MILLISECONDS)
                .subscribe {
                    actionStream.accept(
                            StartQueueClick(
                                    queueNameField.text.toString(),
                                    fairplayCheckbox.isChecked
                            )
                    )
                }
                .addTo(subscriptions)

        fairplayInfoIcon.clicks().map { FairplayInfoClick as InfoClick }
                .mergeWith(basePlaylistInfoIcon.clicks().map { BasePlaylistInfoClick })
                .throttleFirst(300, TimeUnit.MILLISECONDS)
                .subscribe {
                    when (it) {
                        FairplayInfoClick -> showFairplayInfoDialog()
                        BasePlaylistInfoClick -> showBasePlaylistInfoDialog()
                    }
                }
                .addTo(subscriptions)
    }

    private fun updatePlaylistDisplay(state: DisplayBasePlaylists) {
        basePlaylistAdapter.updateAdapter(state.playlists)
    }

    private fun navigateToHostActivity(state: NavigateToHostActivity) {
        HostActivity.start(this, state.queueTitle, state.isFairPlay, state.basePlaylistId)
        finish()
    }

    private fun showFairplayInfoDialog() {
        if (alertDialog == null || alertDialog?.isShowing == false) {
            alertDialog = AlertDialog.Builder(this)
                    .setView(R.layout.dialog_fair_play)
                    .setPositiveButton(R.string.ok) { dialog, which ->
                        dialog.dismiss()
                    }
                    .create()
            alertDialog!!.show()
        }
    }

    private fun showBasePlaylistInfoDialog() {
        if (alertDialog == null || alertDialog?.isShowing == false) {
            alertDialog = AlertDialog.Builder(this)
                    .setView(R.layout.dialog_base_playlist)
                    .setPositiveButton(R.string.ok) { dialog, which ->
                        dialog.dismiss()
                    }
                    .create()
            alertDialog!!.show()
        }
    }

    sealed class InfoClick {
        object FairplayInfoClick : InfoClick()
        object BasePlaylistInfoClick : InfoClick()
    }
}