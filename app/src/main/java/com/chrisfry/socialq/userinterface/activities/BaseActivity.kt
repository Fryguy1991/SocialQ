package com.chrisfry.socialq.userinterface.activities

import android.content.Intent

import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.chrisfry.socialq.R
import com.chrisfry.socialq.enums.RequestType

abstract class BaseActivity : AppCompatActivity() {
    private val TAG = BaseActivity::class.java.name

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.search_fragment -> {
                val searchIntent = Intent(this, SearchActivity::class.java)
                startActivityForResult(searchIntent, RequestType.SEARCH_REQUEST.requestCode)
                return true
            }
            else ->
                // Do nothing
                return false
        }
    }
}