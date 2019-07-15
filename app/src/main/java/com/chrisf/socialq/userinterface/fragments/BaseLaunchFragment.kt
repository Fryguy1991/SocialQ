package com.chrisf.socialq.userinterface.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.fragment.app.Fragment
import com.chrisf.socialq.enums.RequestType
import com.chrisf.socialq.model.AccessModel
import com.chrisf.socialq.userinterface.App
import kaaes.spotify.webapi.android.SpotifyApi
import kaaes.spotify.webapi.android.SpotifyCallback
import kaaes.spotify.webapi.android.SpotifyError
import kaaes.spotify.webapi.android.models.UserPrivate
import retrofit.client.Response
import timber.log.Timber
import javax.inject.Inject

abstract class BaseLaunchFragment : Fragment() {

    @Inject
    protected lateinit var spotifyApi: SpotifyApi
    protected var currentUser: UserPrivate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val parentActivity = activity
        if (parentActivity != null) {
            (parentActivity.application as App).appComponent.inject(this)
        }
    }

    /**
     * Determines if ACCESS_COARSE_LOCATION permission has been granted and requests it if needed
     *
     * @return - true if permission is already granted, false (and requests) if not
     */
    protected fun hasLocationPermission(): Boolean {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val parentActivity = activity

            if (parentActivity != null) {
                if (parentActivity.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED) {
                    return true
                } else {
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), RequestType.LOCATION_PERMISSION_REQUEST.requestCode)
                    return false
                }
            } else {
                return false
            }
        }
        // If low enough SDK version, manifest contains permission and doesn't need to be requested at runtime
        return true
    }

    /**
     * Processes permission results and notifies child objects if location permission has been granted or rejected
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val requestType = RequestType.getRequestTypeFromRequestCode(requestCode)
        Timber.d("Received request type: $requestType")

        // Handle request result
        when (requestType) {
            RequestType.LOCATION_PERMISSION_REQUEST -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Timber.d("Location permission received")

                locationPermissionReceived()
            } else {
                // Permissions rejected. User will see permissions request until permission
                // is granted or else the application will not be able to function
                Timber.d("Location permission rejected")

                locationPermissionRejected()
            }
            else -> {
                // Not handling this request here
            }
        }
    }

    protected fun requestSpotifyUser() {
        if (AccessModel.getCurrentUser() == null) {
            spotifyApi.service.getMe(currentUserCallback)
        } else {
            currentUser = AccessModel.getCurrentUser()
            userRetrieved()
        }
    }

    private val currentUserCallback = object : SpotifyCallback<UserPrivate>() {
        override fun success(user: UserPrivate?, response: Response?) {
            if (user != null) {
                Timber.d("Successfully retrieved current user")

                AccessModel.setCurrentUser(user)
                currentUser = user
                userRetrieved()
            } else {
                Timber.e("Error user was null")
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            Timber.e("Error retrieving current user")
            Timber.e(spotifyError?.errorDetails?.message.toString())
            // TODO: Think about what we should do if we can't retrieve the user
        }
    }

    abstract fun locationPermissionReceived()

    abstract fun locationPermissionRejected()

    abstract fun userRetrieved()
}