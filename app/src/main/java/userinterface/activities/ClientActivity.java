package userinterface.activities;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
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

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import business.AppConstants;
import business.listeners.BluetoothConnectionListener;
import chrisfry.spotifydj.R;
import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Track;
import services.BluetoothConnectThread;
import services.PlayQueueService;
import userinterface.adapters.TrackListAdapter;
import userinterface.widgets.QueueItemDecoration;
import utils.ApplicationUtils;
import utils.DisplayUtils;

/**
 * Activity class for client of a queue
 */
public class ClientActivity extends Activity implements ConnectionStateCallback,
        PlayQueueService.TrackChangeListener, PlayQueueService.QueueChangeListener,
        BluetoothConnectionListener {
    private final String TAG = ClientActivity.class.getName();

    // Bluetooth elements
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mHostBTDevice;
    // Elements for queue display
    private RecyclerView mQueueList;
    private TrackListAdapter mQueueDisplayAdapter;

    private TextView mCurrentTrackName;
    private TextView mCurrentArtistName;

    // Spotify API elements
    private SpotifyApi mApi;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);
        BluetoothDevice newDevice = intent.getParcelableExtra(AppConstants.BT_DEVICE_EXTRA_KEY);
        if (!mHostBTDevice.equals(newDevice)) {
            mHostBTDevice = null;
            // TODO: Close BT socket since we have a new host
            if (newDevice != null) {
                // New host device.  Launch connection.
                mHostBTDevice = newDevice;
                // TODO: Open BT connection with new BT device
            }
        }
    }

    private SpotifyService mSpotifyService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.client_screen);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mHostBTDevice = getIntent().getParcelableExtra(AppConstants.BT_DEVICE_EXTRA_KEY);


        // Allow network operation in main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Building and sending login request through Spotify built activity
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(
                ApplicationUtils.getClientId(),
                AuthenticationResponse.Type.TOKEN,
                ApplicationUtils.getRedirectUri());
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
                    Log.d("ClientActivity", "Access token granted");
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
                    }
                }
                break;
            case AppConstants.REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {

                } else {
                    // TODO: Bluetooth not enabled, need to handle here
                }
                break;
            default:
                // Do nothing
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
        Log.d("ClientActivity", "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d("ClientActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Error error) {
        Log.d("ClientActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("ClientActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("ClientActivity", "Received connection message: " + message);
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

    @Override
    public void onConnectionEstablished(BluetoothSocket socket) {
        Log.d(TAG, "WE ARE CONNECTED!");
        try {
            socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the client socket", e);
        }
    }

    private void connectToHost() {
        new BluetoothConnectThread(BluetoothAdapter.getDefaultAdapter(), mHostBTDevice, this);
    }
}
