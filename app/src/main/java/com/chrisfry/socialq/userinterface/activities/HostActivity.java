package com.chrisfry.socialq.userinterface.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.chrisfry.socialq.business.dagger.modules.SpotifyModule;
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent;
import com.chrisfry.socialq.business.dagger.modules.components.SpotifyComponent;
import com.chrisfry.socialq.model.SongRequestData;
import com.chrisfry.socialq.userinterface.adapters.PlaylistTrackListAdapter;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.R;

import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Playlist;
import kaaes.spotify.webapi.android.models.PlaylistTrack;
import kaaes.spotify.webapi.android.models.UserPrivate;

import com.chrisfry.socialq.services.PlayQueueService;
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration;
import com.chrisfry.socialq.utils.ApplicationUtils;

public abstract class HostActivity extends AppCompatActivity implements ConnectionStateCallback,
        PlayQueueService.PlayQueueServiceListener {
    private final String TAG = HostActivity.class.getName();

    // UI element references
    private View mNextButton;
    private ImageView mPlayPauseButton;

    // Track list elements
    private RecyclerView mQueueList;
    private PlaylistTrackListAdapter mQueueDisplayAdapter;

    // Spotify elements
    private SpotifyService mSpotifyService;
    private PlayQueueService mPlayQueueService;
    protected UserPrivate mCurrentUser;
    protected Playlist mPlaylist;

    // Flag to determine if the service is bound or not
    private boolean mIsServiceBound = false;
    // Cached value for playing index (used to inform new clients)
    protected int mCachedPlayingIndex = -1;
    // Boolean flag to store if queue should be "fair play"
    private boolean mIsQueueFairPlay;
    // List containing client song requests
    private List<SongRequestData> mSongRequests = new ArrayList<>();

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
//            setupShortDemoQueue();
//            setupLongDemoQueue();
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

        // Set fair play flag from intent (or default to app boolean default)
        mIsQueueFairPlay = getIntent().getBooleanExtra(AppConstants.FAIR_PLAY_KEY, getResources().getBoolean(R.bool.fair_play_default));

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
        AuthenticationClient.openLoginActivity(this, AppConstants.SPOTIFY_LOGIN_REQUEST, request);
    }

    private void initUi() {
        // Initialize UI elements
        mNextButton = findViewById(R.id.btn_next);
        mPlayPauseButton = findViewById(R.id.btn_play_pause);
        mQueueList = findViewById(R.id.rv_queue_list_view);
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
            case AppConstants.SPOTIFY_LOGIN_REQUEST:
                AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
                if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                    Log.d(TAG, "Access token granted");

                    // Initialize spotify elements and create playlist for queue
                    ApplicationUtils.setAccessToken(response.getAccessToken());
                    initSpotifyElements(response.getAccessToken());

                    // Start service that will play and control queue
                    Intent startPlayQueueIntent = new Intent(this, PlayQueueService.class);
                    startPlayQueueIntent.putExtra(AppConstants.SERVICE_PLAYLIST_ID_KEY, mPlaylist.id);

                    mIsServiceBound = bindService(startPlayQueueIntent, mServiceConnection, Context.BIND_AUTO_CREATE);


                    // All logged in and good to go.  Start host connection.
                    startHostConnection(getIntent().getStringExtra(AppConstants.QUEUE_TITLE_KEY));
                } else {
                    Log.d(TAG, "Authentication Response: " + response.getError());
                    Toast.makeText(HostActivity.this, getString(R.string.toast_authentication_error_host), Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case AppConstants.SEARCH_REQUEST:
                if (resultCode == RESULT_OK) {
                    String trackUri = intent.getStringExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY);
                    handleSongRequest(new SongRequestData(trackUri, mCurrentUser.id));
                }
                break;
        }
    }

    private void initSpotifyElements(String accessToken) {
        // Setup service for searching Spotify library
        SpotifyComponent componenet = DaggerSpotifyComponent.builder().spotifyModule(
                new SpotifyModule(accessToken)).build();

        mSpotifyService = componenet.service();

        mCurrentUser = mSpotifyService.getMe();
        mPlaylist = createPlaylistForQueue();
    }

    private Playlist createPlaylistForQueue() {
        // Create body parameters for new playlist
        Map<String, Object> playlistParameters = new HashMap<>();
        playlistParameters.put("name", "SocialQ Playlist");
        playlistParameters.put("public", false);
        playlistParameters.put("collaborative", false);
        playlistParameters.put("description", "Playlist created by the SocialQ App.");

        Log.d(TAG, "Creating playlist for the SocialQ");
        return mSpotifyService.createPlaylist(mCurrentUser.id, playlistParameters);
    }

    protected void handleSongRequest(SongRequestData songRequest) {
        if (songRequest != null && !songRequest.getUri().isEmpty()) {
            Log.d(TAG, "Received request for URI: " + songRequest.getUri() + ", from User ID: " + songRequest.getUserId());

            Map<String, Object> queryParameters = new HashMap<>();
            queryParameters.put("uris", songRequest.getUri());
            Map<String, Object> bodyParameters = new HashMap<>();

            // Add song to queue playlist
            mSpotifyService.addTracksToPlaylist(mCurrentUser.id, mPlaylist.id, queryParameters, bodyParameters);
            // Add track to request list
            mSongRequests.add(songRequest);

            manageQueue();
            mPlayQueueService.notifyServiceQueueHasChanged();
        }
    }

    private void manageQueue() {
        // TODO: Manage playlist here (sorting)
    }

    private void refreshPlaylist() {
        mPlaylist = mSpotifyService.getPlaylist(mCurrentUser.id, mPlaylist.id);
    }

    private void unfollowQueuePlaylist() {
        // Unfollow the playlist created for SocialQ
        if (mCurrentUser != null && mPlaylist != null) {
            Log.d(TAG, "Unfollowing playlist created for the SocialQ");
            mSpotifyService.unfollowPlaylist(mCurrentUser.id, mPlaylist.id);
            mPlaylist = null;
        }
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
        if (mIsServiceBound) {
            unbindService(mServiceConnection);
            mIsServiceBound = false;
        }

        if (mPlayQueueService != null) {
            mPlayQueueService.removePlayQueueServiceListener(this);
            mPlayQueueService.onDestroy();
        }

        unfollowQueuePlaylist();
        super.onDestroy();
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

    private void setupQueueList() {
        mQueueDisplayAdapter = new PlaylistTrackListAdapter(new ArrayList<PlaylistTrack>());
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

    @Override
    public void onQueueChanged(int currentPlayingIndex) {
        mCachedPlayingIndex = currentPlayingIndex;

        // Refresh playlist and update UI
        refreshPlaylist();
        mQueueDisplayAdapter.updateQueueList(mPlaylist.tracks.items.subList(currentPlayingIndex, mPlaylist.tracks.items.size()));

        // Notify clients queue has been updated
        notifyClientsQueueUpdated(currentPlayingIndex);
    }

    @Override
    public void onQueuePause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mPlayPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.play_button, this.getTheme()));
        } else {
            mPlayPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.play_button));
        }
        mPlayPauseButton.setContentDescription("queue_paused");
    }

    @Override
    public void onQueuePlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mPlayPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.pause_button, this.getTheme()));
        } else {
            mPlayPauseButton.setImageDrawable(getResources().getDrawable(R.drawable.pause_button));
        }
        mPlayPauseButton.setContentDescription("queue_playing");
    }

    private void setupShortDemoQueue() {
        String longQueueString =
                "spotify:track:0p8fUOBfWtGcaKGiD9drgJ," +
                        "spotify:track:6qtg4gz3DhqOHL5BHtSQw8," +
                        "spotify:track:57bgtoPSgt236HzfBOd8kj," +
                        "spotify:track:7lGh1Dy02c5C0j3tj9AVm3";

        Map<String, Object> queryParameters = new HashMap<>();
        queryParameters.put("uris", longQueueString);
        Map<String, Object> bodyParameters = new HashMap<>();

        mSpotifyService.addTracksToPlaylist(mCurrentUser.id, mPlaylist.id, queryParameters, bodyParameters);
        mPlayQueueService.notifyServiceQueueHasChanged();
    }

    private void setupLongDemoQueue() {
        String longQueueString =
                "spotify:track:0p8fUOBfWtGcaKGiD9drgJ," +
                        "spotify:track:6qtg4gz3DhqOHL5BHtSQw8," +
                        "spotify:track:57bgtoPSgt236HzfBOd8kj," +
                        "spotify:track:4VbDJMkAX3dWNBdn3KH6Wx," +
                        "spotify:track:2jnvdMCTvtdVCci3YLqxGY," +
                        "spotify:track:419qOkEdlmbXS1GRJEMntC," +
                        "spotify:track:1jvqZQtbBGK5GJCGT615ao," +
                        "spotify:track:6cG3kY60HMcFqiZN8frkXF," +
                        "spotify:track:0dqrAmrvQ6fCGNf5T8If5A," +
                        "spotify:track:0wHNrrefyaeVewm4NxjxrX," +
                        "spotify:track:1hh4GY1zM7SUAyM3a2ziH5," +
                        "spotify:track:5Cl9GDb0AyQnppRr6q7ldb," +
                        "spotify:track:7D180Q77XAEP7atBLmMTgK," +
                        "spotify:track:2uxL6E8Yq0Psc1V9uBtC4F," +
                        "spotify:track:7lGh1Dy02c5C0j3tj9AVm3";

        Map<String, Object> queryParameters = new HashMap<>();
        queryParameters.put("uris", longQueueString);
        Map<String, Object> bodyParameters = new HashMap<>();

        mSpotifyService.addTracksToPlaylist(mCurrentUser.id, mPlaylist.id, queryParameters, bodyParameters);
        mPlayQueueService.notifyServiceQueueHasChanged();
    }

    public abstract void initiateNewClient(Object client);

    protected abstract void startHostConnection(String queueTitle);

    protected abstract void notifyClientsQueueUpdated(int currentPlayingIndex);
}