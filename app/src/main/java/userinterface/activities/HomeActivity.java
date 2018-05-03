package userinterface.activities;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
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

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Metadata;

import java.util.ArrayList;
import java.util.List;

import business.AppConstants;
import chrisfry.spotifydj.R;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Track;
import kaaes.spotify.webapi.android.models.TracksPager;
import services.PlayQueueService;
import userinterface.adapters.TrackListAdapter;
import userinterface.widgets.QueueItemDecoration;
import utils.ApplicationUtils;
import utils.DisplayUtils;

public class HomeActivity extends Activity implements ConnectionStateCallback,
        PlayQueueService.TrackChangeListener, PlayQueueService.QueueChangeListener {
    // Request code that will be used to verify if the result comes from correct activity
    // Can be any integer
    private static final int SPOTIFY_LOGIN_REQUEST = 8675309;
    // Request code used for Spotify search
    private static final int SEARCH_REQUEST = 1337;

    // UI element references
    private Button mNextButton;

    private Button mPlayPauseButton;
    private RecyclerView mQueueList;
    private TrackListAdapter mQueueDisplayAdapter;
    private TextView mCurrentTrackName;
    private TextView mCurrentArtistName;

    private SpotifyApi mApi;
    private SpotifyService mSpotifyService;
    private PlayQueueService mPlayQueueService;

    BluetoothAdapter mBTAdapter;

    // Object for connecting to/from play queue service
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            PlayQueueService.PlayQueueBinder binder = (PlayQueueService.PlayQueueBinder) iBinder;
            mPlayQueueService = binder.getService();

            // Setup activity for callbacks
            mPlayQueueService.addTrackChangedListener(HomeActivity.this);
            mPlayQueueService.addQueueChangedListener(HomeActivity.this);

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
        setContentView(R.layout.home_screen);



        // Allow network operation in main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Building and sending login request through Spotify built activity
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(
                ApplicationUtils.getClientId(),
                AuthenticationResponse.Type.TOKEN,
                ApplicationUtils.getRedirectUri());
        builder.setScopes(new String[]{"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, SPOTIFY_LOGIN_REQUEST, request);

        initUi();
        addListeners();
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
                    Log.d("HomeActivity", "Access token granted");
                    // Start service that will play and control queue
                    ApplicationUtils.setAccessToken(response.getAccessToken());
                    Intent startPlayQueueIntent = new Intent(this, PlayQueueService.class);
                    bindService(startPlayQueueIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
                    initSpotifySearchElements(response.getAccessToken());
                }
                break;
            case SEARCH_REQUEST:
                if (resultCode == RESULT_OK) {
                    String trackUri = intent.getStringExtra(AppConstants.SEARCH_RESULTS_KEY);
                    if (trackUri != null && !trackUri.isEmpty()) {
                        mPlayQueueService.addSongToQueue(mSpotifyService.getTrack(trackUri));
                    }
                }
                break;
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
        Log.d("HomeActivity", "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d("HomeActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Error error) {
        Log.d("HomeActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("HomeActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("HomeActivity", "Received connection message: " + message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mPlayQueueService.onDestroy();
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
//                searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityForResult(searchIntent, SEARCH_REQUEST);
                return true;
            default:
                // Do nothing
                return false;
        }
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
    }

    @Override
    public void onQueueChanged(List<Track> trackQueue, String nextTrackUri) {
        Track nextTrack = mSpotifyService.getTrack(nextTrackUri);
        trackQueue.add(0, nextTrack);
        mQueueDisplayAdapter.updateQueueList(trackQueue);
    }
}