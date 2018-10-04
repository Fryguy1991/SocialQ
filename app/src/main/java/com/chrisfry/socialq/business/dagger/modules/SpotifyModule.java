package com.chrisfry.socialq.business.dagger.modules;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;

@Module
public class SpotifyModule {
    private static String accessToken;

    public SpotifyModule(String accessToken) {
        SpotifyModule.accessToken = accessToken;
    }

    @Provides @Singleton
     static SpotifyApi provideSpotifyApi() {
        return new SpotifyApi().setAccessToken(accessToken);
    }

    @Provides @Singleton
    static SpotifyService provideSpotifyService(SpotifyApi spotifyApi) {
        return spotifyApi.getService();
    }

}
