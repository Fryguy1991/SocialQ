package com.chrisfry.socialq.userinterface.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.RequestType
import com.chrisfry.socialq.model.AccessModel
import com.chrisfry.socialq.userinterface.fragments.*
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse
import androidx.navigation.ui.NavigationUI

class BaseSpotifyActivity : AppCompatActivity(), StartFragment.StartFragmentListener, HostFragmentBase.BaseHostFragmentListener {
    val TAG = BaseSpotifyActivity::class.java.name

    // Fragment references
    private var startFragment: StartFragment? = null
    private var hostFragment: HostFragmentBase? = null
    private var searchFragment: SearchFragment? = null

    // Menu action item references
    private lateinit var searchActionItem: MenuItem

    // Variables needed for passing to a new host queue from start fragment
    private lateinit var queueTitle: String
    private var isFairPlay: Boolean = false

    // Handler for sending messages to the UI thread
    private val mHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                AppConstants.ACCESS_TOKEN_REFRESH ->
                    // Don't request access tokens if activity is being shut down
                    if (!isFinishing) {
                        Log.d(TAG, "Requesting new access token on UI thread")
                        requestAccessToken()
                    }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.main_screen_menu, menu)

        searchActionItem = menu!!.findItem(R.id.search_action)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.getItemId()) {
            R.id.search_action -> {
//                launchSearchFragment()
                return true
            }
            else ->
                // Do nothing
                return false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.base_activity_layout)

        // Setup application toolbar
        val navController = findNavController(R.id.nav_host_fragment)
        val appBarConfig = AppBarConfiguration.Builder(navController.graph).build()
        val toolbar = findViewById<Toolbar>(R.id.app_toolbar)
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfig)

        // Allow network operation in main thread
        val policy = StrictMode.ThreadPolicy.Builder()
                .permitAll().build()
        StrictMode.setThreadPolicy(policy)

        // Stop keyboard from pushing UI up
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        // Ensure we have location permission and access to Spotify API
        if (hasLocationPermission()) {
            requestAccessToken()
        }

        // Start application by showing start fragment
