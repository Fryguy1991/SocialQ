package com.chrisfry.socialq.userinterface.activities

import android.content.Context
import android.content.Intent
import android.os.*
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.RequestType
import com.chrisfry.socialq.enums.UserType
import com.chrisfry.socialq.model.AccessModel
import com.chrisfry.socialq.userinterface.fragments.*
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse

class BaseSpotifyActivity: AppCompatActivity(), StartFragment.StartFragmentListener, HostFragmentBase.BaseHostFragmentListener {
    val TAG = BaseSpotifyActivity::class.java.name

    // Fragment references
    private var hostFragment: HostFragmentBase? = null

    // Variables needed for passing to a new host queue from start fragment
    private lateinit var queueTitle: String
    private var isFairPlay: Boolean = false

    private var accessType = UserType.NONE

    // Handler for sending messages to the UI thread
    private val mHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                AppConstants.ACCESS_TOKEN_REFRESH ->
                    // Don't request access tokens if activity is being shut down
                    if (!isFinishing && accessType != UserType.NONE) {
                        Log.d(TAG, "Requesting new access token on UI thread")
                        requestAccessToken(AccessModel.getAccessType())
                    }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.base_activity_layout)

        // Allow network operation in main thread
        val policy = StrictMode.ThreadPolicy.Builder()
                .permitAll().build()
        StrictMode.setThreadPolicy(policy)

        // Stop keyboard from pushing UI up
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        // Start application by showing start fragment
        if (supportFragmentManager.findFragmentById(R.id.fragment_holder) == null) {
            val startFragment = StartFragment()
            startFragment.listener = this
            val transaction = supportFragmentManager.beginTransaction();
            transaction.add(R.id.fragment_holder, startFragment, StartFragment.TAG)
            transaction.commit()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val requestType = RequestType.getRequestTypeFromRequestCode(requestCode)
        Log.d(TAG, "Received request type: $requestType")

        when (requestType) {
            RequestType.SPOTIFY_AUTHENTICATION_REQUEST -> {
                val response = AuthenticationClient.getResponse(resultCode, data)
                if (response.type == AuthenticationResponse.Type.TOKEN) {
                    Log.d(TAG, "Access token granted")

                    // Store when access token expires (response "ExpiresIn" is in seconds, subtract a minute to worry less about timing)
                    val accessExpireTime = System.currentTimeMillis() + (response.expiresIn - 60) * 1000
                    AccessModel.setAccess(response.accessToken, accessType, accessExpireTime)

                    // Start thread responsible for notifying UI thread when new access token is needed
                    AccessRefreshThread().start()

                    when (accessType) {
                        UserType.HOST -> launchHostFragment()
                        UserType.CLIENT, UserType.NONE -> {
                            //TODO: Implement client launching
                        }
                    }

                    // Refresh access token for all fragments that require one
                    for (frag: Fragment in supportFragmentManager.fragments) {
                        if (frag is SpotifyFragment) {
                            frag.refreshAccessToken(response.accessToken)
                        }
                    }
                } else {
                    Log.d(TAG, "Authentication Response: " + response.error)
                    Toast.makeText(this@BaseSpotifyActivity, getString(R.string.toast_authentication_error_host), Toast.LENGTH_SHORT).show()
                }
            }
            RequestType.LOCATION_PERMISSION_REQUEST, RequestType.REQUEST_ENABLE_BT, RequestType.REQUEST_DISCOVER_BT,
                RequestType.SEARCH_REQUEST , RequestType.NONE -> {
                // Base activity should do nothing for these requests
            }

        }
    }

    private fun launchHostFragment() {
        if (hostFragment == null) {
            Log.d(TAG, "Creating new host fragment")

            // Initialize a new host activity
            val args = Bundle()
            args.putString(AppConstants.QUEUE_TITLE_KEY, queueTitle)
            args.putBoolean(AppConstants.FAIR_PLAY_KEY, isFairPlay)
            hostFragment = HostFragmentNearby.newInstance(args)
            hostFragment!!.listener = this

            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_holder, hostFragment!!)
            transaction.addToBackStack(null)
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            transaction.commitAllowingStateLoss()
        }
    }

    private fun launchQueueSearchFragment() {

    }

    override fun onBackPressed() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_holder)
        if (currentFragment != null && currentFragment is BaseFragment) {
            if (currentFragment.handleOnBackPressed()) {
                return
            } else {
                super.onBackPressed()
            }
        }
    }

    fun hideKeyboard(focusedView: View?) {
        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (focusedView != null && focusedView.isFocused) {
            inputManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
        }
    }

    /**
     * Use Spotify login activity to retrieve an access token
     */
    private fun requestAccessToken(userType: UserType) {
        accessType = userType
        lateinit var scopes: Array<String>
        when(userType) {
            UserType.HOST -> scopes = arrayOf("user-read-private", "streaming", "playlist-modify-private")
            UserType.CLIENT -> scopes = arrayOf("user-read-private")
            UserType.NONE -> {
                Log.e(TAG, "Need to request access for host or client")
                return
            }
        }

        Log.d(TAG, "Requesting access token for $accessType")
        val builder = AuthenticationRequest.Builder(
                AppConstants.CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                AppConstants.REDIRECT_URI)
        builder.setScopes(scopes)
        val request = builder.build()
        AuthenticationClient.openLoginActivity(this, RequestType.SPOTIFY_AUTHENTICATION_REQUEST.requestCode, request)
    }

    /**
     * Inner thread class used to detect when a new access code is needed and send message to handler to request a new one.
     */
    private inner class AccessRefreshThread internal constructor() : Thread(Runnable {
        while (true) {
            if (System.currentTimeMillis() >= AccessModel.getAccessExpireTime()) {
                Log.d(TAG, "Detected that we need a new access token")

                // Access is no longer valid
                AccessModel.reset()

                val message = Message()
                message.what = AppConstants.ACCESS_TOKEN_REFRESH
                mHandler.dispatchMessage(message)
                break
            }
        }
    })

    // BEGIN METHODS RESPONSIBLE FOR INTERRACTING WITH START FRAGMENT
    override fun startHost(queueName: String, isFairPlay: Boolean) {
        queueTitle = queueName
        this.isFairPlay = isFairPlay

        // Check to see if we need a new access token from Spotify
        if (AccessModel.getAccessType() != UserType.HOST || System.currentTimeMillis() > AccessModel.getAccessExpireTime()) {
            requestAccessToken(UserType.HOST)
        } else {
            launchHostFragment()
        }
    }

    override fun startQueueSearch() {
        Log.d(TAG, "User wants to search for a host queue")
        Toast.makeText(this, "TODO: Start host search", Toast.LENGTH_SHORT).show()
    }
    // END METHODS RESPONSIBLE FOR INTERRACTING WITH START FRAGMENT

    // BEGIN METHODS RESPONSIBLE FOR INTERRACTING WITH HOST FRAGMENT
    override fun hostShutDown() {
        Log.d(TAG, "Removing host fragment")
        hostFragment = null
        supportFragmentManager.popBackStack()
        title = resources.getString(R.string.app_name)
        queueTitle = ""
        isFairPlay = resources.getBoolean(R.bool.fair_play_default)
    }

    override fun showHostTitle() {
        Log.d(TAG, "Showing host title")
        title = queueTitle
    }
    // END METHODS RESPONSIBLE FOR INTERRACTING WITH HOST FRAGMENT
}