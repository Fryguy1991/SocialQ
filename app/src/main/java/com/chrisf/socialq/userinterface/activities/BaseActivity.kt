package com.chrisf.socialq.userinterface.activities

import android.app.ActivityManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.chrisf.socialq.R

abstract class BaseActivity : AppCompatActivity() {
    companion object {
        val TAG = BaseActivity::class.java.name
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            else -> {
                // Do nothing
                return false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure when app is in recents a white title bar is displayed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            var taskDescription: ActivityManager.TaskDescription
            taskDescription = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                    ActivityManager.TaskDescription(
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
}