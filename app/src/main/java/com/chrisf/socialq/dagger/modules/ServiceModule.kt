package com.chrisf.socialq.dagger.modules

import androidx.lifecycle.Lifecycle
import com.chrisf.socialq.services.BaseService
import dagger.Module
import dagger.Provides

@Module
class ServiceModule(private val service: BaseService<*, *, *>) {

    @Provides
    fun providesNullLifecycle() : Lifecycle? {
        return null
    }
}