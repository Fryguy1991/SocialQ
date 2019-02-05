package com.chrisfry.socialq.userinterface.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.model.JoinableQueueModel
import com.chrisfry.socialq.userinterface.adapters.QueueDisplayAdapter
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
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [LaunchFragment.LaunchFragmentListener] interface
 * to handle interaction events.
 * Use the [LaunchFragment.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class LaunchFragment : Fragment() {
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

    private var listener: LaunchFragmentListener? = null

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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is LaunchFragmentListener) {
            listener = context
        } else {
            throw RuntimeException(context.toString() + " must implement LaunchFragmentListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        searchForQueues()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        if (container != null) {
            return inflater.inflate(R.layout.fragment_launch, container, false)
        } else {
            return null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        newQueueButton = view.findViewById(R.id.btn_new_queue)
        recyclerView = view.findViewById(R.id.rv_available_queue_list)

        newQueueButton.setOnClickListener {
            view.findNavController().navigate(R.id.action_launchFragment_to_newQueueFragment)
        }

        // Add recycler view item decoration
        val layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(QueueItemDecoration(context))

        recyclerView.adapter = queueAdapter
        queueAdapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        // Stop discovering SocialQs
        val context = activity
        if (nearbySuccessfullyDiscovering && context != null) {
            Log.d(TAG, "Stopping discovering of SocialQ Hosts")

            Nearby.getConnectionsClient(context).stopDiscovery()
            nearbySuccessfullyDiscovering = false
        }
        super.onDestroy()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     *
     *
     * See the Android Training lesson [Communicating with Other Fragments]
     * (http://developer.android.com/training/basics/fragments/communicating.html)
     * for more information.
     */
    interface LaunchFragmentListener {
        fun queueSelected(endpointId: String)
    }

    private fun searchForQueues() {
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
            for (queue: JoinableQueueModel in joinableQueues) {
                if (queue.endpointId == endpointId) {
                    Log.d(TAG, "Lost a SocialQ host with endpoint ID ${queue.endpointId}")

                    joinableQueues.remove(queue)
                    queueAdapter.updateAdapter(joinableQueues)
                }
            }
        }
    }
}
