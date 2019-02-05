package com.chrisfry.socialq.userinterface.fragments


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import androidx.navigation.findNavController
import com.chrisfry.socialq.R

/**
 * A simple [Fragment] subclass.
 *
 */
class NewQueueFragment : Fragment() {

    // UI ELEMENTS
    private lateinit var startQueueButton: View
    private lateinit var fairplayCheckBox: CheckBox
    private lateinit var queueTitleEditText: EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        if (container != null) {
            return inflater.inflate(R.layout.fragment_new_queue, container, false)
        } else {
            return null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        startQueueButton = view.findViewById(R.id.btn_start_queue)
        fairplayCheckBox = view.findViewById(R.id.cb_fairplay_checkbox)
        queueTitleEditText = view.findViewById(R.id.et_queue_name)

        startQueueButton.setOnClickListener {
            var title = queueTitleEditText.text.toString()
            if (title.isEmpty()) {
                title = getString(R.string.queue_title_default_value)
            }

            val isFairPlay = fairplayCheckBox.isChecked

            // Navigate to the host activity
            val hostDirections = NewQueueFragmentDirections.actionNewQueueFragmentToHostActivity(isFairPlay)
            hostDirections.setQueueTitle(title)
            view.findNavController().navigate(hostDirections)

        }
    }
}
