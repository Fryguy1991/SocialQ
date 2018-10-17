package com.chrisfry.socialq.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.business.dagger.modules.SpotifyModule;
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent;
import com.chrisfry.socialq.business.dagger.modules.components.SpotifyComponent;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.PlayerApi;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.PlayerContext;
import com.spotify.protocol.types.PlayerState;
import com.spotify.protocol.types.Track;


import java.util.ArrayList;

import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Playlist;
import kaaes.spotify.webapi.android.models.UserPrivate;

import com.chrisfry.socialq.utils.ApplicationUtils;

/**
 * Service for playing the social queue with Spotify
 */
public class PlayQueueService extends Service {
    private static final String TAG = PlayQueueService.class.getName();

    // Binder given to clients
    private final IBinder mPlayQueueBinder = new PlayQueueBinder();
    // Spotify elements
    private SpotifyService mSpotifyService;
    private SpotifyAppRemote mAppRemote;
    private PlayerApi mPlayerApi;
    private PlayerState mCachedPlayerState;
    private Playlist mPlaylist;
    private UserPrivate mCurrentUser;

    // List to contain objects listening for queue changed events
    private ArrayList<PlayQueueServiceListener> mListeners = new ArrayList<>();

    // Integer to keep track of song index in the queue
    private int mCurrentPlaylistIndex = 0;
    // Boolean flag to store when when delivery is done
    private boolean mAudioDeliveryDoneFlag = true;

    public class PlayQueueBinder extends Binder {
        public PlayQueueService getService() {
            // Return this instance of LocalService so clients can call public methods
            return PlayQueueService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        String accessToken = ApplicationUtils.getAccessToken();
        String playlistId = intent.getStringExtra(AppConstants.SERVICE_PLAYLIST_ID_KEY);

        Log.d(TAG, "onBind: Starting service");
        if (accessToken == null || playlistId == null) {
            stopSelf();
        } else {
            Log.d(TAG, "onBind: Initializing player");
            initSpotifyServiceElements(accessToken, playlistId);
            initSpotifyAppRemote();
        }
        return mPlayQueueBinder;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service is ending.  Shutting down player.");
        // Pause player and disconnect app remote
        if (mPlayerApi != null) {
            mPlayerApi.pause();
            mPlayerApi = null;
        }

        if (mAppRemote != null) {
            SpotifyAppRemote.disconnect(mAppRemote);
        }

        super.onDestroy();
    }

    private void initSpotifyAppRemote() {
        SpotifyAppRemote.connect(this, ApplicationUtils.getConnectionParams(), new Connector.ConnectionListener() {
            @Override
            public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                Log.d(TAG, "App remote successfully connected");
                mAppRemote = spotifyAppRemote;
                mPlayerApi = spotifyAppRemote.getPlayerApi();

                mPlayerApi.subscribeToPlayerState().setEventCallback(new Subscription.EventCallback<PlayerState>() {
                    @Override
                    public void onEvent(PlayerState playerState) {
                        Log.d(TAG, "Received player state");
                        Log.d(TAG, playerState.toString());

                        if ((mCachedPlayerState == null || mCachedPlayerState.isPaused) && !playerState.isPaused) {
                            // Player has begun playing, signal to host
                            Log.d(TAG, "Player started playing");
                            notifyPlayStarted();
                        }

                        if (mCachedPlayerState != null && !mCachedPlayerState.isPaused && playerState.isPaused) {
                            // Player has begun playing, signal to host
                            Log.d(TAG, "Player paused");
                            notifyPaused();
                        }

                        if (mCachedPlayerState != null) {
                            Track cachedTrack = mCachedPlayerState.track;
                            Track currentTrack = playerState.track;
                            if (mCachedPlayerState.playbackPosition > playerState.playbackPosition) {
                                // TODO: Need better check than above.  Can receive player state with unexpected playback positions
                                // Player has changed track, signal to host
                                Log.d(TAG, "Player changed track");
                                mCurrentPlaylistIndex++;
                                notifyQueueChanged();
                            }
                        }

                        mCachedPlayerState = playerState;
                    }
                });
            }

            @Override
            public void onFailure(Throwable throwable) {
                Log.e(TAG, "App remote connection failed");
                Log.e(TAG, throwable.getMessage(), throwable);

                stopSelf();
            }
        });
    }

    private void initSpotifyServiceElements(String accessToken, String playlistId) {
        Log.d(TAG, "Initializing Spotify elements");

        // Setup service for searching Spotify library
        SpotifyComponent componenet = DaggerSpotifyComponent.builder().spotifyModule(
                new SpotifyModule(accessToken)).build();

        mSpotifyService = componenet.service();
        
        mCurrentUser = mSpotifyService.getMe();
        mPlaylist = mSpotifyService.getPlaylist(mCurrentUser.id, playlistId);
    }

    public void requestResume() {
        Log.d(TAG, "RESUME REQUEST");

        if (mPlayerApi != null) {
            if (mCachedPlayerState.isPaused) {
                Log.d(TAG, "Resume playlist");
                mPlayerApi.resume();
            }
        }
    }

    public void requestPlay() {
        Log.d(TAG, "PLAY REQUEST");
        if (mPlayerApi != null) {
            mCachedPlayerState = null;
            Log.d(TAG, "Begin playing from start of playlist");
            mPlayerApi.play(mPlaylist.uri);
        }
    }

    public void requestPause() {
        Log.d(TAG, "PAUSE REQUEST");
        if (mPlayerApi != null) {
            mPlayerApi.pause();
        }
    }

    public void requestNext() {
        Log.d(TAG, "NEXT REQUEST");

        if (mPlayerApi != null && mCachedPlayerState != null) {
            if (mCachedPlayerState.playbackRestrictions.canSkipNext) {
                Log.d(TAG, "Player skipping to next track");
                mPlayerApi.skipNext();
            } else if (!mCachedPlayerState.isPaused) {
                mPlayerApi.pause();
            }
        }
    }

    public void notifyServiceQueueHasChanged() {
        refreshPlaylist();
        notifyQueueChanged();
    }

    // Inner interface used to cast listeners for service events
    public interface PlayQueueServiceListener {

        void onQueueChanged(int mCurrentPlayingIndex);

        void onQueuePause();

        void onQueuePlay();
    }

    private void notifyQueueChanged() {
        for (PlayQueueServiceListener listener : mListeners) {
            listener.onQueueChanged(mCurrentPlaylistIndex);
        }
    }

    private void notifyPaused() {
        for (PlayQueueServiceListener listener : mListeners) {
            listener.onQueuePause();
        }
    }

    private void notifyPlayStarted() {
        for (PlayQueueServiceListener listener : mListeners) {
            listener.onQueuePlay();
        }
    }

    public void addPlayQueueServiceListener(PlayQueueServiceListener listener) {
        mListeners.add(listener);
    }

    public void removePlayQueueServiceListener(PlayQueueServiceListener listener) {
        mListeners.remove(listener);
    }

    private void refreshPlaylist() {
        mPlaylist = mSpotifyService.getPlaylist(mCurrentUser.id, mPlaylist.id);
    }
}
