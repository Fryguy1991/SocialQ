package userinterface.activities;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import business.AppConstants;
import business.listeners.BluetoothConnectionListener;
import chrisfry.socialq.R;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Track;
import kaaes.spotify.webapi.android.models.TracksPager;
import services.BluetoothAcceptThread;
import services.PlayQueueService;
import userinterface.adapters.TrackListAdapter;
import userinterface.widgets.QueueItemDecoration;
import utils.ApplicationUtils;
import utils.DisplayUtils;

public class HostActivity extends Activity implements ConnectionStateCallback,
        PlayQueueService.TrackChangeListener, PlayQueueService.QueueChangeListener,
        BluetoothConnectionListener{
    private final String TAG = HostActivity.class.getName();
    
    // Request code that will be used to verify if the result comes from correct activity
    // Can be any integer
    private static final int SPOTIFY_LOGIN_REQUEST = 8675309;

    // Handler message values
    private static final int QUEUE_TRACK = 0;
    private static final String BUNDLE_TRACK_KEY = "bundle_track_key";

    // UI element references
    private Button mNextButton;
    private Button mPlayPauseButton;
    private TextView mCurrentTrackName;
    private TextView mCurrentArtistName;

    // Track list elements
    private RecyclerView mQueueList;
    private TrackListAdapter mQueueDisplayAdapter;

    // Spotify elements
    private SpotifyApi mApi;
    private SpotifyService mSpotifyService;
    private PlayQueueService mPlayQueueService;

    // Bluetooth elements
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothServerSocket mBTServerSocket;
    private List<BluetoothSocket> mBluetoothClients = new ArrayList<>();

    private List<Track> mCurrentTrackList = new ArrayList<>();

    // Object for connecting to/from play queue service
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            PlayQueueService.PlayQueueBinder binder = (PlayQueueService.PlayQueueBinder) iBinder;
            mPlayQueueService = binder.getService();

            // Setup activity for callbacks
            mPlayQueueService.addTrackChangedListener(HostActivity.this);
            mPlayQueueService.addQueueChangedListener(HostActivity.this);

            setupQueueList();
//            setupDemoQueue();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mPlayQueueService = null;
        }
    };

    private Handler mHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message inputMessage) {
            switch (inputMessage.what) {
                case QUEUE_TRACK:
                    String trackUri = inputMessage.getData().getString(BUNDLE_TRACK_KEY);
                    if (trackUri != null) {
                        Track trackToQueue = mSpotifyService.getTrack(trackUri);
                        mPlayQueueService.addSongToQueue(trackToQueue);
                    }
                    break;
                default:
                    // Do nothing
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.host_screen);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // TODO: Throw some sort of error/exception?  Need bluetooth for main application usage
        }
        mBluetoothAdapter.setName("SocialQ Host");

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
                    Log.d(TAG, "Access token granted");
                    // Start service that will play and control queue
                    ApplicationUtils.setAccessToken(response.getAccessToken());
                    Intent startPlayQueueIntent = new Intent(this, PlayQueueService.class);
                    bindService(startPlayQueueIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
                    initSpotifySearchElements(response.getAccessToken());

                    // All logged in and good to go.  Launch BT server and allow server to be discovered
                    if (!mBluetoothAdapter.isEnabled()) {
                        Log.d(TAG, "Attempting to enable Bluetooth");
                        // If bluetooth is not enabled request
                        Intent bluetoothEnableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(bluetoothEnableIntent, AppConstants.REQUEST_ENABLE_BT);
                    } else {
                        // If bluetooth is not discoverable request
                        Log.d(TAG, "Attempting to set device discoverable.");
                        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
                        startActivityForResult(discoverableIntent, AppConstants.REQUEST_DISCOVER_BT);
                    }
//                    else {
//                        Log.d(TAG, "Launching Bluetooth Host");
//                        launchBluetoothServer();
//                    }
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
            case AppConstants.REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "Bluetooth Was Enabled");
                    // Request bluetooth discoverability
                    Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                    discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
                    startActivityForResult(discoverableIntent, AppConstants.REQUEST_DISCOVER_BT);
                } else {
                    // TODO: Bluetooth not enabled, need to handle here
                }
                break;
            case AppConstants.REQUEST_DISCOVER_BT:
                if (resultCode == RESULT_CANCELED) {
                    // TODO: User said no to discoverability, handle here
                } else {
                    Log.d(TAG, "Device should be discoverable.");
                    launchBluetoothServer();
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

        mCurrentTrackList = trackQueue;
    }

    @Override
    public void onQueueChanged(List<Track> trackQueue, String nextTrackUri) {
        Track nextTrack = mSpotifyService.getTrack(nextTrackUri);
        trackQueue.add(0, nextTrack);
        mQueueDisplayAdapter.updateQueueList(trackQueue);

        mCurrentTrackList = trackQueue;
    }

    // BEGIN BLUETOOTH METHODS
    private void launchBluetoothServer() {
        new BluetoothAcceptThread(mBluetoothAdapter, this).start();
    }

    @Override
    public void onConnectionEstablished(BluetoothSocket socket) {
        Log.d(TAG, "A Client has connected.");
        mBluetoothClients.add(socket);
        try {
            new ReadThread(socket).start();
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Could not start ReadThread");
        }


//        try {
//            Log.d(TAG, "Attempting to send queue.");
//            OutputStream dataOut = socket.getOutputStream();
//            dataOut.write("start_queue".getBytes());
//            for (Track track : mCurrentTrackList) {
//                dataOut.write(track.uri.getBytes());
//            }
//            dataOut.write("end_queue".getBytes());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }
    // END BLUETOOTH METHODS


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

    private class ReadThread extends Thread {
        BluetoothSocket mSocket;
        InputStream mInputStream;
        public ReadThread(BluetoothSocket socket) throws IOException {
            mSocket = socket;
            try {
                mInputStream = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Could not get input stream.");
                throw e;
            }
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytesRead;

            while (true) {
                try {
                    bytesRead = mInputStream.read(buffer);
                    if (bytesRead > 0) {
                        byte[] uriBytes = new byte[bytesRead];
                        for (int i = 0; i < bytesRead; i++) {
                            uriBytes[i] = buffer[i];
                        }
                        String trackUri = new String(uriBytes, StandardCharsets.UTF_8);
                        Log.d(TAG, "Read track URI! START: " + trackUri + " :END");
                        Message queueTrackMessage = mHandler.obtainMessage(QUEUE_TRACK);
                        Bundle trackBundle = new Bundle();
                        trackBundle.putString(BUNDLE_TRACK_KEY, trackUri);
                        queueTrackMessage.setData(trackBundle);
                        queueTrackMessage.sendToTarget();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d(TAG, "Could not read bytes.");
                    break;
                }
            }
        }
    }
}