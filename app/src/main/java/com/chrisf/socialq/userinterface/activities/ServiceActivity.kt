package com.chrisf.socialq.userinterface.activities

import android.content.Intent
import android.view.Menu

import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.chrisf.socialq.R
import com.chrisf.socialq.enums.RequestType

abstract class ServiceActivity : AppCompatActivity(), View.OnClickListener {
    companion object {
        val TAG = ServiceActivity::class.java.name
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
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

    protected fun startSearchActivity() {
        val searchIntent = Intent(this, SearchActivity::class.java)
        startActivityForResult(searchIntent, RequestType.SEARCH_REQUEST.requestCode)
        overridePendingTransition(R.anim.slide_in_from_bottom, R.anim.fade_out)
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