package com.chrisfry.socialq.userinterface.fragments

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.support.v7.widget.AppCompatCheckBox
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.chrisfry.socialq.R
import com.chrisfry.socialq.enums.RequestType
import com.chrisfry.socialq.enums.UserType
import com.chrisfry.socialq.userinterface.activities.BaseSpotifyActivity

class StartFragment : BaseFragment() {
    companion object {
        val TAG = StartFragment::class.java.name
    }

    // Used as a flag to determine if we need to launch a host or client after a permission request
    private var userType = UserType.NONE

    // Values for handling user input during host dialog
    private var isFairPlayChecked = false
    private var queueTitle = ""

    // Listener for fragment notifications
    var listener: StartFragmentListener? = null

    // Click listener for start/join buttons
    private var typeSelectClickListener: View.OnClickListener = View.OnClickListener { view ->
        when (view.id) {
            R.id.btn_host_queue -> {
                userType = UserType.HOST
                // Ensure we have location permission before starting a host
                if (hasLocationPermission()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        handleHostStart()
                    }
                }
            }
            R.id.btn_join_queue -> {
                userType = UserType.CLIENT
                // Ensure we have location permission before starting a client
                if (hasLocationPermission()) {
                    listener?.startQueueSearch()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val baseView = inflater.inflate(R.layout.start_screen, container, false)
        initUi(baseView)
        return baseView
    }

    private fun initUi(baseView: View) {
        baseView.findViewById<View>(R.id.btn_host_queue).setOnClickListener(typeSelectClickListener)
        baseView.findViewById<View>(R.id.btn_join_queue).setOnClickListener(typeSelectClickListener)
    }

    /**
     * Determines if ACCESS_COARSE_LOCATION permission has been granted and requests it if needed
     *
     * @return - true if permission is already granted, false (and requests) if not
     */
    private fun hasLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return if (activity?.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), RequestType.LOCATION_PERMISSION_REQUEST.requestCode)
                false
            }
        }
        // If low enough SDK version, manifest contains permission and doesn't need to be requested at runtime
        return true
    }


    private fun handleHostStart() {
        Log.d(TAG, "Launching host dialog")
        val dialogBuilder = AlertDialog.Builder(activity)
        dialogBuilder.setTitle(R.string.queue_options)

        // Reset default options
        isFairPlayChecked = resources.getBoolean(R.bool.fair_play_default)
        queueTitle = resources.getString(R.string.queue_title_default_value)

        // Inflate content view and get references to UI elements
        val contentView = layoutInflater.inflate(R.layout.new_queue_dialog, null)
        val queueNameEditText = contentView.findViewById<EditText>(R.id.et_queue_name)
        val fairPlayCheckbox = contentView.findViewById<AppCompatCheckBox>(R.id.cb_fairplay_checkbox)

        // Set dialog content view
        dialogBuilder.setView(contentView)

        queueNameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // Don't care before text changed
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // Don't care on text changed
            }

            override fun afterTextChanged(s: Editable) {
                // Only care about result
                queueTitle = s.toString()
            }
        })

        fairPlayCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
            buttonView.clearFocus()
            isFairPlayChecked = isChecked
        }

        dialogBuilder.setPositiveButton(R.string.start) { dialog, which ->
            if (dialog is AlertDialog) {
                if (activity is BaseSpotifyActivity) {
                    (activity as BaseSpotifyActivity).hideKeyboard(dialog.currentFocus)
                }
            }

            // Ensure we don't display an empty queue title
            if (queueTitle.isEmpty()) {
                queueTitle = resources.getString(R.string.queue_title_default_value)
            }

            listener?.startHost(queueTitle, isFairPlayChecked)
            dialog.dismiss()
        }
        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which -> dialog.dismiss() }

        // Show host queue options dialog
        dialogBuilder.create().show()
    }

    interface StartFragmentListener {
        /**
         * Starts a host qeueue with the given options
         *
         * @param queueName - Name of the host queue
         * @param isFairPlay - True if fairplay is on, false if off
         */
        fun startHost(queueName: String, isFairPlay: Boolean)

        /**
         * Starts a search for host queues
         */
        fun startQueueSearch()
    }
}