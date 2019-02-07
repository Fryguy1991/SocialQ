package com.chrisfry.socialq.userinterface.activities

import android.content.Intent
import android.view.Menu

import android.view.MenuItem
import com.chrisfry.socialq.R
import com.chrisfry.socialq.enums.RequestType

abstract class ServiceActivity : BaseActivity() {
    companion object {
        val TAG = ServiceActivity::class.java.name
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_screen_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (!super.onOptionsItemSelected(item)) {
            when (item.itemId) {
                R.id.search_fragment -> {
                    startSearchActivity()
                    return true
                }
                else -> {
                    // Do nothing
                    return false
                }
            }
        } else {
            return true
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