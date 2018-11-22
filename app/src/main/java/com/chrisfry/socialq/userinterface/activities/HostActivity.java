package com.chrisfry.socialq.userinterface.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.chrisfry.socialq.business.dagger.modules.SpotifyModule;
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent;
import com.chrisfry.socialq.business.dagger.modules.components.SpotifyComponent;
import com.chrisfry.socialq.enums.RequestType;
import com.chrisfry.socialq.model.AccessModel;
import com.chrisfry.socialq.model.ClientRequestData;
import com.chrisfry.socialq.model.SongRequestData;
import com.chrisfry.socialq.userinterface.adapters.HostTrackListAdapter;
import com.chrisfry.socialq.userinterface.adapters.IItemSelectionListener;
import com.chrisfry.socialq.userinterface.adapters.SelectablePlaylistAdapter;
import com.google.gson.JsonArray;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.R;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Pager;
import kaaes.spotify.webapi.android.models.Playlist;
import kaaes.spotify.webapi.android.models.PlaylistSimple;
import kaaes.spotify.webapi.android.models.PlaylistTrack;
import kaaes.spotify.webapi.android.models.UserPrivate;
import kaaes.spotify.webapi.android.models.UserPublic;

import com.chrisfry.socialq.services.PlayQueueService;
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration;

