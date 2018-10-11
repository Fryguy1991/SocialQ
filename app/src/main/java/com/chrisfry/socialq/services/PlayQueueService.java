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
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Connectivity;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Metadata;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Playlist;
import kaaes.spotify.webapi.android.models.Track;
import kaaes.spotify.webapi.android.models.UserPrivate;

import com.chrisfry.socialq.utils.ApplicationUtils;

/**
 * Service for playing the social queue with Spotify
 */
public class PlayQueueService extends Service implements ConnectionStateCallback,
        Player.NotificationCallback, Player.OperationCallback {
    private static final String TAG = PlayQueueService.class.getName();

    // Binder given to clients
    private final IBinder mPlayQueueBinder = new PlayQueueBinder();
    // List containing the queue of songs (next to play always index 0)
    private ArrayList<Track> mSongQueue = new ArrayList<>();
    // Member player object used for playing audio
    private SpotifyPlayer mSpotifyPlayer;
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
    public IBinder onBind(Intent intent) {
        String accessToken = ApplicationUtils.getAccessToken();
        Log.d(TAG, "onBind: Starting service");
        if (accessToken == null) {
            stopSelf();
        } else {
            Log.d(TAG, "onBind: Initializing player");
            initPlayer(accessToken);
            initSpotifyServiceElements(accessToken);
        }
        return mPlayQueueBinder;
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

        unfollowQueuePlaylist();

        super.onDestroy();
    }

    private void unfollowQueuePlaylist() {
        // Unfollow the playlist created for SocialQ
        if (mCurrentUser != null && mPlaylist != null) {
            Log.d(TAG, "Unfollowing playlist created for the SocialQ");
            mSpotifyService.unfollowPlaylist(mCurrentUser.id, mPlaylist.id);
            mPlaylist = null;
        }
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

    private void initSpotifyServiceElements(String accessToken) {
        Log.d(TAG, "Initializing Spotify elements");

        // Setup service for searching Spotify library
        SpotifyComponent componenet = DaggerSpotifyComponent.builder().spotifyModule(
                new SpotifyModule(accessToken)).build();

        mSpotifyService = componenet.service();
        
        mCurrentUser = mSpotifyService.getMe();
        mPlaylist = createPlaylistForQueue();
    }

    private Playlist createPlaylistForQueue() {
        // Get current user
        mCurrentUser = mSpotifyService.getMe();

        // Create body parameters for new playlist
        Map<String, Object> playlistParameters = new HashMap<>();
        playlistParameters.put("name", "SocialQ Playlist");
        playlistParameters.put("public", false);
        playlistParameters.put("collaborative", false);
        playlistParameters.put("description", "Playlist created by the SocialQ App.");

        Log.d(TAG, "Creating playlist for the SocialQ");
        return mSpotifyService.createPlaylist(mCurrentUser.id, playlistParameters);
    }

    public void requestPlay() {
        Log.d(TAG, "PLAY REQUEST");
        if (mSpotifyPlayer.getPlaybackState() != null) {
             if (mAudioDeliveryDoneFlag) {
                 if (mCurrentPlaylistIndex < mPlaylist.tracks.items.size()) {
                     // TODO: Not a good check above?
                     // If audio has previously been completed (or never started)
                     // start the playlist at the current index
                     Log.d(TAG, "Starting playlist from index: " + mCurrentPlaylistIndex);
                     mSpotifyPlayer.playUri(this, mPlaylist.uri, mCurrentPlaylistIndex, 1);
                     mAudioDeliveryDoneFlag = false;

                     // TODO: Remove call below once we're sending the queue to clients on connection
                     notifyQueueChanged();
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
        mSpotifyPlayer.pause(this);
    }

    public void requestPlayNext() {
        Log.d(TAG, "NEXT REQUEST");
        if (!mAudioDeliveryDoneFlag) {
            // Don't allow a skip when we're waiting on a new track to be queued
            Metadata metaData = mSpotifyPlayer.getMetadata();
            if (metaData != null) {
                if (metaData.nextTrack == null
                        && mCurrentPlaylistIndex + 1 < mPlaylist.tracks.items.size()
                        && mSongQueue.size() > 1) {
                    // MetaData hasn't updated but there is a new track in the playlist
                    // play from the next index, and update the queue
                    Log.d(TAG, "MetaData is null but there is a track to play");
                    mCurrentPlaylistIndex++;
                    mSongQueue.remove(0);
                    Log.d(TAG, "Starting playlist from index: " + mCurrentPlaylistIndex);
                    mSpotifyPlayer.playUri(this, mPlaylist.uri, mCurrentPlaylistIndex, 1);
                    notifyQueueChanged();
                } else {
                    mSpotifyPlayer.skipToNext(this);
                }
            }
        } else {
            Log.d(TAG, "Can't go to next track");
        }
    }

    public void requestAddSongToQueue(Track track) {
        Log.d(TAG, "QUEUE REQUEST");
        if (track != null) {
            // Build parameters required for playlist add
            Map<String, Object> queryParameters = new HashMap<>();
            queryParameters.put("uris", track.uri);
            Map<String, Object> bodyParameters = new HashMap<>();

            // Queue song and add to local queue list
            mSpotifyService.addTracksToPlaylist(mCurrentUser.id, mPlaylist.id, queryParameters, bodyParameters);
            mSongQueue.add(track);
            // Update local copy of playlist object
            mPlaylist = mSpotifyService.getPlaylist(mCurrentUser.id, mPlaylist.id);

            notifyQueueChanged();
        } else {
            Log.d(TAG, "Invalid track to queue");
        }
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
                notifyPaused();
                break;
            case kSpPlaybackNotifyTrackChanged:
                break;
            case kSpPlaybackNotifyMetadataChanged:
                break;
            case kSpPlaybackNotifyTrackDelivered:
            case kSpPlaybackNotifyNext:
                // Track has changed, remove top track from queue list
                Log.d(TAG, "Player has moved to next track (next/track complete)");
                if (mSongQueue.size() > 0) {
                    mSongQueue.remove(0);
                }
                mCurrentPlaylistIndex++;
                notifyQueueChanged();
                break;
            case kSpPlaybackNotifyAudioDeliveryDone:
                // Current queue playlist has finished.
                Log.d(TAG, "Player has finished playing audio");
                mAudioDeliveryDoneFlag = true;
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

        void onQueueChanged(List<Track> trackQueue);

        void onQueuePause();

        void onQueuePlay();
    }

    private void notifyQueueChanged() {
        for (PlayQueueServiceListener listener : mListeners) {
            listener.onQueueChanged(new ArrayList<>(mSongQueue));
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
}
