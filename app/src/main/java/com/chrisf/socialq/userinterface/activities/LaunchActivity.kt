package com.chrisf.socialq.userinterface.activities

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import com.chrisf.socialq.processor.SocialQEndpoint
import com.chrisf.socialq.services.AccessService
import com.chrisf.socialq.userinterface.adapters.QueueDisplayAdapter
import com.chrisf.socialq.userinterface.views.QueueItemDecoration
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.jakewharton.rxbinding3.view.clicks
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_launch.*
import timber.log.Timber
import java.lang.IllegalStateException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class LaunchActivity : BaseActivity<LaunchState, LaunchAction, LaunchProcessor>() {
    override val FRAGMENT_HOLDER_ID: Int = View.NO_ID

    private val adapter = QueueDisplayAdapter()

    private val socialQEndpoints: MutableList<SocialQEndpoint> = mutableListOf()

    private val connectionsClient: ConnectionsClient by lazy { Nearby.getConnectionsClient(this) }
    private var isDiscovering = false
    private var isAskingLocationPermission = false

    @Inject
    lateinit var rxPermissions: RxPermissions

    private var isUserPremium = false

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, discoveredEndpointInfo: DiscoveredEndpointInfo) {
            Timber.d("Endpoint Found")

            socialQEndpoints.add(
                SocialQEndpoint(
                    endpointId = endpointId,
                    endpointInfo = discoveredEndpointInfo
                )
            )
            actionStream.accept(EndpointListUpdated(socialQEndpoints))
        }

        override fun onEndpointLost(endpointId: String) {
            Timber.d("Endpoint Lost")

            val endpointsToRemove = socialQEndpoints.filter { it.endpointId == endpointId }
            socialQEndpoints.removeAll(endpointsToRemove)

            actionStream.accept(EndpointListUpdated(socialQEndpoints))
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
        startAuthRefresh()
        actionStream.accept(ViewCreated)

        isAskingLocationPermission = true
        rxPermissions.request(Manifest.permission.ACCESS_COARSE_LOCATION)
            .map { permissionGranted ->
                isAskingLocationPermission = false
                if (permissionGranted) QueueRefreshRequested else LocationPermissionDenied
            }
            .subscribe(actionStream)
            .addTo(subscriptions)
    }

    override fun onResume() {
        super.onResume()

        // Don't try to restart queue searching if it's in progress or permissions request is in progress
        if (isAskingLocationPermission || isDiscovering) return

        if (rxPermissions.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            actionStream.accept(QueueRefreshRequested)
        } else {
            showQueueRefreshFailed()
        }
    }

    override fun onPause() {
        super.onPause()
        connectionsClient.stopDiscovery()
        hostSwipeRefreshLayout.isRefreshing = false

        actionStream.accept(ViewPausing)
    }

    override fun handleState(state: LaunchState) {
        when (state) {
            is DisplayAvailableQueues -> displayQueues(state)
            is EnableNewQueueButton -> enableNewQueueButton(state)
            is LaunchClientActivity -> launchClientActivity(state)
            is NavigateToNewQueue -> navigateToNewQueue()
            is NoQueuesFound -> onNoQueuesFound()
            is SearchForQueues -> searchForQueues()
            is ShowAuthFailedDialog -> showAlertDialog(state.binding)
            is ShowLocationPermissionRequiredDialog -> showAlertDialog(state.binding)
            is ShowPremiumRequiredDialog -> showAlertDialog(state.binding)
            is ShowQueueRefreshFailed -> showQueueRefreshFailed()
            is StopSearchingForQueues -> stopSearchingForQueues()
            is NavigateToAppSettings -> navigateToAppSettings()
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

    private fun searchForQueues() {
        socialQEndpoints.clear()
        hostSwipeRefreshLayout.isRefreshing = true
        swipeRefreshText.text = getString(R.string.queue_searching_message)
        swipeRefreshText.visibility = View.VISIBLE
        availableQueueRecyclerView.visibility = View.GONE

        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()

        connectionsClient.startDiscovery(
            AppConstants.SERVICE_NAME,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            actionStream.accept(StartedNearbySearch(true))
            isDiscovering = true
        }.addOnFailureListener {
            actionStream.accept(StartedNearbySearch(false))
            isDiscovering = false
        }.addOnCanceledListener {
            isDiscovering = false
        }.addOnCompleteListener {
            isDiscovering = false
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

    private fun stopSearchingForQueues() {
        hostSwipeRefreshLayout.isRefreshing = false
        Nearby.getConnectionsClient(this).stopDiscovery()

        actionStream.accept(QueueSearchStopped(socialQEndpoints))
    }

    private fun navigateToAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
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
        hostSwipeRefreshLayout.setColorSchemeResources(R.color.BurntOrangeLight)

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

    private fun startAuthRefresh() {
        val scheduler = getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler?
        scheduler?.schedule(
            JobInfo
                .Builder(
                    AccessService.ACCESS_SERVICE_ID,
                    ComponentName(applicationContext, AccessService::class.java)
                )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(TimeUnit.MINUTES.toMillis(20))
                .build()
        ) ?: throw IllegalStateException("Authorization refresh job failed to initialize")
    }
}