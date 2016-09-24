package services;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.PlayerStateCallback;
import com.spotify.sdk.android.player.Spotify;

import java.util.ArrayList;

import chrisfry.spotifydj.R;
import kaaes.spotify.webapi.android.models.Track;

/**
 * Service for playing the social queue with Spotify
 */
public class PlayQueueService extends Service implements PlayerNotificationCallback,
        PlayerStateCallback, IPlayQueueService {

    // Binder given to clients
    private final IBinder mPlayQueueBinder = new PlayQueueBinder();

    // Client ID specific to this application
    private static final String CLIENT_ID = "0fab62a3895a4fa3aae14bc3e46bc59c";

    private ArrayList<Track> mSongQueue = new ArrayList<>();

    private Player mSpotifyPlayer;
    private PlayerState mPlayerState;

    private Boolean mIsPlaying;

    private String TRACK_TO_PLAY_FORMATTER;

    public class PlayQueueBinder extends Binder {
        public PlayQueueService getService() {
            // Return this instance of LocalService so clients can call public methods
            return PlayQueueService.this;
        }
    }

    @Override
    public void play() {
        if (mPlayerState != null) {
            if (!mPlayerState.playing && mPlayerState.durationInMs != 0) {
                // Player is paused and loaded with a track
                mSpotifyPlayer.resume();
            } else {
                // Player not loaded with track
                playNextInQueue();
            }
        }
    }

    @Override
    public void pause() {
        mSpotifyPlayer.pause();
    }

    @Override
    public void playNextInQueue() {
        if (mSongQueue.size() > 0) {
            // Load next song in queue into player, remove from queue
            mSpotifyPlayer.play(String.format(TRACK_TO_PLAY_FORMATTER, mSongQueue.get(0).id));
            mSongQueue.remove(0);
        }
    }

    @Override
    public void addSongToQueue(Track track) {
        mSongQueue.add(track);
    }

    @Override
    public boolean isPlaying() {
        if (!(mPlayerState == null)) {
            return mPlayerState.playing;
        }
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        TRACK_TO_PLAY_FORMATTER = getString(R.string.track_to_play_format);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle extras = intent.getExtras();
        String accessToken = extras.getString(getString(R.string.access_token_extra));
        Log.d("PlayQueueService", "Starting service");
        if (accessToken == null) {
            stopSelf();
        } else {
            Log.d("PlayQueueService", "Initializing player");
            initPlayer(accessToken);
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Bundle extras = intent.getExtras();
        String accessToken = extras.getString(getString(R.string.access_token_extra));
        Log.d("PlayQueueService", "Starting service");
        if (accessToken == null) {
            stopSelf();
        } else {
            Log.d("PlayQueueService", "Initializing player");
            initPlayer(accessToken);
        }
        return mPlayQueueBinder;
    }

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState playerState) {
        //Song has ended play next song
        if (eventType == EventType.END_OF_CONTEXT) {
            playNextInQueue();
        }
        Log.d("PlayQueueService", "Playback event received: " + eventType.name());
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String s) {

    }

    @Override
    public void onPlayerState(PlayerState playerState) {
        mPlayerState = playerState;
    }

    private void initPlayer(String accessToken) {
        // Setup Spotify player
        Config playerConfig = new Config(this, accessToken, CLIENT_ID);
        mSpotifyPlayer = Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {
            @Override
            public void onInitialized(Player player) {
                Log.e("PlayQueueService", "Player initialized");
                mSpotifyPlayer = player;
                mSpotifyPlayer.addPlayerNotificationCallback(PlayQueueService.this);
                getPlayerStatus.start();
            }

            @Override
            public void onError(Throwable throwable) {
                Log.e("PlayQueueService", "Could not initialize player: " + throwable.getMessage());
            }
        });
    }

    // Thread for requesting player status
    // TODO: Most likely not safe, investigate
    Thread getPlayerStatus = new Thread(new Runnable() {
        @Override
        public void run() {
            while (1 > 0) {
                if (mSpotifyPlayer != null) {
                    mSpotifyPlayer.getPlayerState(PlayQueueService.this);
                    try { Thread.sleep(1000); }
                    catch (InterruptedException e) {

                    }
                }
            }
        }
    });
}
