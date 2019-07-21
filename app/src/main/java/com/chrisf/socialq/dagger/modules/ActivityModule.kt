package com.chrisf.socialq.dagger.modules

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.chrisf.socialq.userinterface.activities.BaseActivity
import dagger.Module
import dagger.Provides

@Module
class ActivityModule(private val activity: BaseActivity<*, *, *>) {

    @Provides
    fun providesLifecycle() : Lifecycle {
        return (activity as AppCompatActivity).lifecycle
    }
}