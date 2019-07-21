package com.chrisf.socialq.userinterface.fragments

import android.content.*
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.chrisf.socialq.R
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.enums.SpotifyUserType
import com.chrisf.socialq.enums.UserType
import com.chrisf.socialq.model.AccessModel
import com.chrisf.socialq.model.QueueModel
import com.chrisf.socialq.userinterface.adapters.QueueDisplayAdapter
import com.chrisf.socialq.userinterface.interfaces.IQueueSelectionListener
import com.chrisf.socialq.userinterface.views.QueueItemDecoration
import com.chrisf.socialq.utils.DisplayUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import java.lang.Exception
import java.util.regex.Pattern


/**
 * Landing fragment for the application. Responsible for finishing authorization and displaying any
 * SocialQ's that can be joined.
 */
class LaunchFragment : BaseLaunchFragment(), IQueueSelectionListener, SwipeRefreshLayout.OnRefreshListener {
    companion object {
        val TAG = LaunchFragment::class.java.name
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment LaunchFragment.
         */
        @JvmStatic
        fun newInstance() = LaunchFragment().apply {}

        // Message what value for displaying host search complete
        private const val HOST_SEARCH_COMPLETE = 0
        // Wait time before displaying instruction to start own queue (in milliseconds)
        private const val NEARBY_WAIT_TIME = 5000L
    }

    // NEARBY VALUES
    // Flag to indicate if we successfully started searching for hosts
    private var nearbySuccessfullyDiscovering = false
    // List of queues that can be joined
    private val joinableQueues: MutableList<QueueModel> = mutableListOf()

    // UI ELEMENTS
    // Button for starting a new queue
    private lateinit var newQueueButton: View
    // Recycler view and adapter references
    private val queueAdapter = QueueDisplayAdapter()
    private lateinit var recyclerView: RecyclerView
    // Builder for premium required dialog
    private lateinit var alertbuilder: AlertDialog.Builder
    // Text display for no host found
    private lateinit var hostSearchMessage: TextView
    // Layout for handling swipe refresh
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    // Used as a flag to determine if we need to launch a host or client after a permission request
    private var userType = UserType.NONE
    // Cached queue model for joining queue after location permission is granted
    private var cachedQueueModel: QueueModel? = null

