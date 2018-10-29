package com.chrisfry.socialq.userinterface.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
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

import kaaes.spotify.webapi.android.SpotifyApi;
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
    private SpotifyApi mSpotifyApi;
    private SpotifyService mSpotifyService;
    private PlayQueueService mPlayQueueService;
    protected UserPrivate mCurrentUser;
    protected Playlist mPlaylist;

    // Flag to determine if the service is bound or not
    private boolean mIsServiceBound = false;
    // Cached value for playing index (used to inform new clients)
    protected int mCachedPlayingIndex = 0;
    // Boolean flag to store if queue should be "fair play"
    private boolean mIsQueueFairPlay;
    // List containing client song requests
    private List<SongRequestData> mSongRequests = new ArrayList<>();
    // Time for when access token expires
    private long mSystemAccessExpireTime = -1;

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

    // Handler for sending messages to the UI thread
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AppConstants.ACCESS_TOKEN_REFRESH:
                    // Don't request access tokens if activity is being shut down
                    if (!isFinishing()) {
                        Log.d(TAG, "Requesting new access token on UI thread");
                        requestNewAccessToken();
                    }
                    break;
            }
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

        // Request access token from Spotify
        requestNewAccessToken();
    }


    /**
     * Use Spotify login activity to retrieve an access token
     */
    private void requestNewAccessToken() {
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(
                AppConstants.CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                AppConstants.REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming", "playlist-modify-private", "app-remote-control"});
        AuthenticationRequest request = builder.build();
        AuthenticationClient.openLoginActivity(this, AppConstants.SPOTIFY_AUTHENTICATION_REQUEST, request);
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
            case AppConstants.SPOTIFY_AUTHENTICATION_REQUEST:
                AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
                if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                    Log.d(TAG, "Access token granted");

                    // Store when access token expires (response "ExpiresIn" is in seconds, subtract a minute to worry less about timing)
                    mSystemAccessExpireTime = System.currentTimeMillis() + (response.getExpiresIn() - 60) * 1000;

                    ApplicationUtils.setAccessToken(response.getAccessToken());

                    // Start thread responsible for notifying UI thread when new access token is needed
                    new AccessRefreshThread().start();

                    if (mPlayQueueService == null) {
                        Log.d(TAG, "First access token granted.  Init Spotify elements, play queue service, and start host connection");
                        // Initialize spotify elements and create playlist for queue
                        initSpotifyElements(response.getAccessToken());

                        // Start service that will play and control queue
                        Intent startPlayQueueIntent = new Intent(this, PlayQueueService.class);
                        startPlayQueueIntent.putExtra(AppConstants.SERVICE_PLAYLIST_ID_KEY, mPlaylist.id);

                        mIsServiceBound = bindService(startPlayQueueIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

                        // All logged in and good to go.  Start host connection.
                        startHostConnection(getIntent().getStringExtra(AppConstants.QUEUE_TITLE_KEY));
                    } else {
                        Log.d(TAG, "New access token granted.  Update Spotify Api and service");
                        mSpotifyApi.setAccessToken(response.getAccessToken());
                        mSpotifyService = mSpotifyApi.getService();
                    }
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

        mSpotifyApi = componenet.api();
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

            // Add track to request list
            mSongRequests.add(songRequest);

            // Don't need to worry about managing the queue if fairplay is off
            if (mIsQueueFairPlay) {
                if (injectNewTrack(songRequest)) {
                    // If we changed the next track notify service meta data is out of sync
                    mPlayQueueService.notifyServiceMetaDataIsStale();
                }
            } else {
                addTrackToPlaylist(songRequest.getUri());
                if (mSongRequests.size() == 2) {
                    // If we changed the next track notify service meta data is out of sync
                    mPlayQueueService.notifyServiceMetaDataIsStale();
                } else {
                    mPlayQueueService.notifyServiceQueueHasChanged();
                }
            }
        }
    }

    /**
     * Adds a song to end of the referenced playlist
     *
     * @param uri - uri of the track to be added
     */
    private void addTrackToPlaylist(String uri) {
        addTrackToPlaylistPosition(uri, -1);
    }

    /**
     * Adds a song to the referenced playlist at the given position (or end if not specified)
     *
     * @param uri - uri of the track to be added
     * @param position - position of the track to be added (if less than 0, track placed at end of playlist)
     */
    private void addTrackToPlaylistPosition(String uri, int position) {
        Map<String, Object> queryParameters = new HashMap<>();
        queryParameters.put("uris", uri);
        if (position >= 0) {
            queryParameters.put("position", position);
        }
        Map<String, Object> bodyParameters = new HashMap<>();

        // Add song to queue playlist
        mSpotifyService.addTracksToPlaylist(mCurrentUser.id, mPlaylist.id, queryParameters, bodyParameters);
    }

    /**
     * Injects most recently added track to fairplay position
     *
     * @param songRequest - Song request containing requestee and track info
     * @return - Boolean flag for if the track was added at the next position
     */
    private boolean injectNewTrack(SongRequestData songRequest) {
        // Position of new track needs to go before first repeat that doesn't have a song of the requestee inside
        // EX (Requestee = 3): 1 -> 2 -> 3 -> 1 -> 2 -> 1  New 3 track goes before 3rd track by 1
        // PLAYLIST RESULT   : 1 -> 2 -> 3 -> 1 -> 2 -> 3 -> 1
        int newTrackPosition;

        // Only run check for song injection if there are 3 or more requests tracked
        if (mSongRequests.size() > 2) {
            HashMap<String, Boolean> clientRepeatHash = new HashMap<>();

            // Start inspecting song requests
            for (newTrackPosition = 0; newTrackPosition < mSongRequests.size(); newTrackPosition++) {
                String currentRequestUserId = mSongRequests.get(newTrackPosition).getUserId();

                if (currentRequestUserId.equals(songRequest.getUserId())) {
                    // If we found a requestee track set open repeats to true (found requestee track)
                    for (Map.Entry<String, Boolean> mapEntry : clientRepeatHash.entrySet()) {
                        mapEntry.setValue(true);
                    }
                } else {
                    // Found a request NOT from the requestee client
                    if (clientRepeatHash.containsKey(currentRequestUserId)) {
                        // Client already contained in hash (repeat)
                        if (clientRepeatHash.get(currentRequestUserId)) {
                            // If repeat contained requestee track (true flag) reset to false
                            clientRepeatHash.put(currentRequestUserId, false);
                        } else {
                            // Client already contained in hash (repeat) and does not have a requestee track
                            // We have a repeat with no requestee song in between
                            break;
                        }
                    } else {
                        // Add new client to the hash
                        clientRepeatHash.put(currentRequestUserId, false);
                    }
                }
            }
        } else {
            // If not enough requests set new track position so new track will be placed at the end of the list
            newTrackPosition = mSongRequests.size();
        }

        if (newTrackPosition == mSongRequests.size()) {
            // No repeat found (or list too small) add track to end of playlist
            Log.d(TAG, "Adding track to end of playlist");
            addTrackToPlaylist(songRequest.getUri());
            // Return true if the song being added is next (request size of 2)
            return mSongRequests.size() == 1 || mSongRequests.size() == 2;
        } else if (newTrackPosition > mSongRequests.size()) {
            // Should not be possible
            Log.e(TAG, "INVALID NEW TRACK POSITION INDEX");
            return false;
        } else {
            // If new track position is not equal or greater than song request size we need to move it
            // Inject song request data to new position
            mSongRequests.add(newTrackPosition, mSongRequests.remove(mSongRequests.size() - 1));

            Log.d(TAG, "Adding new track at playlist index: " + (newTrackPosition + mCachedPlayingIndex));
            addTrackToPlaylistPosition(songRequest.getUri(), newTrackPosition + mCachedPlayingIndex);

            // Return true if we're moving the added track to the "next" position
            return newTrackPosition == 1;
        }
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

        // This should trigger access request thread to end if it is running
        mSystemAccessExpireTime = -1;
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
    public void onQueueNext(int currentPlayingIndex) {
        if (mSongRequests.size() > 0) {
            mSongRequests.remove(0);
        }

        mCachedPlayingIndex = currentPlayingIndex;

        // Refresh playlist and update UI
        refreshPlaylist();
        if (currentPlayingIndex >= mPlaylist.tracks.items.size()) {
            mQueueDisplayAdapter.updateQueueList(new ArrayList<PlaylistTrack>());
        } else {
            mQueueDisplayAdapter.updateQueueList(mPlaylist.tracks.items.subList(currentPlayingIndex, mPlaylist.tracks.items.size()));
        }

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

    @Override
    public void onQueueUpdated() {
        // Refresh playlist and update UI
        refreshPlaylist();
        mQueueDisplayAdapter.updateQueueList(mPlaylist.tracks.items.subList(mCachedPlayingIndex, mPlaylist.tracks.items.size()));

        notifyClientsQueueUpdated(mCachedPlayingIndex);
    }


    /**
     * Inner thread class used to detect when a new access code is needed and send message to handler to request a new one.
     */
    private class AccessRefreshThread extends Thread {
        AccessRefreshThread() {
            super(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        if (System.currentTimeMillis() >= mSystemAccessExpireTime) {
                            Log.d(TAG, "Detected that we need a new access token");
                            Message message = new Message();
                            message.what = AppConstants.ACCESS_TOKEN_REFRESH;
                            mHandler.dispatchMessage(message);
                            break;
                        }
                    }
                }
            });
        }
    }

    private void setupShortDemoQueue() {
        String shortQueueString =
                "spotify:track:0p8fUOBfWtGcaKGiD9drgJ," +
                        "spotify:track:6qtg4gz3DhqOHL5BHtSQw8," +
                        "spotify:track:57bgtoPSgt236HzfBOd8kj," +
                        "spotify:track:7lGh1Dy02c5C0j3tj9AVm3";


        Map<String, Object> queryParameters = new HashMap<>();
        queryParameters.put("uris", shortQueueString);
        Map<String, Object> bodyParameters = new HashMap<>();

        mSpotifyService.addTracksToPlaylist(mCurrentUser.id, mPlaylist.id, queryParameters, bodyParameters);
        mPlayQueueService.notifyServiceQueueHasChanged();

        // String for testing fair play (simulate a user name)
        String userId = "fake_user";
//        String userId = mCurrentUser.id;

        mSongRequests.add(new SongRequestData("spotify:track:0p8fUOBfWtGcaKGiD9drgJ", userId));
        mSongRequests.add(new SongRequestData("spotify:track:6qtg4gz3DhqOHL5BHtSQw8", userId));
        mSongRequests.add(new SongRequestData("spotify:track:57bgtoPSgt236HzfBOd8kj", userId));
        mSongRequests.add(new SongRequestData("spotify:track:7lGh1Dy02c5C0j3tj9AVm3", userId));
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

        // String for testing fair play (simulate a user name)
        String userId = "fake_user";
//        String userId = mCurrentUser.id;

        mSongRequests.add(new SongRequestData("spotify:track:0p8fUOBfWtGcaKGiD9drgJ", userId));
        mSongRequests.add(new SongRequestData("spotify:track:6qtg4gz3DhqOHL5BHtSQw8", userId));
        mSongRequests.add(new SongRequestData("spotify:track:57bgtoPSgt236HzfBOd8kj", userId));
        mSongRequests.add(new SongRequestData("spotify:track:4VbDJMkAX3dWNBdn3KH6Wx", userId));
        mSongRequests.add(new SongRequestData("spotify:track:2jnvdMCTvtdVCci3YLqxGY", userId));
        mSongRequests.add(new SongRequestData("spotify:track:419qOkEdlmbXS1GRJEMntC", userId));
        mSongRequests.add(new SongRequestData("spotify:track:1jvqZQtbBGK5GJCGT615ao", userId));
        mSongRequests.add(new SongRequestData("spotify:track:6cG3kY60HMcFqiZN8frkXF", userId));
        mSongRequests.add(new SongRequestData("spotify:track:0dqrAmrvQ6fCGNf5T8If5A", userId));
        mSongRequests.add(new SongRequestData("spotify:track:0wHNrrefyaeVewm4NxjxrX", userId));
        mSongRequests.add(new SongRequestData("spotify:track:1hh4GY1zM7SUAyM3a2ziH5", userId));
        mSongRequests.add(new SongRequestData("spotify:track:5Cl9GDb0AyQnppRr6q7ldb", userId));
        mSongRequests.add(new SongRequestData("spotify:track:7D180Q77XAEP7atBLmMTgK", userId));
        mSongRequests.add(new SongRequestData("spotify:track:2uxL6E8Yq0Psc1V9uBtC4F", userId));
        mSongRequests.add(new SongRequestData("spotify:track:7lGh1Dy02c5C0j3tj9AVm3", userId));
    }

    public abstract void initiateNewClient(Object client);

    protected abstract void startHostConnection(String queueTitle);

    protected abstract void notifyClientsQueueUpdated(int currentPlayingIndex);
}