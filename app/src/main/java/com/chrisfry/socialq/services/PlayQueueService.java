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
    // List to contain objects listening for track changed events
//    private ArrayList<TrackChangeListener> mTrackChangeListeners = new ArrayList<>();
    // List to contain objects listening for queue changed events
    private ArrayList<QueueChangeListener> mQueueChangeListeners = new ArrayList<>();
    // Playlist object for the queue
    private Playlist mPlaylist;
    // Current user object
    private UserPrivate mCurrentUser;
    

    private final Player.OperationCallback mConnectivityCallback = new Player.OperationCallback() {
        @Override
        public void onSuccess() {
            Log.d(TAG, "Success!");
        }

        @Override
        public void onError(Error error) {
            Log.d(TAG, "ERROR: " + error);
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

        // Unfollow the playlist created for SocialQ
        if (mCurrentUser != null && mPlaylist != null) {
            Log.d(TAG, "Unfollowing playlist created for the SocialQ");
            mSpotifyService.unfollowPlaylist(mCurrentUser.id, mPlaylist.id);
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

    public void play() {
        Log.d(TAG, "PLAY");
        Metadata metaData = mSpotifyPlayer.getMetadata();
        if (mSpotifyPlayer.getPlaybackState() != null) {
            if (metaData.currentTrack == null && mSongQueue.size() > 0) {
                // If track is not loaded into the player, start the playlist
                Log.d(TAG, "Starting Spotify Player?");
                mSpotifyPlayer.playUri(this, mPlaylist.uri, 0, 1);
                notifyQueueChanged();
            } else {
                mSpotifyPlayer.resume(this);
            }
        }
    }

    public void pause() {
        Log.d(TAG, "PAUSE");
        mSpotifyPlayer.pause(this);
    }

    public void playNext() {
        Log.d(TAG, "NEXT");
        mSpotifyPlayer.skipToNext(this);
    }

    public void addSongToQueue(Track track) {
        if (track != null) {
            // Build parameters required for playlist add
            Map<String, Object> queryParameters = new HashMap<>();
            queryParameters.put("uris", track.uri);
            Map<String, Object> bodyParameters = new HashMap<>();

            // Queue song and add to local queue list
            mSpotifyService.addTracksToPlaylist(mCurrentUser.id, mPlaylist.id, queryParameters, bodyParameters);
            mSongQueue.add(track);

            notifyQueueChanged();
        }
    }

    public boolean isPlaying() {
        return mSpotifyPlayer.getPlaybackState().isPlaying;
    }

    @Override
    public void onPlaybackEvent(PlayerEvent playerEvent) {
        Log.d(TAG, "New playback event: " + playerEvent.name());

        switch (playerEvent) {
            case kSpPlaybackNotifyPlay:
                break;
            case kSpPlaybackNotifyContextChanged:
                break;
            case kSpPlaybackNotifyPause:
                break;
            case kSpPlaybackNotifyTrackChanged:
                break;
            case kSpPlaybackNotifyMetadataChanged:
                // Log current and next track
                Log.d(TAG, "Current track: " + mSpotifyPlayer.getMetadata().currentTrack
                        + "\n Next Track: " + mSpotifyPlayer.getMetadata().nextTrack);
                break;
            case kSpPlaybackNotifyTrackDelivered:
            case kSpPlaybackNotifyNext:
                // Track has changed, remove top track from queue list
                if (mSongQueue.size() > 0) {
                    mSongQueue.remove(0);
                }
                notifyQueueChanged();
                break;
            case kSpPlaybackNotifyAudioDeliveryDone:
                break;
            default:
                // Do nothing or future implementation
                break;
        }
    }

    @Override
    public void onPlaybackError(Error error) {
        Log.d(TAG, "ERROR: New playback error: " + error.name());
    }

    @Override
    public void onSuccess() {
        Log.d(TAG, "Great Success!");
    }

    @Override
    public void onError(Error error) {
        Log.d(TAG, "ERROR: Playback error received : Error - " + error.name());
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
        Log.d(TAG, "ERROR: Login Error: " + error.name());
    }

    @Override
    public void onTemporaryError() {
        Log.d(TAG, "ERROR: Temporary Error");
    }

    @Override
    public void onConnectionMessage(String s) {
        Log.d(TAG, "Connection Message: " + s);
    }
    // END CONNECTION CALLBACK METHODS

    // Inner interface used to cast listeners for when the queue has changed
    public interface QueueChangeListener {

        void onQueueChanged(List<Track> trackQueue);

    }

    private void notifyQueueChanged() {
        for (QueueChangeListener listener : mQueueChangeListeners) {
            listener.onQueueChanged(new ArrayList<>(mSongQueue));
        }
    }

    public void addQueueChangedListener(QueueChangeListener listener) {
        mQueueChangeListeners.add(listener);
    }

    public void removeQueueChangeListener(QueueChangeListener listener) {
        mQueueChangeListeners.remove(listener);
    }
}
