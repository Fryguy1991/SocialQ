package com.chrisf.socialq.userinterface.activities

import android.content.Intent

import android.view.MenuItem
import android.view.View
import com.chrisf.socialq.R
import com.chrisf.socialq.enums.RequestType

abstract class ServiceActivity : BaseActivity(), View.OnClickListener {
    companion object {
        val TAG = ServiceActivity::class.java.name
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

    override fun onClick(v: View?) {
        if (v != null) {
            when {
                v.id == R.id.btn_add_track -> {
                    startSearchActivity()
                }
                else -> {
                    // Click not handled here, do nothing
                }
            }
        }
    }
}