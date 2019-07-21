package com.chrisf.socialq.dagger.components

import com.chrisf.socialq.dagger.modules.ActivityModule
import com.chrisf.socialq.dagger.modules.ProcessorModule
import com.chrisf.socialq.userinterface.activities.NewSearchActivity
import dagger.Subcomponent

@Subcomponent(modules = [ActivityModule::class, ProcessorModule::class])
interface ActivityComponent {
    fun inject(activity: NewSearchActivity)
}