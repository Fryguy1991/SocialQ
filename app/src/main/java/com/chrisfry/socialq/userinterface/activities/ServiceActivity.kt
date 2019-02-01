package com.chrisfry.socialq.userinterface.activities

import android.content.Intent

import android.view.MenuItem
import com.chrisfry.socialq.R
import com.chrisfry.socialq.enums.RequestType

abstract class ServiceActivity : BaseActivity() {
    companion object {
        val TAG = ServiceActivity::class.java.name
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