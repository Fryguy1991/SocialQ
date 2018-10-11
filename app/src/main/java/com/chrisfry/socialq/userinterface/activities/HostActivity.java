package com.chrisfry.socialq.userinterface.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.chrisfry.socialq.business.dagger.modules.SpotifyModule;
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent;
import com.chrisfry.socialq.business.dagger.modules.components.SpotifyComponent;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;

import java.util.ArrayList;
import java.util.List;

import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.R;

import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Track;
import kaaes.spotify.webapi.android.models.TracksPager;

import com.chrisfry.socialq.services.PlayQueueService;
import com.chrisfry.socialq.userinterface.adapters.TrackListAdapter;
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration;
import com.chrisfry.socialq.utils.ApplicationUtils;
import com.chrisfry.socialq.utils.DisplayUtils;

public abstract class HostActivity extends Activity implements ConnectionStateCallback,
        PlayQueueService.PlayQueueServiceListener {
    private final String TAG = HostActivity.class.getName();

    // Request code that will be used to verify if the result comes from correct activity
    // Can be any integer
    private static final int SPOTIFY_LOGIN_REQUEST = 8675309;

    // UI element references
    private Button mNextButton;
    private Button mPlayPauseButton;
    private TextView mCurrentTrackName;
    private TextView mCurrentArtistName;

    // Track list elements
    private RecyclerView mQueueList;
    private TrackListAdapter mQueueDisplayAdapter;

    // Spotify elements
    private SpotifyService mSpotifyService;
    private PlayQueueService mPlayQueueService;
    private boolean mIsServiceBound = false;

    // Object for connecting to/from play queue service
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "Service Connected");
            PlayQueueService.PlayQueueBinder binder = (PlayQueueService.PlayQueueBinder) iBinder;
            mPlayQueueService = binder.getService();

            // Setup activity for callbacks
            mPlayQueueService.addPlayQueueServiceListener(HostActivity.this);

            setupQueueList();
