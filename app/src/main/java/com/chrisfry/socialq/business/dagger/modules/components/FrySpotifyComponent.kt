package com.chrisfry.socialq.business.dagger.modules.components

import com.chrisfry.socialq.business.dagger.modules.FrySpotifyModule
import com.chrisfry.socialq.business.presenters.SpotifyAccessPresenter
import com.chrisfry.socialq.services.SpotifyAccessService
import com.chrisfry.socialq.userinterface.fragments.BaseLaunchFragment
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [FrySpotifyModule::class])
interface FrySpotifyComponent {
    fun inject(launchFragment: BaseLaunchFragment)
    fun inject(spotifyService: SpotifyAccessService)
    fun inject(spotifyPresenter: SpotifyAccessPresenter)
}