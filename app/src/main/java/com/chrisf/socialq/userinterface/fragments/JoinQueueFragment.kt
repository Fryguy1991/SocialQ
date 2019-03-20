package com.chrisf.socialq.userinterface.fragments


import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.chrisf.socialq.R
import com.chrisf.socialq.model.JoinableQueueModel

/**
 * A simple [Fragment] subclass.
 *
 */
class JoinQueueFragment : Fragment() {

    private var listener: JoinQueueFragmentListener? = null

    private lateinit var queueModel: JoinableQueueModel

    // UI ELEMENTS
    private lateinit var ownerTextView: TextView
    private lateinit var isFairplayTextView: TextView

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is JoinQueueFragmentListener) {
            listener = context
        } else {
            throw RuntimeException(context.toString() + " must implement LaunchFragmentListener")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        if (container != null) {
            return inflater.inflate(R.layout.fragment_join_queue, container, false)
        } else {
            return null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        listener?.showQueueTitle(queueModel.queueName)

        ownerTextView = view.findViewById(R.id.tv_owner)
        isFairplayTextView = view.findViewById(R.id.tv_fair_play_status)

        // Setup queue view
        if (queueModel.isFairPlayActive) {
            isFairplayTextView.text = getString(R.string.on)
        } else {
            isFairplayTextView.text = getString(R.string.off)
        }

        ownerTextView.text = queueModel.ownerName
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
    interface JoinQueueFragmentListener {
        fun showQueueTitle(queueTitle: String)
    }
}
