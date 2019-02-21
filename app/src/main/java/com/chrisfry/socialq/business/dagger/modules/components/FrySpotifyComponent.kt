package com.chrisfry.socialq.business.dagger.modules.components

import com.chrisfry.socialq.business.dagger.modules.FrySpotifyModule
import com.chrisfry.socialq.userinterface.App
import com.chrisfry.socialq.userinterface.fragments.BaseLaunchFragment
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [FrySpotifyModule::class])
interface FrySpotifyComponent {
    fun inject(application: App)
    fun inject(launchFragment: BaseLaunchFragment)
}