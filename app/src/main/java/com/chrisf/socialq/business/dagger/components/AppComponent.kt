package com.chrisf.socialq.business.dagger.components

import com.chrisf.socialq.business.dagger.modules.AppModule
import com.chrisf.socialq.business.presenters.SpotifyAccessPresenter
import com.chrisf.socialq.services.AccessService
import com.chrisf.socialq.services.SpotifyAccessService
import com.chrisf.socialq.userinterface.fragments.BaseLaunchFragment
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {
    fun inject(accessService: AccessService)
    fun inject(launchFragment: BaseLaunchFragment)
    fun inject(spotifyService: SpotifyAccessService)
    fun inject(spotifyPresenter: SpotifyAccessPresenter)
}