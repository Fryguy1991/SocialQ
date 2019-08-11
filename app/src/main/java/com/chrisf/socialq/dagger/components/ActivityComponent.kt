package com.chrisf.socialq.dagger.components

import com.chrisf.socialq.dagger.modules.ActivityModule
import com.chrisf.socialq.dagger.modules.FragmentModule
import com.chrisf.socialq.dagger.modules.ProcessorModule
import com.chrisf.socialq.dagger.qualifier.ActivityScope
import com.chrisf.socialq.userinterface.activities.SearchActivity
import dagger.Subcomponent

@Subcomponent(modules = [ActivityModule::class, ProcessorModule::class])
@ActivityScope
interface ActivityComponent {
    fun fragmentComponent(fragmentModule: FragmentModule): FragmentComponent
    fun inject(activity: SearchActivity)
}

interface ActivityComponentHolder {
    fun provideActivityComponent() : ActivityComponent
}