//        launchStartFragment()
    }

    override fun onDestroy() {
        // This should ensure that the access refresh thread ends
        AccessModel.reset()

        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val requestType = RequestType.getRequestTypeFromRequestCode(requestCode)
        Log.d(TAG, "Received request type: $requestType")

        when (requestType) {
            RequestType.SPOTIFY_AUTHENTICATION_REQUEST -> {
                handleAuthenticationResponse(AuthenticationClient.getResponse(resultCode, data))
            }
            RequestType.LOCATION_PERMISSION_REQUEST, RequestType.REQUEST_ENABLE_BT, RequestType.REQUEST_DISCOVER_BT,
            RequestType.SEARCH_REQUEST, RequestType.NONE -> {
                // Base activity should do nothing for these requests
            }
        }
    }

    private fun handleAuthenticationResponse(response: AuthenticationResponse) {
        when (response.type) {
            AuthenticationResponse.Type.CODE -> {
                Log.d(TAG, "Access code granted")
                // TODO: Part of authentication flow.  Currently not using.
            }
            AuthenticationResponse.Type.TOKEN -> {
                Log.d(TAG, "Access token granted")

                // Store when access token expires (response "ExpiresIn" is in seconds, subtract a minute to worry less about timing)
                val accessExpireTime = System.currentTimeMillis() + (response.expiresIn - 60) * 1000
                AccessModel.setAccess(response.accessToken, accessExpireTime)

                // Start thread responsible for notifying UI thread when new access token is needed
                AccessRefreshThread().start()

                // Refresh access token for all fragments that require one
                for (frag: androidx.fragment.app.Fragment in supportFragmentManager.fragments) {
                    if (frag is SpotifyFragment) {
                        frag.refreshAccessToken(response.accessToken)
                    }
                }
            }
            AuthenticationResponse.Type.EMPTY -> {
                Log.e(TAG, "User didn't complete Spotify login activity")
                // TODO: show dialog indicating that spotify access is required for app functionality giving user optinon to close the app
                finish()
            }
            AuthenticationResponse.Type.ERROR -> {
                Log.e(TAG, "Authentication Error: " + response.error)
                Toast.makeText(this@BaseSpotifyActivity, getString(R.string.toast_authentication_error_host), Toast.LENGTH_SHORT).show()
                // TODO: Need to gracefully handle authentication error.  Could be catastrophic depending on where in the application the user is
            }
            AuthenticationResponse.Type.UNKNOWN, null -> {
                Log.e(TAG, "Authentication response is unknown or null. Trying again")
                requestAccessToken()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val requestType = RequestType.getRequestTypeFromRequestCode(requestCode)
        Log.d(StartFragment.TAG, "Received request type: $requestType")

        // Handle request result (currently only handling location permission requests)
        if (requestType == RequestType.LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(StartFragment.TAG, "Received location permission")
                requestAccessToken()
            } else {
                // Permissions rejected. User will see permissions request until permission is granted or else
                // the application will not be able to function
                Log.e(StartFragment.TAG, "Location permission rejected, closing application")
                finish()
            }
        }
    }

//    private fun launchStartFragment() {
//        if (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) == null) {
//            startFragment = StartFragment()
//            startFragment?.listener = this
//            val transaction = supportFragmentManager.beginTransaction();
//            transaction.add(R.id.nav_host_fragment, startFragment!!, StartFragment.TAG)
//            transaction.commit()
//        }
//    }

//    private fun launchHostFragment() {
//        if (hostFragment == null) {
//            Log.d(TAG, "Creating new host fragment")
//
//            // Initialize a new host activity
//            val args = Bundle()
//            args.putString(AppConstants.QUEUE_TITLE_KEY, queueTitle)
//            args.putBoolean(AppConstants.FAIR_PLAY_KEY, isFairPlay)
//            hostFragment = HostFragmentNearby.newInstance(args)
//            hostFragment!!.listener = this
//
//            val transaction = supportFragmentManager.beginTransaction()
//            transaction.replace(R.id.nav_host_fragment, hostFragment!!)
//            transaction.addToBackStack(null)
//            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
//            transaction.commitAllowingStateLoss()
//        }
//    }

    private fun launchQueueSearchFragment() {
        // TODO: implement this
    }

//    private fun launchSearchFragment() {
//        Log.d(TAG, "Launching search fragment")
//        // TODO: Do we want to return to the search fragment as if we never left (probably not)?
//        if (searchFragment == null) {
//            searchFragment = SearchFragment()
//        }
//        // TODO: Add listener for searchFragment
//        val transaction = supportFragmentManager.beginTransaction()
//        transaction.replace(R.id.nav_host_fragment, searchFragment!!)
//        transaction.addToBackStack(null)
//        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
//        transaction.commit()
//
//        // Change title and remove search icon
//        title = resources.getString(R.string.search_activity_name)
//        searchActionItem.isVisible = false
//    }

    override fun onBackPressed() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        if (currentFragment != null && currentFragment is BaseFragment) {
            if (currentFragment.handleOnBackPressed()) {
                return
            } else {
                if (currentFragment is SearchFragment) {
                    // Reshow search icon and change title to queue name
                    searchActionItem.isVisible = true
                    title = queueTitle
                    supportFragmentManager.popBackStack()
                } else {
                    super.onBackPressed()
                }
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
     * Determines if ACCESS_COARSE_LOCATION permission has been granted and requests it if needed
     *
     * @return - true if permission is already granted, false (and requests) if not
     */
    private fun hasLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return if (checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), RequestType.LOCATION_PERMISSION_REQUEST.requestCode)
                false
            }
        }
        // If low enough SDK version, manifest contains permission and doesn't need to be requested at runtime
        return true
    }

    /**
     * Use Spotify login activity to retrieve an access token
     */
    private fun requestAccessToken() {
        Log.d(TAG, "Requesting Spotify access token")
        val builder = AuthenticationRequest.Builder(
                AppConstants.CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                AppConstants.REDIRECT_URI)
        builder.setScopes(arrayOf("user-read-private", "streaming", "playlist-modify-private"))
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

//        launchHostFragment()

//        // Check to see if we need a new access token from Spotify
//        if (System.currentTimeMillis() > AccessModel.getAccessExpireTime()) {
//            requestAccessToken()
//        } else {
//            launchHostFragment()
//        }
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

        // Return to start fragment appearance
        supportFragmentManager.popBackStack()
        title = resources.getString(R.string.app_name)
        searchActionItem.isVisible = false

        // Reset values used for starting queue
        queueTitle = ""
        isFairPlay = resources.getBoolean(R.bool.fair_play_default)
    }

    override fun showHostTitle() {
        Log.d(TAG, "Showing host title")
        // Change action bar for host activity
        title = queueTitle
        searchActionItem.isVisible = true
    }
    // END METHODS RESPONSIBLE FOR INTERRACTING WITH HOST FRAGMENT
}