    // Receiver for registered broadcasts
    private val launchFragmentBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            // For now only action is access token refreshed
            if (intent != null && intent.action != null) {
                when (intent.action) {
                    AppConstants.BR_INTENT_ACCESS_TOKEN_UPDATED -> {
                        Log.d(TAG, "Received broadcast that access token was refreshed")

                        // Ensure if a new user has signed in we store them in the access model
                        AccessModel.setCurrentUser(null)
                        requestSpotifyUser()
                    }
                    else -> {
                        Log.e(TAG, "Not expecting to receive " + intent.action!!)
                    }
                }
            }
        }
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message?) {
            if (msg != null) {
                when (msg.what) {
                    HOST_SEARCH_COMPLETE -> {
                        if (joinableQueues.size > 0) {
                            // Hide host search message
                            hostSearchMessage.visibility = View.GONE
                        } else {
                            // Show message indicating no hosts were found
                            hostSearchMessage.text = getString(R.string.no_host_found_message)
                            val layoutParams = hostSearchMessage.layoutParams as ConstraintLayout.LayoutParams
                            layoutParams.topMargin = DisplayUtils.convertDpToPixels(context, 140)
                            hostSearchMessage.visibility = View.VISIBLE
                        }

                        // Update adapter with retrieved list of hosts
                        queueAdapter.updateAdapter(joinableQueues)
                        // Hide refresh animation
                        swipeRefreshLayout.isRefreshing = false
                        // Stop searching for hosts
                        stopNearbyDiscovery()
                    }
                    else -> {
                        Log.e(TAG, "Handler shouldn't receive message ${msg.what}")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val parentActivity = activity
        if (parentActivity != null) {
            // Register to receive broadcasts when the auth code has been received
            LocalBroadcastManager.getInstance(parentActivity).registerReceiver(launchFragmentBroadcastReceiver, IntentFilter(AppConstants.BR_INTENT_ACCESS_TOKEN_UPDATED))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        if (container != null) {
            return inflater.inflate(R.layout.fragment_launch, container, false)
        } else {
            return null
        }
    }

    override fun onResume() {
        super.onResume()

        if (hasLocationPermission()) {
            // Display that we are searching for queues
            hostSearchMessage.text = getString(R.string.queue_searching_message)
            hostSearchMessage.visibility = View.VISIBLE
            swipeRefreshLayout.isRefreshing = true
            searchForQueues()
        }

        val user = currentUser
        if (user != null) {
            newQueueButton.isEnabled = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        newQueueButton = view.findViewById(R.id.btn_new_queue)
        recyclerView = view.findViewById(R.id.rv_available_queue_list)
        hostSearchMessage = view.findViewById(R.id.tv_swipe_refresh_text)
        swipeRefreshLayout = view.findViewById(R.id.srl_host_refresh_layout)

        // TODO: Should map this to colorAccent so any color refactors are applied
        swipeRefreshLayout.setColorSchemeResources(R.color.BurntOrangeLight2)
        swipeRefreshLayout.setOnRefreshListener(this)

        newQueueButton.setOnClickListener {
            userType = UserType.HOST
            if (hasLocationPermission()) {
                handleHostStart()
            }
        }

        // Add recycler view item decoration
        val layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(QueueItemDecoration(context))

        queueAdapter.listener = this
        recyclerView.adapter = queueAdapter
        queueAdapter.notifyDataSetChanged()

        // Create builder for premium required dialog
        val alertContext = context
        if (alertContext != null) {
            alertbuilder = AlertDialog.Builder(alertContext)
                    .setView(R.layout.dialog_premium_required)
                    .setPositiveButton(R.string.ok) { dialog, which ->
                        dialog.dismiss()
                    }
        }
    }

    private fun handleHostStart() {
        val user = currentUser
        if (user != null) {
            when (SpotifyUserType.getSpotifyUserTypeFromProductType(user.product)) {
                SpotifyUserType.PREMIUM -> findNavController().navigate(R.id.action_launchFragment_to_newQueueFragment)
                SpotifyUserType.FREE,
                SpotifyUserType.OPEN -> showPremiumRequiredDialog()
            }
        }
    }

    override fun onPause() {
        stopNearbyDiscovery()

        // Clear host search complete messages to handler
        handler.removeMessages(HOST_SEARCH_COMPLETE)
        // Clear host list
        joinableQueues.clear()
        queueAdapter.updateAdapter(joinableQueues)
        // Hide searching text
        hostSearchMessage.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false

        super.onPause()
    }

    override fun onDestroy() {
        val brContext = context
        if (brContext != null) {
            LocalBroadcastManager.getInstance(brContext).unregisterReceiver(launchFragmentBroadcastReceiver)
        }
        super.onDestroy()
    }

    override fun onRefresh() {
        Log.d(TAG, "User has pulled down to refresh host list")

        if (!nearbySuccessfullyDiscovering) {
            hostSearchMessage.text = getString(R.string.queue_searching_message)
            val layoutParams = hostSearchMessage.layoutParams as ConstraintLayout.LayoutParams
            layoutParams.topMargin = DisplayUtils.convertDpToPixels(context, 75)

            if (joinableQueues.size == 0) {
                hostSearchMessage.visibility = View.VISIBLE
            }
            swipeRefreshLayout.isRefreshing = true

            searchForQueues()
        } else {
            Log.e(TAG, "Flag indicates we are already discovering")
        }
    }


    private fun stopNearbyDiscovery() {
        // Stop discovering SocialQs
        val context = activity
        if (context != null) {
            Log.d(TAG, "Stopping discovering of SocialQ Hosts")

            Nearby.getConnectionsClient(context).stopDiscovery()
            nearbySuccessfullyDiscovering = false
        }
    }

    private fun showPremiumRequiredDialog() {
        alertbuilder.create().show()
    }

    override fun queueSelected(queueModel: QueueModel) {
        userType = UserType.CLIENT
        cachedQueueModel = queueModel
        if (hasLocationPermission()) {
            handleClientStart(queueModel)
        }
    }

    private fun handleClientStart(queueModel: QueueModel) {
        cachedQueueModel = null
        val joinQueueDirections = LaunchFragmentDirections.actionLaunchFragmentToClientActivity(queueModel.queueName, queueModel.endpointId)
        findNavController().navigate(joinQueueDirections)
    }

    private fun searchForQueues() {
        joinableQueues.clear()

        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()

        val context = activity
        if (context != null) {
            Nearby.getConnectionsClient(context).startDiscovery(AppConstants.SERVICE_NAME, endpointDiscoveryCallback, options)
                    .addOnSuccessListener(object : OnSuccessListener<Void> {
                        override fun onSuccess(p0: Void?) {
                            Log.d(TAG, "Successfully started discovering SocialQ Hosts")
                            nearbySuccessfullyDiscovering = true

                            if (isResumed) {
                                // Send delayed message to display no host message
                                handler.sendEmptyMessageDelayed(HOST_SEARCH_COMPLETE, NEARBY_WAIT_TIME)
                            } else {
                                // Fragment not resumed. onPause may have missed stopping nearby discovery
                                stopNearbyDiscovery()
                            }
                        }

                    })
                    .addOnFailureListener(object : OnFailureListener {
                        override fun onFailure(p0: Exception) {
                            Log.e(TAG, "Failed to start device discovery for SocialQ Hosts")
                            Log.e(TAG, p0.message)

                            nearbySuccessfullyDiscovering = false

                            swipeRefreshLayout.isRefreshing = false

                            hostSearchMessage.text = getString(R.string.host_search_failed_message)
                            val layoutParams = hostSearchMessage.layoutParams as ConstraintLayout.LayoutParams
                            layoutParams.topMargin = DisplayUtils.convertDpToPixels(context, 140)
                            hostSearchMessage.visibility = View.VISIBLE

                            queueAdapter.updateAdapter(joinableQueues)
                        }
                    })
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, discoveredEndpointInfo: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint Found")

            if (discoveredEndpointInfo.serviceId == AppConstants.SERVICE_NAME) {
                Log.d(TAG, "Found a SocialQ host with endpoint ID $endpointId")

                val hostNameMatcher = Pattern.compile(AppConstants.NEARBY_HOST_NAME_REGEX).matcher(discoveredEndpointInfo.endpointName)

                if (hostNameMatcher.find()) {
                    val queueName = hostNameMatcher.group(1)
                    val ownerName = hostNameMatcher.group(2)
                    val isFairplayCharacter = hostNameMatcher.group(3)

                    val isFairplay = isFairplayCharacter == AppConstants.FAIR_PLAY_TRUE_CHARACTER
                    joinableQueues.add(QueueModel(endpointId, queueName, ownerName, isFairplay))
                } else {
                    Log.e(TAG, "Endpoint ID $endpointId has an invalid name")
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint Lost")

            for (queue: QueueModel in joinableQueues) {
                if (queue.endpointId == endpointId) {
                    Log.d(TAG, "Lost a SocialQ host with endpoint ID ${queue.endpointId}")

                    joinableQueues.remove(queue)
                }
            }
        }
    }

    override fun locationPermissionReceived() {
        // Received location permission.  If button for host/client was pressed launch respective action
        when (userType) {
            UserType.HOST -> {
                // User tried to start a queue without location permission
                handleHostStart()
            }
            UserType.CLIENT -> {
                // User tried to join a queue without location permission
                val queueToJoin = cachedQueueModel
                if (queueToJoin != null) {
                    handleClientStart(queueToJoin)
                } else {
                    Log.e(TAG, "Error, cached queue model is null")
                }
            }
            UserType.NONE -> {
                // Permission granted from fragment launch. Do nothing
            }
        }
        userType = UserType.NONE
    }

    override fun locationPermissionRejected() {
        userType = UserType.NONE
    }

    override fun userRetrieved() {
        newQueueButton.isEnabled = true
    }
}
