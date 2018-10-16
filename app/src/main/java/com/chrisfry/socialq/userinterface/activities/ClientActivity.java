package com.chrisfry.socialq.userinterface.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.chrisfry.socialq.R;
import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.business.dagger.modules.SpotifyModule;
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent;
import com.chrisfry.socialq.business.dagger.modules.components.SpotifyComponent;
import com.chrisfry.socialq.userinterface.adapters.PlaylistTrackListAdapter;
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration;
import com.chrisfry.socialq.utils.ApplicationUtils;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Playlist;
import kaaes.spotify.webapi.android.models.PlaylistTrack;
import kaaes.spotify.webapi.android.models.Track;

public abstract class ClientActivity extends Activity implements ConnectionStateCallback {
    private final String TAG = ClientActivity.class.getName();

    // Elements for queue display
    private RecyclerView mQueueList;
    private PlaylistTrackListAdapter mQueueDisplayAdapter;

    // Spotify API elements
    private SpotifyService mSpotifyService;
    private Playlist mPlaylist;
    protected String mHostUserId;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.client_screen);

        // Allow network operation in main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Building and sending login request through Spotify built activity
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(
                AppConstants.CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                AppConstants.REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "playlist-modify-private", "playlist-modify-public", "playlist-read-collaborative"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, AppConstants.SPOTIFY_LOGIN_REQUEST, request);

        initUi();
        setupQueueList();
    }

    private void initUi() {
        // Initialize UI elements
        mQueueList = (RecyclerView) findViewById(R.id.rv_queue_list_view);
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
                    // Start service that will play and control queue
                    ApplicationUtils.setAccessToken(response.getAccessToken());
                    initSpotifySearchElements(response.getAccessToken());
                    connectToHost();
                } else {
                    Log.d(TAG, "Authentication Response: " + response.getError());
                    Toast.makeText(ClientActivity.this, getString(R.string.toast_authentication_error_client), Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case AppConstants.SEARCH_REQUEST:
                if (resultCode == RESULT_OK) {
                    String trackUri = intent.getStringExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY);
                    if (trackUri != null && !trackUri.isEmpty()) {
                        Log.d(TAG, "Client adding track to queue playlist");
                        addSongToQueue(mSpotifyService.getTrack(trackUri));
                        notifyHostTrackAdded();
                    }
                }
                break;
            default:
                // Do nothing
        }
    }

    private void initSpotifySearchElements(String accessToken) {
        // Setup Spotify service
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

    protected void updateQueue(int currentPlayingIndex) {
        if (currentPlayingIndex > 0) {
            refreshPlaylist();
            mQueueDisplayAdapter.updateQueueList(mPlaylist.tracks.items.subList(currentPlayingIndex, mPlaylist.tracks.items.size()));
        }
    }

    protected void setupQueuePlaylistOnConnection(String playlistId) {
        mSpotifyService.followPlaylist(mHostUserId, playlistId);
        mPlaylist = mSpotifyService.getPlaylist(mHostUserId, playlistId);
    }

    private void refreshPlaylist() {
        mPlaylist = mSpotifyService.getPlaylist(mHostUserId, mPlaylist.id);
    }

    private void unfollowQueuePlaylist() {
        // Unfollow the playlist created for SocialQ
        if (mHostUserId != null && mPlaylist != null) {
            Log.d(TAG, "Unfollowing playlist created for the SocialQ");
            mSpotifyService.unfollowPlaylist(mHostUserId, mPlaylist.id);
            mPlaylist = null;
        }
    }

    private void addSongToQueue(Track track) {
        Map<String, Object> queryParameters = new HashMap<>();
        queryParameters.put("uris", track.uri);
        Map<String, Object> bodyParameters = new HashMap<>();

        // Add song to queue playlist
        mSpotifyService.addTracksToPlaylist(mHostUserId, mPlaylist.id, queryParameters, bodyParameters);
    }

    protected abstract void notifyHostTrackAdded();

    protected abstract void connectToHost();
}
