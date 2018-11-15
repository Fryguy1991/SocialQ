package com.chrisfry.socialq.userinterface.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import com.chrisfry.socialq.R;
import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.business.dagger.modules.SpotifyModule;
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent;
import com.chrisfry.socialq.business.dagger.modules.components.SpotifyComponent;
import com.chrisfry.socialq.enums.RequestType;
import com.chrisfry.socialq.enums.UserType;
import com.chrisfry.socialq.model.AccessModel;
import com.chrisfry.socialq.userinterface.adapters.BasicTrackListAdapter;
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;

import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Playlist;

public abstract class ClientActivity extends AppCompatActivity implements ConnectionStateCallback {
    private final String TAG = ClientActivity.class.getName();

    // Elements for queue display
    private RecyclerView mQueueList;
    private BasicTrackListAdapter mTrackDisplayAdapter;

    // Spotify API elements
    private SpotifyService mSpotifyService;
    private Playlist mPlaylist;
    protected String mHostUserId;
    private String mCurrentUserId;

    // Flag for if the client can follow the host playlist
    private boolean mCanFollowPlaylistFlag = true;

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

        Log.d(TAG, "Requesting access token from Spotify");
        AuthenticationClient.openLoginActivity(this, RequestType.SPOTIFY_AUTHENTICATION_REQUEST.getRequestCode(), request);

        initUi();
        setupQueueList();
    }

    private void initUi() {
        // Initialize UI elements
        mQueueList = findViewById(R.id.rv_queue_list_view);

        // Show queue title as activity title
        setTitle(getIntent().getStringExtra(AppConstants.QUEUE_TITLE_KEY));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        RequestType requestType = RequestType.Companion.getRequestTypeFromRequestCode(requestCode);
        Log.d(TAG, "Received request type: " + requestType);

        // Handle request result
        switch (requestType) {
            case SPOTIFY_AUTHENTICATION_REQUEST:
                AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
                if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                    Log.d(TAG, "Access token granted");

                    // Calculate when access token expires (response "ExpiresIn" is in seconds, subtract a minute to worry less about timing)
                    long accessExpireTime = System.currentTimeMillis() + (response.getExpiresIn() - 60) * 1000;

                    AccessModel.setAccess(response.getAccessToken(), UserType.CLIENT, accessExpireTime);
                    initSpotifySearchElements(response.getAccessToken());
                    connectToHost();
                } else {
                    Log.d(TAG, "Authentication Response: " + response.getError());
                    Toast.makeText(ClientActivity.this, getString(R.string.toast_authentication_error_client), Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case SEARCH_REQUEST:
                if (resultCode == RESULT_OK) {
                    String trackUri = intent.getStringExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY);
                    if (trackUri != null && !trackUri.isEmpty() && mCurrentUserId != null && !mCurrentUserId.isEmpty()) {
                        Log.d(TAG, "Client adding track to queue playlist");
                        sendTrackToHost(buildSongRequestMessage(trackUri, mCurrentUserId));
                    }
                }
                break;
            case NONE:
                Log.e(TAG, "Unhandled request code: " + requestCode);
            case REQUEST_ENABLE_BT:
            case REQUEST_DISCOVER_BT:
            default:
                // Do nothing.  Client activity should not handle BT events and if we got NONE back something is wrong
        }
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(R.string.close_client_dialog_title);

        View contentView = getLayoutInflater().inflate(R.layout.client_exit_dialog, null);
        final CheckBox followCheckbox = contentView.findViewById(R.id.cb_follow_playlist);

        dialogBuilder.setView(contentView);

        dialogBuilder.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "User chose to leave the queue");
                dialog.dismiss();

                // If follow is checked follow playlist with client user
                if (followCheckbox.isChecked()) {
                    Log.d(TAG, "User chose to follow the playlist");
                    mSpotifyService.followPlaylist(mCurrentUserId, mPlaylist.id);
                }
                disconnectClient();
                ClientActivity.super.onBackPressed();
            }
        });

        dialogBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "User chose to remain in the queue");
                dialog.dismiss();
            }
        });

        dialogBuilder.create().show();
    }

    private void initSpotifySearchElements(String accessToken) {
        // Setup Spotify service
        SpotifyComponent componenet = DaggerSpotifyComponent.builder().spotifyModule(
                new SpotifyModule(accessToken)).build();

        mSpotifyService = componenet.service();
        mCurrentUserId = mSpotifyService.getMe().id;
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
                startActivityForResult(searchIntent, RequestType.SEARCH_REQUEST.getRequestCode());
                return true;
            default:
                // Do nothing
                return false;
        }
    }

    private void setupQueueList() {
        mTrackDisplayAdapter = new BasicTrackListAdapter();
        mQueueList.setAdapter(mTrackDisplayAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mQueueList.setLayoutManager(layoutManager);
        mQueueList.addItemDecoration(new QueueItemDecoration(getApplicationContext()));
    }

    protected final void updateQueue(int currentPlayingIndex) {
        if (currentPlayingIndex >= 0) {
            refreshPlaylist();
            mTrackDisplayAdapter.updateAdapter(mPlaylist.tracks.items.subList(currentPlayingIndex, mPlaylist.tracks.items.size()));
        }
    }

    protected final void setupQueuePlaylistOnConnection(String playlistId) {
        mPlaylist = mSpotifyService.getPlaylist(mHostUserId, playlistId);
    }

    private void refreshPlaylist() {
        mPlaylist = mSpotifyService.getPlaylist(mHostUserId, mPlaylist.id);
    }

    protected final void showHostDisconnectedFollowPlaylistDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(R.string.close_client_host_disconnect_dialog_title);
        dialogBuilder.setView(R.layout.host_disconnected_dialog);

        dialogBuilder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "User chose to follow the playlist");
                dialog.dismiss();

                // If yes, follow the Spotify playlist
                mSpotifyService.followPlaylist(mCurrentUserId, mPlaylist.id);
            }
        });

        dialogBuilder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "User chose not to follow the playlist");
                dialog.dismiss();
                finish();
            }
        });

        dialogBuilder.create().show();
    }

    protected abstract void sendTrackToHost(String requestMessage);

    protected abstract void connectToHost();

    protected abstract void disconnectClient();

    private String buildSongRequestMessage(String trackUri, String userId) {
        if (trackUri != null && userId != null && !trackUri.isEmpty() && !userId.isEmpty()) {
            return String.format(AppConstants.SONG_REQUEST_MESSAGE_FORMAT, trackUri, userId);
        }
        Log.d(TAG, "Can't build track request for URI: " + trackUri + ", user ID: " + userId);
        return null;
    }
}
