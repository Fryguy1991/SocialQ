package com.chrisfry.socialq.userinterface.fragments

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import androidx.appcompat.widget.AppCompatCheckBox
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
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

    // View used to find nav controller for navigation
    private lateinit var hostButton: View

    // Click listener for start/join buttons
    private var typeSelectClickListener: View.OnClickListener = View.OnClickListener { view ->
        when (view.id) {
            R.id.btn_host_queue -> {
                userType = UserType.HOST
                // Ensure we have location permission before starting a host
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    handleHostStart()
                }
            }
            R.id.btn_join_queue -> {
                userType = UserType.CLIENT
                // Ensure we have location permission before starting a client
                // TODO: Navigate to queue connect screen
                Toast.makeText(context, "TODO: Start host search", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val baseView = inflater.inflate(R.layout.start_screen, container, false)
        initUi(baseView)
        return baseView
    }

    private fun initUi(baseView: View) {
        hostButton = baseView.findViewById<View>(R.id.btn_host_queue)
        hostButton.setOnClickListener(typeSelectClickListener)
        baseView.findViewById<View>(R.id.btn_join_queue).setOnClickListener(typeSelectClickListener)
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

            val args = bundleOf(
                    AppConstants.QUEUE_TITLE_KEY to queueTitle,
                    AppConstants.FAIR_PLAY_KEY to isFairPlayChecked
            )

//            findNavController().navigate(R.id.host_fragment_nearby, args)
            findNavController().navigate(R.id.action_startFragment_to_hostFragmentNearby, args)

            dialog.dismiss()
        }
        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which -> dialog.dismiss() }

        // Show host queue options dialog
        dialogBuilder.create().show()
    }
}