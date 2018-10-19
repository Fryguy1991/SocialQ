package com.chrisfry.socialq.userinterface.activities

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.chrisfry.socialq.R

open class BaseActivity : AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.base_activity)
    }
}