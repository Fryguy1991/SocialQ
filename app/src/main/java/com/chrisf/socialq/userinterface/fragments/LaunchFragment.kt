package com.chrisf.socialq.userinterface.fragments

import android.content.*
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.R
import com.chrisf.socialq.business.AppConstants
import com.chrisf.socialq.enums.SpotifyUserType
import com.chrisf.socialq.enums.UserType
import com.chrisf.socialq.model.AccessModel
import com.chrisf.socialq.model.JoinableQueueModel
import com.chrisf.socialq.userinterface.adapters.QueueDisplayAdapter
import com.chrisf.socialq.userinterface.interfaces.IQueueSelectionListener
import com.chrisf.socialq.userinterface.views.QueueItemDecoration
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
class LaunchFragment : BaseLaunchFragment(), IQueueSelectionListener {
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

        // Message what value for displaying no host found
        private const val HOST_INSTRUCTION_MESSAGE_WHAT = 0
        // Wait time before displaying instruction to start own queue (in milliseconds)
        private const val NEARBY_WAIT_TIME = 5000L
    }

    // NEARBY VALUES
    // Flag to indicate if we successfully started searching for hosts
    private var nearbySuccessfullyDiscovering = false
    // List of queues that can be joined
    private val joinableQueues: MutableList<JoinableQueueModel> = mutableListOf()

    // UI ELEMENTS
    // Button for starting a new queue
    private lateinit var newQueueButton: View
    // Recycler view and adapter references
    private val queueAdapter = QueueDisplayAdapter()
    private lateinit var recyclerView: RecyclerView
    // Builder for premium required dialog
    private lateinit var alertbuilder: AlertDialog.Builder
    // Text display for no host found
    private lateinit var noHostMessage: View
    // Animation view for host discovery
    private lateinit var animatedDiscoveryView: View

    // Used as a flag to determine if we need to launch a host or client after a permission request
    private var userType = UserType.NONE
    // Cached queue model for joining queue after location permission is granted
    private var cachedQueueModel: JoinableQueueModel? = null

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
                    HOST_INSTRUCTION_MESSAGE_WHAT -> {
                        if (joinableQueues.size == 0) {
                            recyclerView.visibility = View.GONE
                            noHostMessage.visibility = View.VISIBLE
                        }
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
        noHostMessage = view.findViewById(R.id.tv_no_host_found)
        animatedDiscoveryView = view.findViewById(R.id.cv_spinkit_animation)

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
        super.onPause()
    }

    override fun onDestroy() {
        val brContext = context
        if (brContext != null) {
            LocalBroadcastManager.getInstance(brContext).unregisterReceiver(launchFragmentBroadcastReceiver)
        }
        super.onDestroy()
    }

    private fun stopNearbyDiscovery() {
        // Stop discovering SocialQs
        val context = activity
        if (context != null) {
            Log.d(TAG, "Stopping discovering of SocialQ Hosts")

            Nearby.getConnectionsClient(context).stopDiscovery()
            nearbySuccessfullyDiscovering = false

            // Clear messages to handler for displaying no host message
            handler.removeMessages(HOST_INSTRUCTION_MESSAGE_WHAT)

            noHostMessage.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            animatedDiscoveryView.visibility = View.GONE
        }

        joinableQueues.clear()
    }

    private fun showPremiumRequiredDialog() {
        alertbuilder.create().show()
    }

    override fun queueSelected(queueModel: JoinableQueueModel) {
        userType = UserType.CLIENT
        cachedQueueModel = queueModel
        if (hasLocationPermission()) {
            handleClientStart(queueModel)
        }
    }

    private fun handleClientStart(queueModel: JoinableQueueModel) {
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
                                handler.sendEmptyMessageDelayed(HOST_INSTRUCTION_MESSAGE_WHAT, NEARBY_WAIT_TIME)
                                animatedDiscoveryView.visibility = View.VISIBLE
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
                    joinableQueues.add(JoinableQueueModel(endpointId, queueName, ownerName, isFairplay))

                    queueAdapter.updateAdapter(joinableQueues)

                    noHostMessage.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                } else {
                    Log.e(TAG, "Endpoint ID $endpointId has an invalid name")
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint Lost")

            for (queue: JoinableQueueModel in joinableQueues) {
                if (queue.endpointId == endpointId) {
                    Log.d(TAG, "Lost a SocialQ host with endpoint ID ${queue.endpointId}")

                    joinableQueues.remove(queue)
                    queueAdapter.updateAdapter(joinableQueues)
                }
            }

            if (joinableQueues.size == 0) {
                // No longer have any joinable queues. Start wait time for no host message
                handler.sendEmptyMessageDelayed(HOST_INSTRUCTION_MESSAGE_WHAT, NEARBY_WAIT_TIME)
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