public abstract class HostActivity extends AppCompatActivity implements ConnectionStateCallback,
        PlayQueueService.PlayQueueServiceListener, IItemSelectionListener<String> {
    private final String TAG = HostActivity.class.getName();

    // UI element references
    private View mNextButton;
    private ImageView mPlayPauseButton;

    // Track list elements
    private RecyclerView mQueueList;
    private HostTrackListAdapter mTrackDisplayAdapter;

    // Spotify elements
    private SpotifyApi mSpotifyApi;
    protected SpotifyService mSpotifyService;
    private PlayQueueService mPlayQueueService;
    protected UserPrivate mCurrentUser;
    protected Playlist mPlaylist;
    // List that will contain ALL tracks from mPlaylist (mPlaylist contains max 100)
    private List<PlaylistTrack> mPlaylistTracks = new ArrayList<>();

    // Flag to determine if the service is bound or not
    private boolean mIsServiceBound = false;
    // Cached value for playing index (used to inform new clients)
    protected int mCachedPlayingIndex = 0;
    // Boolean flag to store if queue should be "fair play"
    private boolean mIsQueueFairPlay;
    // List containing client song requests
    private List<SongRequestData> mSongRequests = new ArrayList<>();
    // Reference to base playlist dialog
    private AlertDialog mBasePlaylistDialog = null;
    // Reference to base playlist ID for loading when service is connected
    private String mBasePlaylistId = "";
    // Flag for storing if the player has been activated
    private boolean mIsPlayerActive = false;

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

            // Load base playlist if one was selected
            if (!mBasePlaylistId.isEmpty()) {
                loadBasePlaylist(mBasePlaylistId);
            }
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
        Log.d(TAG, "Requesting access token from Spotify");
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
        builder.setScopes(new String[]{"user-read-private", "streaming", "playlist-modify-private", "playlist-read-private"});
        AuthenticationRequest request = builder.build();
        AuthenticationClient.openLoginActivity(this, RequestType.SPOTIFY_AUTHENTICATION_REQUEST.getRequestCode(), request);
    }

    private void initUi() {
        // Initialize UI elements
        mNextButton = findViewById(R.id.btn_next);
        mPlayPauseButton = findViewById(R.id.btn_play_pause);
        mQueueList = findViewById(R.id.rv_queue_list_view);

        // Show queue title as activity title
        setTitle(getIntent().getStringExtra(AppConstants.QUEUE_TITLE_KEY));

        // Stop soft keyboard from pushing UI up
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
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

                    AccessModel.setAccess(response.getAccessToken(), accessExpireTime);

                    // Start thread responsible for notifying UI thread when new access token is needed
                    new AccessRefreshThread().start();

                    if (mPlayQueueService == null) {
                        Log.d(TAG, "First access token granted.  Init Spotify elements, play queue service, and start host connection");
                        // Initialize spotify elements and create playlist for queue
                        initSpotifyElements(response.getAccessToken());

                        // Show dialog for selecting base playlist if user has playlists to show
                        Map<String, Object> options = new HashMap<>();
                        options.put(SpotifyService.LIMIT, 50);
                        Pager<PlaylistSimple> playlistPager = mSpotifyService.getPlaylists(mCurrentUser.id, options);
                        if (playlistPager.items.size() > 0) {
                            showBasePlaylistDialog(playlistPager.items);
                        } else {
                            // If no existing Spotify playlists don't show dialog and create one from scratch
                            createPlaylistForQueue();
                            startPlayQueueService();
                        }
                    } else {
                        Log.d(TAG, "New access token granted.  Update Spotify Api and service");
                        mSpotifyApi.setAccessToken(response.getAccessToken());
                        mSpotifyService = mSpotifyApi.getService();

                        // Update service with new access token
                        mPlayQueueService.notifyServiceAccessTokenChanged(response.getAccessToken());
                    }
                } else {
                    Log.d(TAG, "Authentication Response: " + response.getError());
                    Toast.makeText(HostActivity.this, getString(R.string.toast_authentication_error_host), Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case SEARCH_REQUEST:
                if (resultCode == RESULT_OK) {
                    String trackUri = intent.getStringExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY);
                    handleSongRequest(new SongRequestData(trackUri, mCurrentUser));
                }
                break;
            case NONE:
                Log.e(TAG, "Unhandled request code: " + requestCode);
            case REQUEST_ENABLE_BT:
            case REQUEST_DISCOVER_BT:
            default:
                // Do nothing.  Host activity should not handle BT events and if we got NONE back something is wrong
        }
    }

    private void startPlayQueueService() {
        // Start service that will play and control queue
        Intent startPlayQueueIntent = new Intent(this, PlayQueueService.class);
        startPlayQueueIntent.putExtra(AppConstants.SERVICE_PLAYLIST_ID_KEY, mPlaylist.id);
        startService(startPlayQueueIntent);

        // Bind activity to service
        mIsServiceBound = bindService(startPlayQueueIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        // All logged in and good to go.  Start host connection.
        startHostConnection(getIntent().getStringExtra(AppConstants.QUEUE_TITLE_KEY));
    }

    private void initSpotifyElements(String accessToken) {
        Log.d(TAG, "Initializing Spotify elements");

        // Setup service for searching Spotify library
        SpotifyComponent componenet = DaggerSpotifyComponent.builder().spotifyModule(
                new SpotifyModule(accessToken)).build();

        mSpotifyApi = componenet.api();
        mSpotifyService = componenet.service();

        mCurrentUser = mSpotifyService.getMe();
    }

    @Override
    public void onBackPressed() {

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(getString(R.string.close_host_dialog_title));

        // Inflate content view and get references to UI elements
        View contentView = getLayoutInflater().inflate(R.layout.save_playlist_dialog, null);
        final EditText playlistNameEditText = contentView.findViewById(R.id.et_save_playlist_name);
        final CheckBox savePlaylistCheckbox = contentView.findViewById(R.id.cb_save_playlist);

        dialogBuilder.setView(contentView);

        // If save playlist box is checked, enable edit text for playlist name
        // If save playlist box is unchecked, disabled edit text and clear field value
        savePlaylistCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                playlistNameEditText.setEnabled(isChecked);

                if (!isChecked) {
                    playlistNameEditText.setText("");
                }
            }
        });

        dialogBuilder.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                if (savePlaylistCheckbox.isChecked()) {
                    Log.d(TAG, "Updating SocialQ playlist details");

                    String playlistName = playlistNameEditText.getText().toString();

                    // Create body parameters for modifying playlist details
                    Map<String, Object> playlistParameters = new HashMap<>();
                    playlistParameters.put("name", playlistName.isEmpty() ? getString(R.string.default_playlist_name) : playlistName);

                    mSpotifyService.changePlaylistDetails(mCurrentUser.id, mPlaylist.id, playlistParameters);
                } else {
                    unfollowQueuePlaylist();
                }

                dialog.dismiss();
                HostActivity.super.onBackPressed();
            }
        });

        dialogBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Don't actually want to close the queue
                dialog.dismiss();
            }
        });

        dialogBuilder.create().show();
    }

    private void showBasePlaylistDialog(List<PlaylistSimple> playlists) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(getString(R.string.select_base_playlist));

        // Inflate content view and get references to UI elements
        View contentView = getLayoutInflater().inflate(R.layout.base_playlist_dialog, null);
        RecyclerView playlistList = contentView.findViewById(R.id.rv_playlist_list);

        // Add recycler view item decoration
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, RecyclerView.VERTICAL, false);
        playlistList.setLayoutManager(layoutManager);
        playlistList.addItemDecoration(new QueueItemDecoration(getApplicationContext()));

        // Setup list adapter
        SelectablePlaylistAdapter playlistAdapter = new SelectablePlaylistAdapter();
        playlistAdapter.setListener(this);
        playlistAdapter.updateAdapter(playlists);
        playlistList.setAdapter(playlistAdapter);

        dialogBuilder.setView(contentView);

        dialogBuilder.setNeutralButton(R.string.fresh_playlist, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                Log.d(TAG, "User selected not to use a base playlist");
                createPlaylistForQueue();
                startPlayQueueService();
            }
        });

        dialogBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                Log.d(TAG, "User didn't complete base playlist dialog");
                createPlaylistForQueue();
                startPlayQueueService();
            }
        });

        mBasePlaylistDialog = dialogBuilder.create();
        mBasePlaylistDialog.show();
    }

    private void createPlaylistForQueue() {
        // Create body parameters for new playlist
        Map<String, Object> playlistParameters = new HashMap<>();
        playlistParameters.put("name", getString(R.string.default_playlist_name));
        playlistParameters.put("public", true);
        playlistParameters.put("collaborative", false);
        playlistParameters.put("description", "Playlist created by the SocialQ App.");

        Log.d(TAG, "Creating playlist for the SocialQ");
        mPlaylist = mSpotifyService.createPlaylist(mCurrentUser.id, playlistParameters);
    }

    private void loadBasePlaylist(String playlistId) {
        Log.d(TAG, "Loading base playlist with ID: " + playlistId);

        Playlist basePlaylist = mSpotifyService.getPlaylist(mCurrentUser.id, playlistId);

        // Adding with base user ensure host added tracks are sorted within the base playlist
        UserPublic baseUser = new UserPublic();
        baseUser.id = AppConstants.BASE_USER_ID;
        baseUser.display_name = getResources().getString(R.string.base_playlist);

        // Retrieve all tracks
        List<PlaylistTrack> playlistTracks = new ArrayList<>();
        // Can only retrieve 100 tracks at a time
        for (int i = 0; i < basePlaylist.tracks.total; i += 100) {
            Map<String, Object> iterationQueryParameters = new HashMap<>();
            iterationQueryParameters.put("offset", i);

            // Retrieve max next 100 tracks
            Pager<PlaylistTrack> iterationTracks = mSpotifyService.getPlaylistTracks(mCurrentUser.id, playlistId, iterationQueryParameters);

            playlistTracks.addAll(iterationTracks.items);
        }

        // Shuffle entire track list
        List<PlaylistTrack> shuffledTracks = new ArrayList<>();
        Random randomGenerator = new Random();
        while (playlistTracks.size() > 0) {
            int trackIndexToAdd = randomGenerator.nextInt(playlistTracks.size());

            shuffledTracks.add(playlistTracks.remove(trackIndexToAdd));
        }

        // Add tracks (100 at a time) to playlist and add request date
        while (shuffledTracks.size() > 0) {
            JsonArray urisArray = new JsonArray();
            // Can only add max 100 tracks
            for (int i = 0; i < 100; i++) {
                if (shuffledTracks.size() == 0) {
                    break;
                }
                PlaylistTrack track = shuffledTracks.remove(0);
                // Can't add local tracks (local to playlist owner's device)
                if (!track.is_local) {
                    SongRequestData requestData = new SongRequestData(track.track.uri, baseUser);
                    mSongRequests.add(requestData);

                    urisArray.add(track.track.uri);
                }
            }
            Map<String, Object> queryParameters = new HashMap<>();
            Map<String, Object> bodyParameters = new HashMap<>();
            bodyParameters.put("uris", urisArray);

            // Add max 100 tracks to the playlist
            mSpotifyService.addTracksToPlaylist(mCurrentUser.id, mPlaylist.id, queryParameters, bodyParameters);
        }
        mPlayQueueService.notifyServiceQueueHasChanged();
    }

    protected final void handleSongRequest(SongRequestData songRequest) {
        if (songRequest != null && !songRequest.getUri().isEmpty()) {
            Log.d(TAG, "Received request for URI: " + songRequest.getUri() + ", from User ID: " + songRequest.getUser().id);

            // Add track to request list
            mSongRequests.add(songRequest);

            // Don't need to worry about managing the queue if fairplay is off
            if (mIsQueueFairPlay) {
                if (injectNewTrack(songRequest)) {
                    // If we changed the next track notify service meta data is out of sync
                    mPlayQueueService.notifyServiceMetaDataIsStale(songRequest.getUri());
                } else {
                    mPlayQueueService.notifyServiceQueueHasChanged();
                }
            } else {
                addTrackToPlaylist(songRequest.getUri());
                if (mSongRequests.size() == 2) {
                    // If we changed the next track notify service meta data is out of sync
                    mPlayQueueService.notifyServiceMetaDataIsStale(songRequest.getUri());
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
     * @param uri      - uri of the track to be added
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

        HashMap<String, Boolean> clientRepeatHash = new HashMap<>();

        // Start inspecting song requests
        for (newTrackPosition = 0; newTrackPosition < mSongRequests.size(); newTrackPosition++) {
            String currentRequestUserId = mSongRequests.get(newTrackPosition).getUser().id;

            // Base playlist track. Check if we can replace it
            if (currentRequestUserId.equals(AppConstants.BASE_USER_ID)) {
                // If player is not active we can add a track at index 0 (replace base playlist)
                // because we haven't started the playlist. Else don't cause the base playlist
                // track may currently be playing
                if ((newTrackPosition == 0 && !mIsPlayerActive) || newTrackPosition > 0) {
                    // We want to keep user tracks above base playlist tracks.  Use base playlist
                    // as a fall back.
                    break;
                }
            }

            if (currentRequestUserId.equals(songRequest.getUser().id)) {
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
        Log.d(TAG, "Refreshing playlist");
        mPlaylist = mSpotifyService.getPlaylist(mCurrentUser.id, mPlaylist.id);
        refreshPlaylistTracks();
    }

    private void refreshPlaylistTracks() {
        Log.d(TAG, "Refreshing playlist track list");
        mPlaylistTracks.clear();
        // refreshPlaylist already retrieved the first 100 tracks, add to list
        mPlaylistTracks.addAll(mPlaylist.tracks.items);
        // TODO: This can slow down app functionality if there are a lot of tracks (every 100 tracks in the playlist is another call to the spotify API)
        for (int i = 100; i < mPlaylist.tracks.total; i += 100) {
            Map<String, Object> iterationQueryParameters = new HashMap<>();
            iterationQueryParameters.put("offset", i);
            Pager<PlaylistTrack> iterationTracks = mSpotifyService.getPlaylistTracks(mCurrentUser.id, mPlaylist.id, iterationQueryParameters);

            mPlaylistTracks.addAll(iterationTracks.items);
        }
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
        // Unbind from the PlayQueueService
        if (mIsServiceBound) {
            unbindService(mServiceConnection);
            mIsServiceBound = false;
        }

        // Remove HostActivity as a listener to PlayQueueService and stop the service
        if (mPlayQueueService != null) {
            mPlayQueueService.removePlayQueueServiceListener(this);
            mPlayQueueService.stopSelf();
        }

        // This should trigger access request thread to end if it is running
        AccessModel.reset();
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
            case R.id.search_fragment:
                Intent searchIntent = new Intent(this, SearchActivity.class);
                startActivityForResult(searchIntent, RequestType.SEARCH_REQUEST.getRequestCode());
                return true;
            default:
                // Do nothing
                return false;
        }
    }

    private void setupQueueList() {
        mTrackDisplayAdapter = new HostTrackListAdapter(getApplicationContext());
        mQueueList.setAdapter(mTrackDisplayAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, RecyclerView.VERTICAL, false);
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

        if (currentPlayingIndex >= mPlaylist.tracks.total) {
            mTrackDisplayAdapter.updateAdapter(new ArrayList<ClientRequestData>());
        } else {
            mTrackDisplayAdapter.updateAdapter(createDisplayList(mPlaylistTracks.subList(currentPlayingIndex, mPlaylist.tracks.total)));
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

        mTrackDisplayAdapter.updateAdapter(createDisplayList(mPlaylistTracks.subList(mCachedPlayingIndex, mPlaylist.tracks.total)));

        notifyClientsQueueUpdated(mCachedPlayingIndex);
    }

    @Override
    public void onPlayerActive() {
        // Change flag that allows new tracks to be added at the very beginning of the playlist
        mIsPlayerActive = true;
    }


    private List<ClientRequestData> createDisplayList(List<PlaylistTrack> trackList) {
        ArrayList<ClientRequestData> displayList = new ArrayList<>();

        // TODO: This does not guarantee correct display.  If player does not play songs in
        // correct order, the incorrect users may be displayed
        for (int i = 0; i < trackList.size() && i < mSongRequests.size(); i++) {
            displayList.add(new ClientRequestData(trackList.get(i), mSongRequests.get(i).getUser()));
        }

        return displayList;
    }

    /**
     * Item selection method for playlist ID in base playlist dialog
     *
     * @param selectedItem - ID of the playlist that was selected
     */
    @Override
    public void onItemSelected(String selectedItem) {
        if (mBasePlaylistDialog != null && mBasePlaylistDialog.isShowing()) {
            mBasePlaylistDialog.dismiss();
            createPlaylistForQueue();
            startPlayQueueService();
            mBasePlaylistId = selectedItem;
        }
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
                        if (System.currentTimeMillis() >= AccessModel.getAccessExpireTime()) {
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

        // String for testing fair play (simulate a user name)
        String userId = "fake_user";
//        String userId = mCurrentUser.id;

        mSongRequests.add(new SongRequestData("spotify:track:0p8fUOBfWtGcaKGiD9drgJ", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:6qtg4gz3DhqOHL5BHtSQw8", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:57bgtoPSgt236HzfBOd8kj", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:7lGh1Dy02c5C0j3tj9AVm3", mCurrentUser));

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


        // String for testing fair play (simulate a user name)
//        String userId = "fake_user";
//        String userId = mCurrentUser.id;

        mSongRequests.add(new SongRequestData("spotify:track:0p8fUOBfWtGcaKGiD9drgJ", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:6qtg4gz3DhqOHL5BHtSQw8", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:57bgtoPSgt236HzfBOd8kj", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:4VbDJMkAX3dWNBdn3KH6Wx", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:2jnvdMCTvtdVCci3YLqxGY", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:419qOkEdlmbXS1GRJEMntC", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:1jvqZQtbBGK5GJCGT615ao", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:6cG3kY60HMcFqiZN8frkXF", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:0dqrAmrvQ6fCGNf5T8If5A", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:0wHNrrefyaeVewm4NxjxrX", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:1hh4GY1zM7SUAyM3a2ziH5", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:5Cl9GDb0AyQnppRr6q7ldb", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:7D180Q77XAEP7atBLmMTgK", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:2uxL6E8Yq0Psc1V9uBtC4F", mCurrentUser));
        mSongRequests.add(new SongRequestData("spotify:track:7lGh1Dy02c5C0j3tj9AVm3", mCurrentUser));

        mPlayQueueService.notifyServiceQueueHasChanged();
    }

    public abstract void initiateNewClient(Object client);

    protected abstract void startHostConnection(String queueTitle);

    protected abstract void notifyClientsQueueUpdated(int currentPlayingIndex);
}