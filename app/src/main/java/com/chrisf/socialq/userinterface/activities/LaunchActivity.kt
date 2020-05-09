package com.chrisf.socialq.userinterface.activities

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.ActivityComponent
import com.chrisf.socialq.extensions.filterEmissions
import com.chrisf.socialq.processor.LaunchProcessor
import com.chrisf.socialq.processor.LaunchProcessor.LaunchAction
import com.chrisf.socialq.processor.LaunchProcessor.LaunchAction.*
import com.chrisf.socialq.processor.LaunchProcessor.LaunchState
import com.chrisf.socialq.processor.LaunchProcessor.LaunchState.*
import com.chrisf.socialq.services.AccessService
import com.chrisf.socialq.userinterface.adapters.QueueDisplayAdapter
import com.chrisf.socialq.userinterface.views.QueueItemDecoration
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Strategy
import com.jakewharton.rxbinding3.view.clicks
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_launch.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class LaunchActivity : BaseActivity<LaunchState, LaunchAction, LaunchProcessor>() {
    override val FRAGMENT_HOLDER_ID: Int = View.NO_ID

    private val adapter = QueueDisplayAdapter()

    private val scheduler: JobScheduler by lazy { getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler }

    @Inject
    lateinit var rxPermissions: RxPermissions

    private var isUserPremium = false

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, discoveredEndpointInfo: DiscoveredEndpointInfo) {
            Timber.d("Endpoint Found")

            actionStream.accept(EndpointFound(endpointId, discoveredEndpointInfo))
        }

        override fun onEndpointLost(endpointId: String) {
            Timber.d("Endpoint Lost")
            actionStream.accept(EndpointLost(endpointId))
        }
    }

    override fun resolveDependencies(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)
        setSupportActionBar(toolbar)

        setupViews()
        actionStream.accept(ViewCreated)
    }

    override fun onResume() {
        super.onResume()

        if (rxPermissions.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            actionStream.accept(QueueRefreshRequested)
        } else {
            showQueueRefreshFailed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            SPOTIFY_AUTH_REFRESH_CODE -> {
                val response = AuthenticationClient.getResponse(resultCode, data)
                if (response.type == AuthenticationResponse.Type.CODE) {
                    Timber.d("Authorization code granted")

                    actionStream.accept(AuthCodeRetrieved(response.code))
                    return
                } else {
                    actionStream.accept(AuthCodeRetrievalFailed)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Nearby.getConnectionsClient(this).stopDiscovery()
    }

    override fun handleState(state: LaunchState) {
        when (state) {
            is CloseApp -> finishAndRemoveTask()
            is DisplayAvailableQueues -> displayQueues(state)
            is EnableNewQueueButton -> enableNewQueueButton(state)
            is LaunchClientActivity -> launchClientActivity(state)
            is NavigateToNewQueue -> navigateToNewQueue()
            is NoQueuesFound -> onNoQueuesFound()
            is RequestAuthorization -> requestAuthorization()
            is SearchForQueues -> searchForQueues()
            is ShowAuthFailedDialog -> showAlertDialog(state.binding)
            is ShowLocationPermissionRequiredDialog -> showAlertDialog(state.binding)
            is ShowPremiumRequiredDialog -> showAlertDialog(state.binding)
            is ShowQueueRefreshFailed -> showQueueRefreshFailed()
            is StartAuthRefreshJob -> startPeriodicAccessRefresh()
            is StopSearchingForQueues -> stopSearchingForQueues()
        }
    }

    private fun displayQueues(state: DisplayAvailableQueues) {
        // Hide Views
        swipeRefreshText.visibility = View.GONE

        // Show Views
        adapter.updateAdapter(state.queueList)
        availableQueueRecyclerView.visibility = View.VISIBLE
    }

    private fun enableNewQueueButton(state: EnableNewQueueButton) {
        isUserPremium = state.isUserPremium
        newQueueButton.show()
    }

    private fun launchClientActivity(state: LaunchClientActivity) {
        ClientActivity.start(this, state.hostEndpoint, state.queueTitle)
    }

    private fun navigateToNewQueue() {
        startActivity(Intent(this, HostQueueOptionsActivity::class.java))
    }

    private fun onNoQueuesFound() {
        // Hide Views
        adapter.updateAdapter(emptyList())
        availableQueueRecyclerView.visibility = View.GONE
        hostSwipeRefreshLayout.isRefreshing = false

        // Show Views
        swipeRefreshText.text = getString(R.string.no_host_found_message)
        swipeRefreshText.visibility = View.VISIBLE
    }

    private fun requestAuthorization() {
        val accessScopes = arrayOf("user-read-private", "streaming", "playlist-modify-private", "playlist-modify-public", "playlist-read-private")
        val builder = AuthenticationRequest.Builder(
            AppConstants.CLIENT_ID,
            AuthenticationResponse.Type.CODE,
            AppConstants.REDIRECT_URI)
        builder.setScopes(accessScopes)
        val request = builder.build()

        AuthenticationClient.openLoginActivity(
            this,
            SPOTIFY_AUTH_REFRESH_CODE,
            request
        )
    }

    private fun searchForQueues() {
        hostSwipeRefreshLayout.isRefreshing = true
        swipeRefreshText.text = getString(R.string.queue_searching_message)
        swipeRefreshText.visibility = View.VISIBLE
        availableQueueRecyclerView.visibility = View.GONE

        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()

        Nearby.getConnectionsClient(this)
            .startDiscovery(
                AppConstants.SERVICE_NAME,
                endpointDiscoveryCallback,
                options
            )
            .addOnSuccessListener {
                actionStream.accept(StartedNearbySearch(true))
            }
            .addOnFailureListener {
                actionStream.accept(StartedNearbySearch(false))
            }
    }

    private fun showQueueRefreshFailed() {
        // Hide Views
        adapter.updateAdapter(emptyList())
        availableQueueRecyclerView.visibility = View.GONE
        hostSwipeRefreshLayout.isRefreshing = false

        // Show Views
        swipeRefreshText.text = getString(R.string.host_search_failed_message)
        swipeRefreshText.visibility = View.VISIBLE
    }

    private fun startPeriodicAccessRefresh() {
        scheduler.schedule(
            JobInfo.Builder(
                AppConstants.ACCESS_SERVICE_ID,
                ComponentName(this, AccessService::class.java)
            ).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(TimeUnit.MINUTES.toMillis(20))
                .build()
        )
    }

    private fun stopSearchingForQueues() {
        hostSwipeRefreshLayout.isRefreshing = false
        Nearby.getConnectionsClient(this).stopDiscovery()
    }

    private fun setupViews() {
        hostSwipeRefreshLayout.setOnRefreshListener {
            rxPermissions.request(Manifest.permission.ACCESS_COARSE_LOCATION)
                .take(1)
                .singleOrError()
                .onErrorReturn { false }
                .map { hasPermission ->
                    if (hasPermission) QueueRefreshRequested else LocationPermissionDenied
                }
                .subscribe(actionStream)
                .addTo(subscriptions)
        }
        hostSwipeRefreshLayout.setColorSchemeResources(R.color.BurntOrangeLight2)

        Observable.merge(
            adapter.queueSelection.map { QueueSelected(it) },
            newQueueButton.clicks().map { StartNewQueueButtonTouched(isUserPremium) }
        ).filterEmissions()
            .subscribe(actionStream)
            .addTo(subscriptions)

        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        availableQueueRecyclerView.layoutManager = layoutManager
        availableQueueRecyclerView.addItemDecoration(QueueItemDecoration(this))
        availableQueueRecyclerView.adapter = adapter
    }

    companion object {
        private const val SPOTIFY_AUTH_REFRESH_CODE = 100
    }
}