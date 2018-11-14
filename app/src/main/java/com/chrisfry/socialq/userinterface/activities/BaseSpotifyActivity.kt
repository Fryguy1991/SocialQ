package com.chrisfry.socialq.userinterface.activities

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.fragments.StartFragment
import kaaes.spotify.webapi.android.SpotifyApi
import kaaes.spotify.webapi.android.SpotifyService

class BaseSpotifyActivity: AppCompatActivity(), StartFragment.StartFragmentListener {
    val TAG = BaseSpotifyActivity::class.java.name

    // Spotify elements
    private lateinit var spotifyApi: SpotifyApi
    private lateinit var spotifyService: SpotifyService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.base_activity_layout)

        // Start application by showing start fragment
        if (supportFragmentManager.findFragmentById(R.id.fragment_holder) == null) {
            val startFragment = StartFragment()
            startFragment.listener = this
            val transaction = supportFragmentManager.beginTransaction();
            transaction.add(R.id.fragment_holder, startFragment, StartFragment.TAG)
            transaction.commit()
        }
    }

    fun hideKeyboard(focusedView: View?) {
        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (focusedView != null && focusedView.isFocused) {
            inputManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
        }
    }

    // BEGIN METHODS RESPONSIBLE FOR INTERRACTING WITH START FRAGMENT
    override fun startHost(queueName: String, isFairPlay: Boolean) {
        Toast.makeText(this, "TODO: Start host \"$queueName\" with fairplay \"$isFairPlay\"", Toast.LENGTH_SHORT).show()
    }

    override fun startClient() {
        Toast.makeText(this, "TODO: Start host search", Toast.LENGTH_SHORT).show()
    }
    // END METHODS RESPONSIBLE FOR INTERRACTING WITH START FRAGMENT
}