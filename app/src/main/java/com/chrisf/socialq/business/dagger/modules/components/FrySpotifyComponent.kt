package com.chrisf.socialq.business.dagger.modules.components

import com.chrisf.socialq.business.dagger.modules.FrySpotifyModule
import com.chrisf.socialq.business.presenters.SpotifyAccessPresenter
import com.chrisf.socialq.services.SpotifyAccessService
import com.chrisf.socialq.userinterface.fragments.BaseLaunchFragment
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [FrySpotifyModule::class])
interface FrySpotifyComponent {
    fun inject(launchFragment: BaseLaunchFragment)
    fun inject(spotifyService: SpotifyAccessService)
    fun inject(spotifyPresenter: SpotifyAccessPresenter)
}