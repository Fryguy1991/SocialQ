package userinterface.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.ConnectionStateCallback;

import chrisfry.spotifydj.R;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.TracksPager;
import services.IPlayQueueService;
import services.PlayQueueService;

public class HomeScreen extends Activity implements ConnectionStateCallback {

    // Client ID specific to this application
    private static final String CLIENT_ID = "0fab62a3895a4fa3aae14bc3e46bc59c";
    // Redirect URI
    private static final String REDIRECT_URI = "fryredirect://callback";
    // Request code that will be used to verify if the result comes from correct activity
    // Can be any integer
    private static final int SPOTIFY_LOGIN_REQUEST = 8675309;

    private Button mNextButton;
    private Button mPlayPauseButton;

    private SpotifyApi mApi;
    private SpotifyService mSpotifyService;
    private IPlayQueueService mPlayQueueService;

    // Object for connecting to/from play queue service
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            PlayQueueService.PlayQueueBinder binder = (PlayQueueService.PlayQueueBinder) iBinder;
            mPlayQueueService = binder.getService();

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
        setContentView(R.layout.home_screen);

        // Allow network operation in main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Initialize UI elements
        mNextButton = (Button) findViewById(R.id.btn_next);
        mPlayPauseButton = (Button) findViewById(R.id.btn_play_pause);

        // Building and sending login request through Spotify built activity
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, SPOTIFY_LOGIN_REQUEST, request);

        addListeners();
    }

    private void addListeners() {
        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayQueueService.playNextInQueue();
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
        if (requestCode == SPOTIFY_LOGIN_REQUEST) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Log.d("HomeScreen", "Access token granted");
                // Start service that will play and control queue
                Intent startPlayQueueIntent = new Intent(this, PlayQueueService.class);
                Bundle extraBundle = new Bundle();
                extraBundle.putString(getString(R.string.access_token_extra), response.getAccessToken());
                startPlayQueueIntent.putExtras(extraBundle);
                bindService(startPlayQueueIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
                initSpotifySearchElements(response.getAccessToken());
            }
        }
    }

    private void initSpotifySearchElements(String accessToken) {
        // Setup service for searching Spotify library
        mApi = new SpotifyApi();
        mApi.setAccessToken(accessToken);
        mSpotifyService = mApi.getService();
    }

    @Override
    public void onLoggedIn() {
        Log.d("HomeScreen", "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d("HomeScreen", "User logged out");
    }

    @Override
    public void onLoginFailed(Throwable error) {
        Log.d("HomeScreen", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("HomeScreen", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("HomeScreen", "Received connection message: " + message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void setupDemoQueue() {
        TracksPager tracks = mSpotifyService.searchTracks("Built This Pool");
        mPlayQueueService.addSongToQueue(tracks.tracks.items.get(0));

        tracks = mSpotifyService.searchTracks("Audience of One");
        mPlayQueueService.addSongToQueue(tracks.tracks.items.get(0));

        tracks = mSpotifyService.searchTracks("Love Yourself Somebody");
        mPlayQueueService.addSongToQueue(tracks.tracks.items.get(0));

        tracks = mSpotifyService.searchTracks("How I Could Just Kill A Man");
        mPlayQueueService.addSongToQueue(tracks.tracks.items.get(0));
    }

    private void handlePlayPause(boolean isPlaying) {
        if(isPlaying) {
            mPlayPauseButton.setText(getString(R.string.play_btn));
            mPlayQueueService.pause();
        } else {
            mPlayPauseButton.setText(getString(R.string.pause_btn));
            mPlayQueueService.play();
        }
    }
}