package com.chrisf.socialq.userinterface.activities

import android.content.Intent

import androidx.appcompat.app.AppCompatActivity
import com.chrisf.socialq.R
import io.reactivex.disposables.CompositeDisposable

abstract class ServiceActivity : AppCompatActivity() {

    protected val subscriptions = CompositeDisposable()

    override fun onDestroy() {
        super.onDestroy()
        subscriptions.clear()
    }

    protected fun startSearchActivity() {
        val searchIntent = Intent(this, SearchActivity::class.java)
        startActivityForResult(searchIntent, SEARCH_REQUEST_CODE)
        overridePendingTransition(R.anim.slide_in_from_bottom, R.anim.fade_out)
    }

    companion object {
        const val SEARCH_REQUEST_CODE = 100
    }
}