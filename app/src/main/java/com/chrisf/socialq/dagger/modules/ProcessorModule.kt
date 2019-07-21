package com.chrisf.socialq.dagger.modules

import androidx.lifecycle.Lifecycle
import com.chrisf.socialq.dagger.qualifier.ActivityScope
import com.chrisf.socialq.network.FrySpotifyService
import com.chrisf.socialq.processor.SearchProcessor
import dagger.Module
import dagger.Provides
import io.reactivex.disposables.CompositeDisposable

@Module
class ProcessorModule {

    @Provides
    @ActivityScope
    fun providesSearchProcessor(
            spotifyService: FrySpotifyService,
            lifecycle: Lifecycle,
            subscriptions: CompositeDisposable
    ) : SearchProcessor {
        return SearchProcessor(
                spotifyService,
                lifecycle,
                subscriptions
        )
    }
}