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
import android.widget.TextView;

import com.chrisfry.socialq.R;
import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.business.dagger.modules.SpotifyModule;
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent;
import com.chrisfry.socialq.business.dagger.modules.components.SpotifyComponent;
import com.chrisfry.socialq.userinterface.adapters.TrackListAdapter;
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration;
import com.chrisfry.socialq.utils.ApplicationUtils;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;

import java.util.ArrayList;
import java.util.List;

import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Track;

public abstract class ClientActivity extends Activity implements ConnectionStateCallback {
    private final String TAG = ClientActivity.class.getName();

    // Elements for queue display
    private RecyclerView mQueueList;
    private TrackListAdapter mQueueDisplayAdapter;

    private TextView mCurrentTrackName;
    private TextView mCurrentArtistName;

    // Spotify API elements
    protected SpotifyService mSpotifyService;

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
        builder.setScopes(new String[]{"user-read-private"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, AppConstants.SPOTIFY_LOGIN_REQUEST, request);

        initUi();
        setupQueueList();
    }

    private void initUi() {
        // Initialize UI elements
        mQueueList = (RecyclerView) findViewById(R.id.rv_queue_list_view);
        mCurrentTrackName = (TextView) findViewById(R.id.tv_current_track_name);
        mCurrentArtistName = (TextView) findViewById(R.id.tv_current_artist_name);
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
                }
                break;
            case AppConstants.SEARCH_REQUEST:
                if (resultCode == RESULT_OK) {
                    String trackUri = intent.getStringExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY);
                    if (trackUri != null && !trackUri.isEmpty()) {
                        // TODO: Send request to host of queue
                        sendTrackToHost(trackUri);
                    }
                }
                break;
            default:
                // Do nothing
        }
    }

    private void initSpotifySearchElements(String accessToken) {
        // Setup service for searching Spotify library
//        mApi = new SpotifyApi();
//        mApi.setAccessToken(accessToken);
//        mSpotifyService = mApi.getService();

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
        mQueueDisplayAdapter = new TrackListAdapter(new ArrayList<Track>());
        mQueueList.setAdapter(mQueueDisplayAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mQueueList.setLayoutManager(layoutManager);
        mQueueList.addItemDecoration(new QueueItemDecoration(getApplicationContext()));
    }

    protected void updateQueue(List<Track> trackQueue) {
        if (trackQueue != null) {
            mQueueDisplayAdapter.updateQueueList(trackQueue);
        }
    }

    protected abstract void sendTrackToHost(String trackUri);

}
