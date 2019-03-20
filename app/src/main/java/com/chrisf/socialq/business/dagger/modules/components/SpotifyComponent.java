package com.chrisf.socialq.business.dagger.modules.components;

import javax.inject.Singleton;

import com.chrisf.socialq.business.dagger.modules.SpotifyModule;
import dagger.Component;
import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;

@Singleton
@Component(modules = SpotifyModule.class)
public interface SpotifyComponent {
    SpotifyApi api();

    SpotifyService service();
}
