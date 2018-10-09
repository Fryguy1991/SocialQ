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
import java.util.List;
import java.util.concurrent.TimeUnit;

import kaaes.spotify.webapi.android.models.Track;
import com.chrisfry.socialq.utils.ApplicationUtils;

/**
 * Service for playing the social queue with Spotify
 */
public class PlayQueueService extends Service implements ConnectionStateCallback,
        Player.NotificationCallback, Player.OperationCallback {

    // Binder given to clients
    private final IBinder mPlayQueueBinder = new PlayQueueBinder();
    // List containing the queue of songs (next to play always index 0)
    private ArrayList<Track> mSongQueue = new ArrayList<>();
    // Member player object used for playing audio
    private SpotifyPlayer mSpotifyPlayer;
    // List to contain objects listening for track changed events
    private ArrayList<TrackChangeListener> mTrackChangeListeners = new ArrayList<>();
    // List to contain objects listening for queue changed events
    private ArrayList<QueueChangeListener> mQueueChangeListeners = new ArrayList<>();

    private final Player.OperationCallback mConnectivityCallback = new Player.OperationCallback() {
        @Override
        public void onSuccess() {
            Log.d(this.getClass().getName(), "Success!");
        }

        @Override
        public void onError(Error error) {
            Log.d(this.getClass().getName(), "ERROR: " + error);
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
        String accessToken = ApplicationUtils.getAccessToken();
        Log.d(this.getClass().getName(), "Starting service");
        if (accessToken == null) {
            stopSelf();
        } else {
            Log.d(this.getClass().getName(), "Initializing player");
            initPlayer(accessToken);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        String accessToken = ApplicationUtils.getAccessToken();
        Log.d(this.getClass().getName(), "Starting service");
        if (accessToken == null) {
            stopSelf();
        } else {
            Log.d(this.getClass().getName(), "Initializing player");
            initPlayer(accessToken);
        }
        return mPlayQueueBinder;
    }

    @Override
    public void onDestroy() {
        Log.d(this.getClass().getName(), "Service is ending.  Shutting down player.");
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
                Log.e(this.getClass().getName(), "Player initialized");
                player.setConnectivityStatus(mConnectivityCallback,
                        getNetworkConnectivity(PlayQueueService.this));
                player.addConnectionStateCallback(PlayQueueService.this);
                player.addNotificationCallback(PlayQueueService.this);
            }

            @Override
            public void onError(Throwable error) {
                Log.e(this.getClass().getName(), "ERROR: Could not initialize player: " + error.getMessage());
            }
        });
    }

    public void play() {
        Log.d(this.getClass().getName(), "PLAY");
        Metadata metaData = mSpotifyPlayer.getMetadata();
        if (mSpotifyPlayer.getPlaybackState() != null) {
            if (metaData.currentTrack == null && mSongQueue.size() > 0) {
                // If track is not loaded into the player, start the first one in the list
                Log.d(this.getClass().getName(), "Starting Spotify Player?");
                mSpotifyPlayer.playUri(this, mSongQueue.get(0).uri, 0, 1);
                mSongQueue.remove(0);
                notifyQueueChanged();
            } else {
                mSpotifyPlayer.resume(this);
            }
        }
    }

    public void pause() {
        Log.d(this.getClass().getName(), "PAUSE");
        mSpotifyPlayer.pause(this);
    }

    public void playNext() {
        Log.d(this.getClass().getName(), "NEXT");
        mSpotifyPlayer.skipToNext(this);
    }

    public void addSongToQueue(Track track) {
        // If next track is null queue track to player, else add to list
        Metadata metaData = mSpotifyPlayer.getMetadata();
        if (metaData != null && metaData.currentTrack != null && metaData.nextTrack == null) {
            mSpotifyPlayer.queue(this, track.uri);
        } else {
            mSongQueue.add(track);
        }
        notifyQueueChanged();
    }

    public boolean isPlaying() {
        return mSpotifyPlayer.getPlaybackState().isPlaying;
    }

    @Override
    public void onPlaybackEvent(PlayerEvent playerEvent) {
        Log.d(this.getClass().getName(), "New playback event: " + playerEvent.name());

        switch (playerEvent) {
            case kSpPlaybackNotifyPlay:
                break;
            case kSpPlaybackNotifyContextChanged:
                break;
            case kSpPlaybackNotifyPause:
                break;
            case kSpPlaybackNotifyTrackChanged:
                // Track has changed
                // Queue next track (if available)
                if (mSongQueue.size() > 0) {
                    Log.d(this.getClass().getName(), "Queueing: " + mSongQueue.get(0).name);
                    mSpotifyPlayer.queue(this, mSongQueue.remove(0).uri);
                }
                notifyTrackChanged();
                break;
            case kSpPlaybackNotifyMetadataChanged:
                // Log current and next track
                Log.d(this.getClass().getName(), "Current track: " + mSpotifyPlayer.getMetadata().currentTrack
                        + "\n Next Track: " + mSpotifyPlayer.getMetadata().nextTrack);
                notifyQueueChanged();
                break;
            case kSpPlaybackNotifyTrackDelivered:
            case kSpPlaybackNotifyNext:
                break;
            case kSpPlaybackNotifyAudioDeliveryDone:
                for (TrackChangeListener listener : mTrackChangeListeners) {
                    listener.onTrackChanged(null);
                }

                break;
            default:
                // Do nothing or future implementation
                break;
        }
    }

    @Override
    public void onPlaybackError(Error error) {
        Log.d(this.getClass().getName(), "ERROR: New playback error: " + error.name());
    }

    @Override
    public void onSuccess() {
        Log.d(this.getClass().getName(), "Great Success!");
    }

    @Override
    public void onError(Error error) {
        Log.d(this.getClass().getName(), "ERROR: Playback error received : Error - " + error.name());
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
        Log.d(this.getClass().getName(), "Logged In!");
    }

    @Override
    public void onLoggedOut() {
        Log.d(this.getClass().getName(), "Logged Out!");
    }

    @Override
    public void onLoginFailed(Error error) {
        Log.d(this.getClass().getName(), "ERROR: Login Error: " + error.name());
    }

    @Override
    public void onTemporaryError() {
        Log.d(this.getClass().getName(), "ERROR: Temporary Error");
    }

    @Override
    public void onConnectionMessage(String s) {
        Log.d(this.getClass().getName(), "Connection Message: " + s);
    }
    // END CONNECTION CALLBACK METHODS

    // Inner interface used to cast listeners for when the track is changed
    public interface TrackChangeListener {

        void onTrackChanged(Metadata.Track track);

    }

    public void addTrackChangedListener(TrackChangeListener listener) {
        mTrackChangeListeners.add(listener);
    }

    public void remove(TrackChangeListener listener) {
        mTrackChangeListeners.remove(listener);
    }

    private void notifyTrackChanged() {
        for (TrackChangeListener listener : mTrackChangeListeners) {
            listener.onTrackChanged(mSpotifyPlayer.getMetadata().currentTrack);
        }
    }

    // Inner interface used to cast listeners for when the queue has changed
    public interface QueueChangeListener {

        void onQueueChanged(List<Track> trackQueue);

        void onQueueChanged(List<Track> trackQueue, String nextTrackUri);

    }

    private void notifyQueueChanged() {
        for (QueueChangeListener listener : mQueueChangeListeners) {
            Metadata.Track queuedTrack = mSpotifyPlayer.getMetadata().nextTrack;
            if (queuedTrack == null) {
                listener.onQueueChanged(new ArrayList<>(mSongQueue));
            } else {
                listener.onQueueChanged(new ArrayList<>(mSongQueue),
                        queuedTrack.uri.replace("spotify:track:", ""));
            }
        }
    }

    public void addQueueChangedListener(QueueChangeListener listener) {
        mQueueChangeListeners.add(listener);
    }

    public void remove(QueueChangeListener listener) {
        mQueueChangeListeners.remove(listener);
    }
}
