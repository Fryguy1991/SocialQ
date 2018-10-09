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

import com.chrisfry.socialq.business.dagger.modules.SpotifyModule;
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent;
import com.chrisfry.socialq.business.dagger.modules.components.SpotifyComponent;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.R;

import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Playlist;
import kaaes.spotify.webapi.android.models.Track;
import kaaes.spotify.webapi.android.models.TracksPager;
import kaaes.spotify.webapi.android.models.UserPrivate;

import com.chrisfry.socialq.services.PlayQueueService;
import com.chrisfry.socialq.userinterface.adapters.TrackListAdapter;
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration;
import com.chrisfry.socialq.utils.ApplicationUtils;
import com.chrisfry.socialq.utils.DisplayUtils;

public abstract class HostActivity extends Activity implements ConnectionStateCallback,
        PlayQueueService.TrackChangeListener, PlayQueueService.QueueChangeListener {
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
    private Playlist mPlaylist;
    private UserPrivate mCurrentUser;

    private List<Track> mCurrentTrackList = new ArrayList<>();

    // Object for connecting to/from play queue service
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "Service Connected");
            PlayQueueService.PlayQueueBinder binder = (PlayQueueService.PlayQueueBinder) iBinder;
            mPlayQueueService = binder.getService();

            // Setup activity for callbacks
            mPlayQueueService.addTrackChangedListener(HostActivity.this);
            mPlayQueueService.addQueueChangedListener(HostActivity.this);

            setupQueueList();
            setupDemoQueue();
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
        builder.setScopes(new String[]{"user-read-private", "streaming", "playlist-modify-private"});
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
                mPlayQueueService.playNext();
            }
        });

        mPlayPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handlePlayPause(mPlayQueueService.isPlaying());
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
                    createPlaylistForQueue();

                    // Start service that will play and control queue
                    Intent startPlayQueueIntent = new Intent(this, PlayQueueService.class);
                    startPlayQueueIntent.putExtra(AppConstants.SOCIALQ_PLAYLIST_URI_KEY, mPlaylist);
                    mIsServiceBound = bindService(startPlayQueueIntent, mServiceConnection, Context.BIND_AUTO_CREATE);


                    // All logged in and good to go.  Start host connection.
                    startHostConnection();
                }
                break;
            case AppConstants.SEARCH_REQUEST:
                if (resultCode == RESULT_OK) {
                    String trackUri = intent.getStringExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY);
                    if (trackUri != null && !trackUri.isEmpty()) {
                        mPlayQueueService.addSongToQueue(mSpotifyService.getTrack(trackUri));
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

    private void createPlaylistForQueue() {
        // Get current user
        mCurrentUser = mSpotifyService.getMe();

        // Create body parameters for new playlist
        Map<String, Object> playlistParameters = new HashMap<>();
        playlistParameters.put("name", "SocialQ Playlist");
        playlistParameters.put("public", false);
        playlistParameters.put("collaborative", false);
        playlistParameters.put("description", "Playlist created by the SocialQ App.");

        Log.d(TAG, "Creating playlist for the SocialQ");
        mPlaylist = mSpotifyService.createPlaylist(mCurrentUser.id, playlistParameters);
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
        mPlayQueueService.onDestroy();

        // Unfollow the playlist created for SocialQ
        if (mCurrentUser != null && mPlaylist != null) {
            Log.d(TAG, "Unfollowing playlist created for the SocialQ");
            mSpotifyService.unfollowPlaylist(mCurrentUser.id, mPlaylist.id);
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
                mPlayQueueService.addSongToQueue(trackToQueue);
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
            mPlayPauseButton.setText(getString(R.string.play_btn));
            mPlayQueueService.pause();
        } else {
            mPlayPauseButton.setText(getString(R.string.pause_btn));
            mPlayQueueService.play();
        }
    }

    @Override
    public void onTrackChanged(Metadata.Track track) {
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
        mQueueDisplayAdapter.updateQueueList(trackQueue);

        sendQueueToClients(trackQueue);

        mCurrentTrackList = trackQueue;
    }

    @Override
    public void onQueueChanged(List<Track> trackQueue, String nextTrackUri) {
        Track nextTrack = mSpotifyService.getTrack(nextTrackUri);
        trackQueue.add(0, nextTrack);
        mQueueDisplayAdapter.updateQueueList(trackQueue);


        sendQueueToClients(trackQueue);

        mCurrentTrackList = trackQueue;
    }

    private void setupDemoQueue() {
        TracksPager tracks = mSpotifyService.searchTracks("Built This Pool");
        mPlayQueueService.addSongToQueue(tracks.tracks.items.get(2));

        tracks = mSpotifyService.searchTracks("Audience of One");
        mPlayQueueService.addSongToQueue(tracks.tracks.items.get(0));

        tracks = mSpotifyService.searchTracks("Love Yourself Somebody");
        mPlayQueueService.addSongToQueue(tracks.tracks.items.get(0));

        tracks = mSpotifyService.searchTracks("How I Could Just Kill A Man");
        mPlayQueueService.addSongToQueue(tracks.tracks.items.get(0));
    }

    abstract void startHostConnection();

    abstract void sendQueueToClients(List<Track> queueTracks);
}