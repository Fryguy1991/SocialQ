package com.chrisfry.socialq.userinterface.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.fragment.app.Fragment
import com.chrisfry.socialq.enums.RequestType

abstract class BaseLaunchFragment : Fragment() {
companion object {
    val TAG = BaseLaunchFragment::class.java.name
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
        Log.d(TAG, "Received request type: $requestType")

        // Handle request result
        when (requestType) {
            RequestType.LOCATION_PERMISSION_REQUEST -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Location permission received")

                locationPermissionReceived()
            } else {
                // Permissions rejected. User will see permissions request until permission
                // is granted or else the application will not be able to function
                Log.d(TAG, "Location permission rejected")

                locationPermissionRejected()
            }
            else -> {
                // Not handling this request here
            }
        }
    }

    abstract fun locationPermissionReceived()

    abstract fun locationPermissionRejected()
}