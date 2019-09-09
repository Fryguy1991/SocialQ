package com.chrisf.socialq.userinterface.activities

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.ActivityComponent
import com.chrisf.socialq.enums.RequestType
import com.chrisf.socialq.extensions.addTo
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
import kotlinx.android.synthetic.main.activity_launch.*
import org.jetbrains.anko.startActivity
import timber.log.Timber
import java.util.concurrent.TimeUnit

class LaunchActivity : BaseActivity<LaunchState, LaunchAction, LaunchProcessor>() {
    override val FRAGMENT_HOLDER_ID: Int = View.NO_ID

    private val adapter = QueueDisplayAdapter()

    private var alertDialog: AlertDialog? = null

    private lateinit var scheduler: JobScheduler

    override fun resolveDependencies(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    override fun handleState(state: LaunchState) {
        when (state) {
            RequestLocationPermission -> requestLocationPermission()
            StartAuthRefreshJob -> startPeriodicAccessRefresh()
            RequestAuthorization -> requestAuthorization()
            SearchForQueues -> searchForQueues()
            StopSearchingForQueues -> stopSearchingForQueues()
            NoQueuesFound -> onNoQueuesFound()
            is DisplayAvailableQueues -> displayQueues(state)
            is DisplayCanHostQueue -> displayCanHostQueue(state)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)
        setSupportActionBar(toolbar)

        scheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

        setupViews()
        actionStream.accept(ViewCreated(hasLocationPermission()))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val requestType = RequestType.getRequestTypeFromRequestCode(requestCode)
        when (requestType) {
            RequestType.SPOTIFY_AUTHENTICATION_REQUEST -> {
                val response = AuthenticationClient.getResponse(resultCode, data)
                if (response.type == AuthenticationResponse.Type.CODE) {
                    Timber.d("Authorization code granted")

                    actionStream.accept(AuthCodeRetrieved(response.code))
                    return
                } else {
                    // Show authorization failed dialog
                    showAuthFailedDialog()
                }
            }
            RequestType.LOCATION_PERMISSION_REQUEST -> {
                Timber.e("Launch activity should not receiver $requestType")
            }
            else -> {
                Timber.e("Unhandled request code")
            }
        }
    }

    /**
     * Processes permission results and notifies child objects if location permission has been granted or rejected
     */
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        // Handle request result
        when (requestCode) {
            RequestType.LOCATION_PERMISSION_REQUEST.requestCode -> {
                Timber.d("Location permission request complete")
                val hasPermission = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                actionStream.accept(LocationPermissionRequestComplete(hasPermission))
            }
            else -> {
                // Not handling this request here
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Nearby.getConnectionsClient(this).stopDiscovery()
    }

    private fun setupViews() {
        hostSwipeRefreshLayout.setOnRefreshListener {
            actionStream.accept(QueueRefreshRequested)
        }
        hostSwipeRefreshLayout.setColorSchemeResources(R.color.BurntOrangeLight2)

        adapter.queueSelection
                .map { QueueSelected(it) }
                .subscribe(actionStream)
                .addTo(subscriptions)

        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        availableQueueRecyclerView.layoutManager = layoutManager
        availableQueueRecyclerView.addItemDecoration(QueueItemDecoration(this))
        availableQueueRecyclerView.adapter = adapter
    }

    private fun requestAuthorization() {
        val accessScopes = arrayOf("user-read-private", "streaming", "playlist-modify-private", "playlist-modify-public", "playlist-read-private")
        val builder = AuthenticationRequest.Builder(
                AppConstants.CLIENT_ID,
                AuthenticationResponse.Type.CODE,
                AppConstants.REDIRECT_URI)
        builder.setScopes(accessScopes)
        val request = builder.build()
        AuthenticationClient.openLoginActivity(this, RequestType.SPOTIFY_AUTHENTICATION_REQUEST.requestCode, request)
    }

    private fun requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    RequestType.LOCATION_PERMISSION_REQUEST.requestCode
            )
        }
    }

    private fun startPeriodicAccessRefresh() {
        scheduler.schedule(
                JobInfo.Builder(
                        AppConstants.ACCESS_SERVICE_ID,
                        ComponentName(this, AccessService::class.java)
                )
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(TimeUnit.MINUTES.toMillis(20))
                        .build()
        )
    }

    /**
     * Determines if ACCESS_COARSE_LOCATION permission has been granted and requests it if needed
     *
     * @return - true if permission is already granted, false if not
     */
    private fun hasLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermission(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Process.myPid(),
                    Process.myUid()) == PackageManager.PERMISSION_GRANTED
        } else {
            // If low enough SDK version, manifest contains permission and doesn't need to be requested at runtime
            true
        }
    }

    private fun searchForQueues() {
        hostSwipeRefreshLayout.isRefreshing = true
        swipeRefreshText.text = getString(R.string.queue_searching_message)

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

    private fun stopSearchingForQueues() {
        hostSwipeRefreshLayout.isRefreshing = false
        Nearby.getConnectionsClient(this).stopDiscovery()
    }

    private fun displayCanHostQueue(state: DisplayCanHostQueue) {
        newQueueButton.isEnabled = true
        newQueueButton.clicks()
                .throttleFirst(300, TimeUnit.MILLISECONDS)
                .subscribe {
                    if (state.canHost) {
                        startActivity<HostQueueOptionsActivity>()
                    } else {
                        showPremiumRequiredDialog()
                    }
                }
                .addTo(subscriptions)
    }

    private fun displayQueues(state: DisplayAvailableQueues) {
        adapter.updateAdapter(state.queueList)
    }

    private fun onNoQueuesFound() {
        adapter.updateAdapter(emptyList())
        swipeRefreshText.text = getString(R.string.no_host_found_message)
    }

    private fun showAuthFailedDialog() {
        if (alertDialog == null || alertDialog?.isShowing == false) {
            alertDialog = AlertDialog.Builder(this)
                    .setView(R.layout.dialog_auth_fail)
                    .setPositiveButton(R.string.retry) { dialog, which ->
                        requestAuthorization()
                    }
                    .setNegativeButton(R.string.close_app) { dialog, which ->
                        finish()
                    }
                    .setOnCancelListener {
                        finish()
                    }
                    .create()
            alertDialog!!.show()
        }
    }

    private fun showPremiumRequiredDialog() {
        if (alertDialog == null || alertDialog?.isShowing == false) {
            alertDialog = AlertDialog.Builder(this)
                    .setView(R.layout.dialog_premium_required)
                    .setPositiveButton(R.string.ok) { dialog, which ->
                        dialog.dismiss()
                    }
                    .create()
            alertDialog!!.show()
        }
    }

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
}