package com.chrisf.socialq.dagger.components

import com.chrisf.socialq.dagger.modules.ActivityModule
import com.chrisf.socialq.dagger.modules.AppModule
import com.chrisf.socialq.dagger.modules.ServiceModule
import com.chrisf.socialq.userinterface.fragments.BaseLaunchFragment
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {
    fun activityComponent(activityModule: ActivityModule): ActivityComponent
    fun serviceComponent(serviceModule: ServiceModule): ServiceComponent

    // TODO: Add processors and move these to Activity/Fragment Component
    // Fragments
    fun inject(fragment: BaseLaunchFragment)
}