//            setupDemoQueue();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mPlayQueueService = null;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.host_screen);

        initUi();
        addListeners();

        // Allow network operation in main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Building and sending login request through Spotify built activity
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(
                AppConstants.CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                AppConstants.REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming", "playlist-modify-private", "app-remote-control"});
        AuthenticationRequest request = builder.build();
        AuthenticationClient.openLoginActivity(this, SPOTIFY_LOGIN_REQUEST, request);
    }

    private void initUi() {
        // Initialize UI elements
        mNextButton = (Button) findViewById(R.id.btn_next);
        mPlayPauseButton = (Button) findViewById(R.id.btn_play_pause);
        mQueueList = (RecyclerView) findViewById(R.id.rv_queue_list_view);
        mCurrentTrackName = (TextView) findViewById(R.id.tv_current_track_name);
        mCurrentArtistName = (TextView) findViewById(R.id.tv_current_artist_name);
    }

    private void addListeners() {
        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayQueueService.requestPlayNext();
            }
        });

        mPlayPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handlePlayPause(view.getContentDescription().equals("queue_playing"));
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        switch (requestCode) {
            case SPOTIFY_LOGIN_REQUEST:
                AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
                if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                    Log.d(TAG, "Access token granted");

                    // Initialize spotify elements and create playlist for queue
                    ApplicationUtils.setAccessToken(response.getAccessToken());
                    initSpotifyElements(response.getAccessToken());

                    // Start service that will play and control queue
                    Intent startPlayQueueIntent = new Intent(this, PlayQueueService.class);
                    mIsServiceBound = bindService(startPlayQueueIntent, mServiceConnection, Context.BIND_AUTO_CREATE);


                    // All logged in and good to go.  Start host connection.
                    startHostConnection();
                } else {
                    Log.d(TAG, "Authentication Response: " + response.getError());
                    Toast.makeText(HostActivity.this, getString(R.string.toast_authentication_error_host), Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case AppConstants.SEARCH_REQUEST:
                if (resultCode == RESULT_OK) {
                    String trackUri = intent.getStringExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY);
                    if (trackUri != null && !trackUri.isEmpty()) {
                        mPlayQueueService.requestAddSongToQueue(mSpotifyService.getTrack(trackUri));
                    }
                }
                break;
        }
    }

    private void initSpotifyElements(String accessToken) {
        // Setup service for searching Spotify library
        SpotifyComponent componenet = DaggerSpotifyComponent.builder().spotifyModule(
                new SpotifyModule(accessToken)).build();

        mSpotifyService = componenet.service();
    }

    @Override
    public void onLoggedIn() {
        Log.d(TAG, "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d(TAG, "User logged out");
    }

    @Override
    public void onLoginFailed(Error error) {
        Log.d(TAG, "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d(TAG, "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d(TAG, "Received connection message: " + message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mIsServiceBound) {
            unbindService(mServiceConnection);
            mIsServiceBound = false;
        }

        if (mPlayQueueService != null) {
            mPlayQueueService.removePlayQueueServiceListener(this);
            mPlayQueueService.onDestroy();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_screen_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.search_action:
                Intent searchIntent = new Intent(this, SearchActivity.class);
                startActivityForResult(searchIntent, AppConstants.SEARCH_REQUEST);
                return true;
            default:
                // Do nothing
                return false;
        }
    }

    protected void handleClientQueueRequest(String trackUri) {
        if (trackUri != null && mSpotifyService != null) {
            Track trackToQueue = mSpotifyService.getTrack(trackUri);
            if (trackToQueue != null) {
                mPlayQueueService.requestAddSongToQueue(trackToQueue);
            }
        }
    }

    private void setupQueueList() {
        mQueueDisplayAdapter = new TrackListAdapter(new ArrayList<Track>());
        mQueueList.setAdapter(mQueueDisplayAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mQueueList.setLayoutManager(layoutManager);
        mQueueList.addItemDecoration(new QueueItemDecoration(getApplicationContext()));
    }

    private void handlePlayPause(boolean isPlaying) {
        if (isPlaying) {
            mPlayQueueService.requestPause();
        } else {
            mPlayQueueService.requestPlay();
        }
    }

    private void setPlayingTrack(Track track) {
        if (track != null) {
            mCurrentTrackName.setText(track.name);
            mCurrentArtistName.setText(DisplayUtils.getTrackArtistString(track));
        } else {
            mCurrentTrackName.setText("");
            mCurrentArtistName.setText("");
        }
    }

    @Override
    public void onQueueChanged(List<Track> trackQueue) {
        // Send entire queue to clients
        sendQueueToClients(trackQueue);

        // Display all but first (current track updated separately)
        if (trackQueue.size() == 0) {
            setPlayingTrack(null);
        } else {
            setPlayingTrack(trackQueue.remove(0));
        }
        mQueueDisplayAdapter.updateQueueList(trackQueue);
    }

    protected void requestQueueForOneClient(Object client) {
        mPlayQueueService.requestSendQueueToClient(client, this);
    }


    @Override
    public void onQueuePause() {
        mPlayPauseButton.setText(R.string.play_btn);
        mPlayPauseButton.setContentDescription("queue_paused");
    }

    @Override
    public void onQueuePlay() {
        mPlayPauseButton.setText(R.string.pause_btn);
        mPlayPauseButton.setContentDescription("queue_playing");
    }


    private void setupDemoQueue() {
        TracksPager tracks = mSpotifyService.searchTracks("Built This Pool");
        mPlayQueueService.requestAddSongToQueue(tracks.tracks.items.get(2));

        tracks = mSpotifyService.searchTracks("Audience of One");
        mPlayQueueService.requestAddSongToQueue(tracks.tracks.items.get(0));

        tracks = mSpotifyService.searchTracks("Love Yourself Somebody");
        mPlayQueueService.requestAddSongToQueue(tracks.tracks.items.get(0));
    }

    @Override
    public abstract void receiveQueueForClient(Object client, List<Track> songQueue);

    protected abstract void startHostConnection();

    protected abstract void sendQueueToClients(List<Track> queueTracks);
}