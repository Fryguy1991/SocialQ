package com.chrisfry.socialq.userinterface.fragments

import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.SpotifyUserType
import com.chrisfry.socialq.enums.UserType
import com.chrisfry.socialq.model.AccessModel
import com.chrisfry.socialq.model.JoinableQueueModel
import com.chrisfry.socialq.userinterface.adapters.QueueDisplayAdapter
import com.chrisfry.socialq.userinterface.interfaces.IQueueSelectionListener
import com.chrisfry.socialq.userinterface.views.QueueItemDecoration
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

                        val user = currentUser
                        if (user == null) {
                            requestSpotifyUser()
                        }
                    }
                    else -> {
                        Log.e(TAG, "Not expecting to receive " + intent.action!!)
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
                    .setPositiveButton(R.string.confirm) { dialog, which ->
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
