package com.chrisfry.socialq.userinterface.activities

import android.app.ActivityManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle

import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.chrisfry.socialq.R
import com.chrisfry.socialq.enums.RequestType

abstract class ServiceActivity : AppCompatActivity() {
    companion object {
        val TAG = ServiceActivity::class.java.name
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure when app is in recents a white title bar is displayed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            var taskDescription: ActivityManager.TaskDescription
            taskDescription = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {ActivityManager.TaskDescription(
                        getString(R.string.app_name),
                        R.mipmap.app_launcher_icon_round,
                        resources.getColor(R.color.White, theme))}
                else -> {
                    val color = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> resources.getColor(R.color.White, theme)
                        else -> resources.getColor(R.color.White)
                    }

                    val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.app_launcher_icon_round)
                    ActivityManager.TaskDescription(getString(R.string.app_name), bitmap, color)
                }
            }
            setTaskDescription(taskDescription)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.search_fragment -> {
                startSearchActivity()
                return true
            }
            else ->
                // Do nothing
                return false
        }
    }

    protected fun launchStartActivityAndFinish() {
        val startIntent = Intent(this, StartActivity::class.java)
        startActivity(startIntent)
        finish()
    }

    protected fun startSearchActivity() {
        val searchIntent = Intent(this, SearchActivity::class.java)
        startActivityForResult(searchIntent, RequestType.SEARCH_REQUEST.requestCode)
    }
}