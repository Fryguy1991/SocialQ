package com.chrisf.socialq.dagger.components

import com.chrisf.socialq.dagger.modules.ActivityModule
import com.chrisf.socialq.dagger.modules.FragmentModule
import com.chrisf.socialq.dagger.modules.ProcessorModule
import com.chrisf.socialq.dagger.qualifier.ActivityScope
import com.chrisf.socialq.userinterface.activities.*
import dagger.Subcomponent

@Subcomponent(modules = [ActivityModule::class, ProcessorModule::class])
@ActivityScope
interface ActivityComponent {
    fun fragmentComponent(fragmentModule: FragmentModule): FragmentComponent
    fun inject(activity: SearchActivity)
    fun inject(activity: LaunchActivity)
    fun inject(activity: HostQueueOptionsActivity)
    fun inject(activity: GateActivity)
    fun inject(activity: SplashActivity)
}

interface ActivityComponentHolder {
    fun provideActivityComponent() : ActivityComponent
}