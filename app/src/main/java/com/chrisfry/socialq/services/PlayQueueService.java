package com.chrisfry.socialq.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.business.dagger.modules.SpotifyModule;
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent;
import com.chrisfry.socialq.business.dagger.modules.components.SpotifyComponent;
import com.chrisfry.socialq.model.AccessModel;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Connectivity;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Playlist;
import kaaes.spotify.webapi.android.models.UserPrivate;

/**
 * Service for playing the social queue with Spotify
 */
public class PlayQueueService extends Service implements ConnectionStateCallback,
        Player.NotificationCallback, Player.OperationCallback {
    private static final String TAG = PlayQueueService.class.getName();

    // Binder given to clients
    private final IBinder mPlayQueueBinder = new PlayQueueBinder();
    // Member player object used for playing audio
    private SpotifyPlayer mSpotifyPlayer;
    // API for retrieve SpotifyService object
    private SpotifyApi mSpotifyApi;
    // Service for adding songs to the queue
    private SpotifyService mSpotifyService;
    // List to contain objects listening for queue changed events
    private ArrayList<PlayQueueServiceListener> mListeners = new ArrayList<>();
    // Playlist object for the queue
    private Playlist mPlaylist;
    // Current user object
    private UserPrivate mCurrentUser;
    // Integer to keep track of song index in the queue
    private int mCurrentPlaylistIndex = 0;
    // Boolean flag to store when when delivery is done
    private boolean mAudioDeliveryDoneFlag = true;
    // Boolean flag to store if MetaData is incorrect
    private boolean mIncorrectMetaDataFlag = false;
    // Boolean flag to store when a track has been fully delivered
    private boolean mTrackDelivered = false;
    // Boolean flag for storing whether the service is bound (to the host activity)
    private boolean mIsBound = false;
    // Boolean flag for if a pause event was requested by the user
    private boolean mUserRequestedPause = false;

    private final Player.OperationCallback mConnectivityCallback = new Player.OperationCallback() {
        @Override
        public void onSuccess() {
            Log.d(TAG, "Success!");
        }

        @Override
        public void onError(Error error) {
            Log.e(TAG, "ERROR: " + error);
        }
    };

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
    public int onStartCommand(Intent intent, int flags, int startId) {

        String accessToken = AccessModel.getAccessToken();
        String playlistId = intent.getStringExtra(AppConstants.SERVICE_PLAYLIST_ID_KEY);

        Log.d(TAG, "onStartCommand: Starting service");
        if (accessToken == null || playlistId == null) {
            stopSelf();
        } else {
            Log.d(TAG, "onStartCommand: Initializing player");
            initPlayer(accessToken);
            initSpotifyServiceElements(accessToken, playlistId);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service is being bound");
        mIsBound = true;
        return mPlayQueueBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "Service is rebinding");
        super.onRebind(intent);
        mIsBound = true;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Service is completely unbound");
        mIsBound = false;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service is ending.  Shutting down player.");
        mSpotifyPlayer.logout();
        try {
            Spotify.awaitDestroyPlayer(this, 10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Spotify.destroyPlayer(this);
        }

        super.onDestroy();
    }

    private void initPlayer(String accessToken) {
        // Setup Spotify player
        Config playerConfig = new Config(this, accessToken, AppConstants.CLIENT_ID);
        mSpotifyPlayer = Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver() {
            @Override
            public void onInitialized(SpotifyPlayer player) {
                Log.d(TAG, "Player initialized");
                player.setConnectivityStatus(mConnectivityCallback,
                        getNetworkConnectivity(PlayQueueService.this));
                player.addConnectionStateCallback(PlayQueueService.this);
                player.addNotificationCallback(PlayQueueService.this);
            }

            @Override
            public void onError(Throwable error) {
                Log.e(TAG, "ERROR: Could not initialize player: " + error.getMessage());
            }
        });
    }

    private void initSpotifyServiceElements(String accessToken, String playlistId) {
        Log.d(TAG, "Initializing Spotify elements");

        // Setup service for searching Spotify library
        SpotifyComponent componenet = DaggerSpotifyComponent.builder().spotifyModule(
                new SpotifyModule(accessToken)).build();

        mSpotifyApi = componenet.api();
        mSpotifyService = componenet.service();
        
        mCurrentUser = mSpotifyService.getMe();
        mPlaylist = mSpotifyService.getPlaylist(mCurrentUser.id, playlistId);
    }

    public void requestPlay() {
        Log.d(TAG, "PLAY REQUEST");
        if (mSpotifyPlayer.getPlaybackState() != null) {
             if (mAudioDeliveryDoneFlag) {
                 if (mCurrentPlaylistIndex < mPlaylist.tracks.items.size()) {
                     // If audio has previously been completed (or never started)
                     // start the playlist at the current index
                     Log.d(TAG, "Audio previously finished.\nStarting playlist from index: " + mCurrentPlaylistIndex);
                     mSpotifyPlayer.playUri(this, mPlaylist.uri, mCurrentPlaylistIndex, 1);
                     mAudioDeliveryDoneFlag = false;
                     mIncorrectMetaDataFlag = false;
                 } else {
                     Log.d(TAG, "Nothing to play");
                 }
             } else {
                 Log.d(TAG, "Resuming player");
                 if (!mSpotifyPlayer.getPlaybackState().isPlaying) {
                     mSpotifyPlayer.resume(this);
                 }
            }
        }
    }

    public void requestPause() {
        Log.d(TAG, "PAUSE REQUEST");
        mUserRequestedPause = true;
        mSpotifyPlayer.pause(this);
    }

    public void requestPlayNext() {
        Log.d(TAG, "NEXT REQUEST");
        if (!mAudioDeliveryDoneFlag) {
            // Don't allow a skip when we're waiting on a new track to be queued
            if (mIncorrectMetaDataFlag) {
                // Meta data is not correct.  Start playlist from next index
                Log.d(TAG, "MetaData not correct on NEXT request");
                mCurrentPlaylistIndex++;
                mIncorrectMetaDataFlag = false;
                Log.d(TAG, "Starting playlist from index: " + mCurrentPlaylistIndex);
                mSpotifyPlayer.playUri(this, mPlaylist.uri, mCurrentPlaylistIndex, 1);
                notifyNext();
            } else {
                mSpotifyPlayer.skipToNext(this);
            }
        } else {
            Log.d(TAG, "Can't go to next track");
        }
    }

    public void notifyServiceQueueHasChanged() {
        refreshPlaylist();
        notifyQueueChanged();
    }

    public void notifyServiceMetaDataIsStale() {
        // If a track is queued to the next position, SpotifyPlayer MetaData will not be updated. This causes the
        // player to either not play a track (if one didn't previously exist) or to play the previously queued track.
        // Flag is used in requestPlayNext method and when track delivery is finished to manually start the playlist
        // at the next song index.
        refreshPlaylist();
        mIncorrectMetaDataFlag = true;
        notifyQueueChanged();
    }

    public void notifyServiceAccessTokenChanged(String accessToken) {
        Log.d(TAG, "Updating Spotify API and service with new access token");
        mSpotifyApi.setAccessToken(accessToken);
        mSpotifyService = mSpotifyApi.getService();
    }

    @Override
    public void onPlaybackEvent(PlayerEvent playerEvent) {
        Log.d(TAG, "New playback event: " + playerEvent.name());

        switch (playerEvent) {
            case kSpPlaybackNotifyPlay:
                Log.d(TAG, "Player has started playing");
                notifyPlayStarted();
                break;
            case kSpPlaybackNotifyContextChanged:
                break;
            case kSpPlaybackNotifyPause:
                Log.d(TAG, "Player has paused");
                // If meta data is incorrect we won't actually pause (unless user requested pause)
                if (mUserRequestedPause || !mIncorrectMetaDataFlag) {
                    notifyPaused();
                    mUserRequestedPause = false;
                }
                break;
            case kSpPlaybackNotifyTrackChanged:
                break;
            case kSpPlaybackNotifyMetadataChanged:
                if (mTrackDelivered && mIncorrectMetaDataFlag) {
                    Log.d(TAG, "Incorrect MetaData, playlist not actually done.\nPlaying playlist from index: " + mCurrentPlaylistIndex);
                    mAudioDeliveryDoneFlag = false;
                    mTrackDelivered = false;
                    mIncorrectMetaDataFlag = false;
                    mSpotifyPlayer.playUri(this, mPlaylist.uri, mCurrentPlaylistIndex, 1);
                }
                break;
            case kSpPlaybackNotifyTrackDelivered:
                mTrackDelivered = true;
            case kSpPlaybackNotifyNext:
                // Track has changed, remove top track from queue list
                Log.d(TAG, "Player has moved to next track (next/track complete)");
                mCurrentPlaylistIndex++;
                notifyNext();
                break;
            case kSpPlaybackNotifyAudioDeliveryDone:
                // Current queue playlist has finished
                Log.d(TAG, "Player has finished playing audio");
                mAudioDeliveryDoneFlag = true;

                // Stop service if we're not bound and out of songs
                if (!mIsBound) {
                    Log.d(TAG, "Out of songs and not bound to host activity. Shutting down service.");
                    stopSelf();
                }
                break;
            default:
                // Do nothing or future implementation
                break;
        }
    }

    @Override
    public void onPlaybackError(Error error) {
        Log.e(TAG, "ERROR: New playback error: " + error.name());
    }

    @Override
    public void onSuccess() {
        Log.d(TAG, "Great Success!");
    }

    @Override
    public void onError(Error error) {
        Log.e(TAG, "ERROR: Playback error received : Error - " + error.name());
    }

    /**
     * Registering for connectivity changes in Android does not actually deliver them to
     * us in the delivered intent.
     *
     * @param context Android context
     * @return Connectivity state to be passed to the SDK
     */
    private Connectivity getNetworkConnectivity(Context context) {
        ConnectivityManager connectivityManager;
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.isConnected()) {
                return Connectivity.fromNetworkType(activeNetwork.getType());
            } else {
                return Connectivity.OFFLINE;
            }
        }
        return Connectivity.OFFLINE;
    }

    // START CONNECTION CALLBACK METHODS
    @Override
    public void onLoggedIn() {
        Log.d(TAG, "Logged In!");
    }

    @Override
    public void onLoggedOut() {
        Log.d(TAG, "Logged Out!");
    }

    @Override
    public void onLoginFailed(Error error) {
        Log.e(TAG, "ERROR: Login Error: " + error.name());
    }

    @Override
    public void onTemporaryError() {
        Log.e(TAG, "ERROR: Temporary Error");
    }

    @Override
    public void onConnectionMessage(String s) {
        Log.d(TAG, "Connection Message: " + s);
    }
    // END CONNECTION CALLBACK METHODS

    // Inner interface used to cast listeners for service events
    public interface PlayQueueServiceListener {

        void onQueueNext(int mCurrentPlayingIndex);

        void onQueuePause();

        void onQueuePlay();

        void onQueueUpdated();
    }

    private void notifyQueueChanged() {
        for (PlayQueueServiceListener listener : mListeners) {
            listener.onQueueUpdated();
        }
    }

    private void notifyNext() {
        for (PlayQueueServiceListener listener : mListeners) {
            listener.onQueueNext(mCurrentPlaylistIndex);
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
