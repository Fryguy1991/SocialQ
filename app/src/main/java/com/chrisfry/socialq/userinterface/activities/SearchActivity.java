package com.chrisfry.socialq.userinterface.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bumptech.glide.Glide;
import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.business.dagger.modules.SpotifyModule;
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent;
import com.chrisfry.socialq.business.dagger.modules.components.SpotifyComponent;
import com.chrisfry.socialq.R;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindViews;
import butterknife.ButterKnife;
import kaaes.spotify.webapi.android.SpotifyCallback;
import kaaes.spotify.webapi.android.SpotifyError;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Album;
import kaaes.spotify.webapi.android.models.Albums;
import kaaes.spotify.webapi.android.models.AlbumsPager;
import kaaes.spotify.webapi.android.models.Artist;
import kaaes.spotify.webapi.android.models.ArtistsPager;
import kaaes.spotify.webapi.android.models.Track;
import kaaes.spotify.webapi.android.models.TrackSimple;
import kaaes.spotify.webapi.android.models.TracksPager;
import retrofit.client.Response;

import com.chrisfry.socialq.enums.SearchNavStep;
import com.chrisfry.socialq.model.AccessModel;
import com.chrisfry.socialq.userinterface.adapters.SearchAlbumTrackAdapter;
import com.chrisfry.socialq.userinterface.adapters.SearchTrackAdapter;
import com.chrisfry.socialq.userinterface.adapters.SearchTrackListAdapter;
import com.chrisfry.socialq.userinterface.adapters.holders.AlbumCardAdapter;
import com.chrisfry.socialq.userinterface.interfaces.ISpotifySelectionListener;
import com.chrisfry.socialq.userinterface.widgets.ArtistView;
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration;
import com.chrisfry.socialq.userinterface.widgets.TrackAlbumView;
import com.chrisfry.socialq.utils.DisplayUtils;

import org.jetbrains.annotations.NotNull;

/**
 * Activity for searching Spotify tracks
 */
public class SearchActivity extends AppCompatActivity implements SearchTrackListAdapter.TrackSelectionListener, ISpotifySelectionListener, View.OnClickListener {
    private final String TAG = SearchActivity.class.getName();

    // Spotify search references
    private SpotifyService mSpotifyService;

    // UI references
    private ConstraintLayout mMainLayout;
    private ConstraintLayout mBaseResultLayout;

    private ScrollView mResultsScrollView;
    private View mSongsHeader;
    private View mArtistsHeader;
    private View mAlbumsHeader;
    private View mViewAllSongs;
    private View mViewAllArtists;
    private View mViewAllAlbums;
    private RecyclerView mResultsList;
    private QueueItemDecoration mListDecoration;
    private EditText mSearchText;
    private TextView mNoResultsText;
    private ImageView mArtistAlbumImage;

    // Bind lists of all views (header, items, and view all button)
    @BindViews({R.id.tv_song_heading, R.id.cv_track_1, R.id.cv_track_2, R.id.cv_track_3, R.id.tv_see_all_songs})
    List<View> mAllSongViews;
    @BindViews({R.id.tv_artist_heading, R.id.cv_artist_1, R.id.cv_artist_2, R.id.cv_artist_3, R.id.tv_see_all_artists})
    List<View> mAllArtistViews;
    @BindViews({R.id.tv_album_heading, R.id.cv_album_1, R.id.cv_album_2, R.id.cv_album_3, R.id.tv_see_all_albums})
    List<View> mAllAlbumViews;

    // Views for different nav states
    @BindViews({R.id.et_search_edit_text, R.id.sv_search_scroll_view})
    List<View> mBaseDisplayViews;
    @BindViews({R.id.iv_artist_album_image, R.id.rv_search_results})
    List<View> mAlbumDisplayViews;

    // Bind lists of item views
    @BindViews({R.id.cv_track_1, R.id.cv_track_2, R.id.cv_track_3})
    List<TrackAlbumView> mSongItemViews;
    @BindViews({R.id.cv_artist_1, R.id.cv_artist_2, R.id.cv_artist_3})
    List<ArtistView> mArtistItemViews;
    @BindViews({R.id.cv_album_1, R.id.cv_album_2, R.id.cv_album_3})
    List<TrackAlbumView> mAlbumItemViews;

    private SearchNavStep mNavStep = SearchNavStep.BASE;

    // Search result containers
    private List<Track> mResultTrackList = new ArrayList<>();
    private List<Artist> mResultArtistList = new ArrayList<>();
    private List<Album> mResultAlbumList = new ArrayList<>();

    // Recycler view adapters
    private SearchTrackAdapter mSongResultsAdapter;
    private SearchAlbumTrackAdapter mAlbumSongAdapter;
    private AlbumCardAdapter mAlbumCardAdapter;
    // TODO: Artist adapter

    // Timer to start searches when edittext is modified
    private Timer mSearchTimer = null;

    // Flags for detecting if search is complete
    private boolean mSongSearchComplete = false;
    private boolean mArtistSearchComplete = false;
    private boolean mAlbumSearchComplete = false;

    private String mCachedSearchTerm = "";

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AppConstants.HANDLER_SEARCH_BY_TEXT:
                    searchByText(mSearchText.getText().toString());
                    break;
            }
        }
    };

    private void sendHandlerMessage(int msgWhat) {
        Message message = mHandler.obtainMessage();
        message.what = msgWhat;
        message.sendToTarget();
    }

    // Song search callback object
    private SpotifyCallback<TracksPager> mSongsCallback = new SpotifyCallback<TracksPager>() {
        @Override
        public void failure(SpotifyError spotifyError) {
            Log.e(TAG, "Error searching for tracks: " + spotifyError.getErrorDetails());
        }

        @Override
        public void success(TracksPager tracksPager, Response response) {
            Log.d(TAG, "Successfully searched for tracks");

            if (doSearchTermsMatch(response, AppConstants.URL_FULL_TRACK_SEARCH)) {
                Log.d(TAG, "Track search terms match");

                mSongSearchComplete = true;
                mResultTrackList = tracksPager.tracks.items;
                if (!checkIfNoResults()) {
                    showSongResults();
                }

            } else {
                Log.d(TAG, "Track search terms DON'T match");
            }
        }
    };

    // Artist search callback object
    private SpotifyCallback<ArtistsPager> mArtistsCallback = new SpotifyCallback<ArtistsPager>() {
        @Override
        public void failure(SpotifyError spotifyError) {
            Log.e(TAG, "Error searching for artists: " + spotifyError.getErrorDetails());
        }

        @Override
        public void success(ArtistsPager artistsPager, Response response) {
            Log.d(TAG, "Successfully searched for artists");

            if (doSearchTermsMatch(response, AppConstants.URL_FULL_ARTIST_SEARCH)) {
                Log.d(TAG, "Artist search terms match");

                mArtistSearchComplete = true;
                mResultArtistList = artistsPager.artists.items;
                if (!checkIfNoResults()) {
                    showArtistResults();
                }
            } else {
                Log.d(TAG, "Artist search terms DON'T match");
            }
        }
    };

    // Album search callback object
    private SpotifyCallback<AlbumsPager> mAlbumsCallback = new SpotifyCallback<AlbumsPager>() {
        @Override
        public void failure(SpotifyError spotifyError) {
            Log.e(TAG, "Error searching for albums: " + spotifyError.getErrorDetails());
        }

        @Override
        public void success(AlbumsPager albumsPager, Response response) {
            Log.d(TAG, "Successfully searched for albums");

            if (doSearchTermsMatch(response, AppConstants.URL_FULL_ALBUM_SEARCH)) {
                Log.d(TAG, "Album search terms match");
                mAlbumSearchComplete = true;

                // Only retrieve full albums if we know more than 1 exists
                if (albumsPager.albums.items.size() > 0) {
                    String albumSearchString = "";
                    for (int i = 0; i < 20 && i < albumsPager.albums.items.size(); i++) {
                        albumSearchString = albumSearchString.concat(albumsPager.albums.items.get(i).id);

                        if (i != 19 && i < albumsPager.albums.items.size() - 1) {
                            albumSearchString = albumSearchString.concat(",");
                        }
                    }

                    mSpotifyService.getAlbums(albumSearchString, mFullAlbumsCallback);
                } else {
                    if (!checkIfNoResults()) {
                        showAlbumResults();
                    }
                }
            } else {
                Log.d(TAG, "Album search terms DON'T match");
            }
        }
    };

    // Full album search callback object
    // TODO: this makes searching for albums really slow. Should attempt to modify underlying spotify service code
    // in order to pull full albums instead of simple ones (so artist data is included)
    private SpotifyCallback<Albums> mFullAlbumsCallback = new SpotifyCallback<Albums>() {
        @Override
        public void failure(SpotifyError spotifyError) {
            Log.e(TAG, "Error retrieveing full albums: " + spotifyError.getErrorDetails());
        }

        @Override
        public void success(Albums albums, Response response) {
            mResultAlbumList = albums.albums;
            if (!checkIfNoResults()) {
                showAlbumResults();
            }
        }
    };

    private boolean doSearchTermsMatch(Response response, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(response.getUrl());

        if (matcher.find()) {
            String searchTerm = matcher.group(1);
            // URL search term replaces spaces with '+'
            return searchTerm.equals(mCachedSearchTerm.replace(' ', '+'));
        }
        return false;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_base_layout);
        ButterKnife.bind(this);

        // Stop soft keyboard from pushing UI up
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        String accessToken = AccessModel.getAccessToken();
        if (accessToken == null || System.currentTimeMillis() > AccessModel.getAccessExpireTime()) {
            Log.d(TAG, "Invalid Access Token");
            Toast.makeText(this, "Invalid Access Token", Toast.LENGTH_LONG).show();
            finish();
        } else {
            initSpotifySearchElements(accessToken);
        }

        // Show up nav icon
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initUi();
        addListeners();
        initAdapters();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            default:
                // Didn't handle item selected return false
                return false;
        }
        return true;
    }

    private void initSpotifySearchElements(String accessToken) {
        // Setup service for searching Spotify library
        SpotifyComponent componenet = DaggerSpotifyComponent.builder().spotifyModule(
                new SpotifyModule(accessToken)).build();

        mSpotifyService = componenet.service();
    }

    private void initUi() {
        // Initialize UI elements
        mMainLayout = findViewById(R.id.cl_search_main);
        mBaseResultLayout = findViewById(R.id.cl_search_result_layout);

        mSearchText = findViewById(R.id.et_search_edit_text);
        mNoResultsText = findViewById(R.id.tv_no_results);
        mResultsScrollView = findViewById(R.id.sv_search_scroll_view);

        // Section headers
        mSongsHeader = findViewById(R.id.tv_song_heading);
        mArtistsHeader = findViewById(R.id.tv_artist_heading);
        mAlbumsHeader = findViewById(R.id.tv_album_heading);

        // View all buttons
        mViewAllSongs = findViewById(R.id.tv_see_all_songs);
        mViewAllArtists = findViewById(R.id.tv_see_all_artists);
        mViewAllAlbums = findViewById(R.id.tv_see_all_albums);

        // Results list
        mResultsList = findViewById(R.id.rv_search_results);
        mListDecoration = new QueueItemDecoration(getApplicationContext());

        // Image used for album/artist display
        mArtistAlbumImage = findViewById(R.id.iv_artist_album_image);
    }

    private void addListeners() {
        // Add on click listeners to view all buttons
        mViewAllSongs.setOnClickListener(this);
        mViewAllArtists.setOnClickListener(this);
        mViewAllAlbums.setOnClickListener(this);

        // Register for selection events on all items
        for (TrackAlbumView trackView : mSongItemViews) {
            trackView.setListener(this);
        }
        for (ArtistView artistView : mArtistItemViews) {
            artistView.setListener(this);
        }
        for (TrackAlbumView albumView : mAlbumItemViews) {
            albumView.setListener(this);
        }

        mSearchText.addTextChangedListener(new TextWatcher() {
            Boolean changeFlag = true;
            String cachedString = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                cachedString = s.toString();

                // Remove any ending white space from cached string
                while (cachedString.length() > 0 && cachedString.endsWith(" ")) {
                    cachedString = cachedString.substring(0, cachedString.length() - 1);
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String currentText = s.toString();
                // Remove any white space from end of current string
                while (currentText.length() > 0 && currentText.endsWith(" ")) {
                    currentText = currentText.substring(0, currentText.length() - 1);
                }

                // If current text  is the same as cached text flag to NOT search (as there is no change besides whitespace)
                changeFlag = !currentText.equals(cachedString);

                // If we have a timer currently running and we're flagged for a change cancel current timer
                if (mSearchTimer != null && changeFlag) {
                    Log.d(TAG, "Cancelling search timer");
                    mSearchTimer.cancel();
                }

                mCachedSearchTerm = currentText;
            }

            @Override
            public void afterTextChanged(final Editable s) {
                if (changeFlag) {
                    mSongSearchComplete = false;
                    mArtistSearchComplete = false;
                    mAlbumSearchComplete = false;
                    // User typed, start timer
                    mSearchTimer = new Timer();
                    mSearchTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            sendHandlerMessage(AppConstants.HANDLER_SEARCH_BY_TEXT);
                        }
                    }, 650); // 650 ms delay before search is executed
                }
            }
        });
    }

    private void initAdapters() {
        mSongResultsAdapter = new SearchTrackAdapter();
        mSongResultsAdapter.setListener(this);
        mAlbumSongAdapter = new SearchAlbumTrackAdapter();
        mAlbumSongAdapter.setListener(this);
        mAlbumCardAdapter = new AlbumCardAdapter();
    }

    private void searchByText(String searchText) {
        // TODO: Searching this way is slow and not user friendly. Consider doing something to speed this up
        Log.d(TAG, "Searching for: " + searchText);

        // No search text. Clear/Hide everything
        if (searchText.isEmpty()) {
            clearSearchResults();
            mResultsScrollView.setVisibility(View.GONE);
            mNoResultsText.setVisibility(View.GONE);
        } else {
            // Create options to set limit for search results to 50 items
            Map<String, Object> options = new HashMap<>();
            options.put(SpotifyService.LIMIT, AppConstants.SPOTIFY_SEARCH_LIMIT);

            // Get results from spotify
            mSpotifyService.searchTracks(searchText, options, mSongsCallback);
            mSpotifyService.searchArtists(searchText, options, mArtistsCallback);
            mSpotifyService.searchAlbums(searchText, options, mAlbumsCallback);
        }
    }

    private void showResultsView() {
        showSongResults();
        showArtistResults();
        showAlbumResults();

        mResultsScrollView.setVisibility(View.VISIBLE);
        mNoResultsText.setVisibility(View.GONE);

        // TODO: Below code is for programmatically adding result views. Consider removing if we won't use
//        ConstraintSet constraints = new ConstraintSet();
//        for (int i = 0; i < 3; i++) {
//            if (mResultTrackList.size() > i) {
//                Track trackToShow = mResultTrackList.get(i);
//                TrackAlbumView newView = new TrackAlbumView(this);
//                newView.setListener(this);
//                newView.setUri(trackToShow.uri);
//                newView.setArtistName(DisplayUtils.getTrackArtistString(trackToShow));
//                newView.setName(trackToShow.name);
//                if (trackToShow.album.images.size() > 0) {
//                    newView.setArtistImage(trackToShow.album.images.get(0).url);
//                }
//                newView.setContentDescription("track_view");
//                ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT);
//                newView.setLayoutParams(layoutParams);
//                newView.setId(View.generateViewId());
//                mBaseResultLayout.addView(newView, i + 1);
//
//                constraints.clone(mBaseResultLayout);
//                constraints.constrainDefaultWidth(newView.getId(), ConstraintSet.MATCH_CONSTRAINT_SPREAD);
//                constraints.connect(newView.getId(), ConstraintSet.TOP, mBaseResultLayout.getChildAt(i).getId(), ConstraintSet.BOTTOM, 0);
//                constraints.connect(newView.getId(), ConstraintSet.START, mBaseResultLayout.getId(), ConstraintSet.START, 0);
//                constraints.connect(newView.getId(), ConstraintSet.END, mBaseResultLayout.getId(), ConstraintSet.END, 0);
//                constraints.applyTo(mBaseResultLayout);
//            }
//        }
//        constraints.clone(mBaseResultLayout);
//        constraints.connect(mViewAllSongs.getId(), ConstraintSet.TOP, mBaseResultLayout.getChildAt(mBaseResultLayout.indexOfChild(mViewAllSongs) - 1).getId(), ConstraintSet.BOTTOM, 0);
//        constraints.applyTo(mBaseResultLayout);
    }

    private void showSongResults() {
        if (mResultTrackList.size() > 0) {
            for (int i = 0; i < 3; i++) {
                // Load song information
                TrackAlbumView trackView = mSongItemViews.get(i);
                if (mResultTrackList.size() > i) {
                    Track trackToShow = mResultTrackList.get(i);

                    trackView.setName(trackToShow.name);
                    trackView.setArtistName(DisplayUtils.getTrackArtistString(trackToShow));
                    if (trackToShow.album.images.size() > 0) {
                        trackView.setArtistImage(trackToShow.album.images.get(0).url);
                    }
                    trackView.setUri(trackToShow.uri);
                    trackView.setVisibility(View.VISIBLE);
                } else {
                    trackView.setVisibility(View.GONE);
                }
            }
            // Reshow header
            mSongsHeader.setVisibility(View.VISIBLE);
            // If more than 3 songs show view all songs button
            mViewAllSongs.setVisibility(mResultTrackList.size() > 3 ? View.VISIBLE : View.GONE);

            ButterKnife.apply(mBaseDisplayViews, DisplayUtils.VISIBLE);
        } else {
            // If we have no results hide all song views
            ButterKnife.apply(mAllSongViews, DisplayUtils.GONE);
        }
    }

    private void showArtistResults() {
        if (mResultArtistList.size() > 0) {
            for (int i = 0; i < 3; i++) {
                // Load artist information
                ArtistView artistView = mArtistItemViews.get(i);
                if (mResultArtistList.size() > i) {
                    Artist artistToShow = mResultArtistList.get(i);

                    artistView.setArtistName(artistToShow.name);
                    if (artistToShow.images.size() > 0) {
                        artistView.setArtistImage(artistToShow.images.get(0).url);
                    }
                    artistView.setArtistUri(artistToShow.uri);
                    artistView.setVisibility(View.VISIBLE);
                } else {
                    artistView.setVisibility(View.GONE);
                }
            }
            // Reshow header
            mArtistsHeader.setVisibility(View.VISIBLE);
            // If more than 3 artists show view all artists button
            mViewAllArtists.setVisibility(mResultArtistList.size() > 3 ? View.VISIBLE : View.GONE);

            ButterKnife.apply(mBaseDisplayViews, DisplayUtils.VISIBLE);
        } else {
            ButterKnife.apply(mAllArtistViews, DisplayUtils.GONE);
        }
    }

    private void showAlbumResults() {
        if (mResultAlbumList.size() > 0) {
            for (int i = 0; i < 3 && i < mResultAlbumList.size(); i++) {
                // Load album information
                TrackAlbumView albumView = mAlbumItemViews.get(i);
                if (mResultAlbumList.size() > i) {
                    Album albumToShow = mResultAlbumList.get(i);

                    albumView.setName(albumToShow.name);
                    albumView.setArtistName(DisplayUtils.getAlbumArtistString(albumToShow));
                    if (albumToShow.images.size() > 0) {
                        albumView.setArtistImage(albumToShow.images.get(0).url);
                    }
                    albumView.setUri(albumToShow.uri);
                    albumView.setVisibility(View.VISIBLE);
                } else {
                    albumView.setVisibility(View.GONE);
                }
            }
            // Reshow header
            mAlbumsHeader.setVisibility(View.VISIBLE);
            // If more than 3 albums show view all albums button
            mViewAllAlbums.setVisibility(mResultAlbumList.size() > 3 ? View.VISIBLE : View.GONE);

            ButterKnife.apply(mBaseDisplayViews, DisplayUtils.VISIBLE);
        } else {
            ButterKnife.apply(mAllAlbumViews, DisplayUtils.GONE);
        }
    }

    private boolean checkIfNoResults() {
        if (mSongSearchComplete && mArtistSearchComplete && mAlbumSearchComplete) {
            if (mResultTrackList.isEmpty() && mResultArtistList.isEmpty() && mResultAlbumList.isEmpty()) {
                showNoResultsView(mSearchText.getText().toString());
                return true;
            }
        }
        mNoResultsText.setVisibility(View.GONE);
        return false;
    }

    private void showNoResultsView(String searchString) {
        mNoResultsText.setText(String.format(getString(R.string.no_results_found), searchString));
        mResultsScrollView.setVisibility(View.GONE);
        mNoResultsText.setVisibility(View.VISIBLE);
    }

    private void clearSearchResults() {
        mResultAlbumList.clear();
        mResultArtistList.clear();
        mResultTrackList.clear();
    }

    @Override
    public void onTrackSelection(TrackSimple track) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY, track.uri);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // TODO: Implement full nav
        switch (mNavStep) {
            case BASE:
                super.onBackPressed();
                break;
            case VIEW_ALL_SONGS:
                mNavStep = SearchNavStep.BASE;

                mResultsList.setVisibility(View.GONE);
                mResultsList.removeItemDecoration(mListDecoration);
                ButterKnife.apply(mBaseDisplayViews, DisplayUtils.VISIBLE);
                setTitle(getString(R.string.search_activity_name));
                break;
            case ARTIST_SELECTED:
                mNavStep = SearchNavStep.BASE;
                break;
            case ARTIST_ALBUM_SELECTED:
                mNavStep = SearchNavStep.ARTIST_SELECTED;
                break;
            case VIEW_ALL_ARTISTS:
                mNavStep = SearchNavStep.BASE;
                break;
            case VIEW_ALL_ARTIST_SELECTED:
                mNavStep = SearchNavStep.VIEW_ALL_ARTISTS;
                break;
            case VIEW_ALL_ARTIST_ALBUM_SELECTED:
                mNavStep = SearchNavStep.VIEW_ALL_ARTIST_SELECTED;
                break;
            case ALBUM_SELECTED:
                mNavStep = SearchNavStep.BASE;

                mResultsList.removeItemDecoration(mListDecoration);
                ButterKnife.apply(mAlbumDisplayViews, DisplayUtils.GONE);
                ButterKnife.apply(mBaseDisplayViews, DisplayUtils.VISIBLE);
                setTitle(getString(R.string.search_activity_name));
                break;
            case VIEW_ALL_ALBUMS:
                mNavStep = SearchNavStep.BASE;

                mResultsList.setVisibility(View.GONE);
                ButterKnife.apply(mBaseDisplayViews, DisplayUtils.VISIBLE);
                setTitle(getString(R.string.search_activity_name));
                break;
            case VIEW_ALL_ALBUM_SELECTED:
                mNavStep = SearchNavStep.VIEW_ALL_ALBUMS;
                break;

        }
    }

    @Override
    public void onSelection(@NotNull String uri) {
        if (uri.startsWith(AppConstants.SPOTIFY_TRACK_PREFIX)) {
            Log.d(TAG, "User selected track with URI: " + uri);

            // If a track is selected add it to the queue
            Intent resultIntent = new Intent();
            resultIntent.putExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY, uri);
            setResult(RESULT_OK, resultIntent);
            finish();
        } else if (uri.startsWith(AppConstants.SPOTIFY_ALBUM_PREFIX)) {
            Log.d(TAG, "User selected album with URI: " + uri);
            // Album to display will be different depending on where we are in navigation
            switch (mNavStep) {
                case BASE:
                case VIEW_ALL_ALBUMS:
                    Album albumToDisplay = null;
                    for (Album album : mResultAlbumList) {
                        if (uri.equals(album.uri)) {
                            albumToDisplay = album;
                        }
                    }

                    if (albumToDisplay == null) {
                        Log.d(TAG, "Something went wrong. Lost album information");
                        return;
                    }

                    displayAlbum(albumToDisplay);
                    mNavStep = SearchNavStep.ALBUM_SELECTED;
                    break;
                case ARTIST_SELECTED:
                    // TODO: Display album selected from artist view
                    break;

            }
        } else if (uri.startsWith(AppConstants.SPOTIFY_ARTIST_PREFIX)) {
            Log.d(TAG, "User selected artist with URI: " + uri);
        } else {
            Log.e(TAG, "Received unexpected URI: " + uri);
        }
    }

    private void displayAlbum(Album albumToDisplay) {
        if (albumToDisplay.images.size() > 0) {
            Glide.with(this).load(albumToDisplay.images.get(0).url).into(mArtistAlbumImage);
        }

        ButterKnife.apply(mBaseDisplayViews, DisplayUtils.GONE);
        setupRecyclerViewForTracks();
        mResultsList.setAdapter(mAlbumSongAdapter);
        mAlbumSongAdapter.updateAdapter(albumToDisplay.tracks.items);
        ButterKnife.apply(mAlbumDisplayViews, DisplayUtils.VISIBLE);
        setTitle(albumToDisplay.name);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.tv_see_all_songs:
                Log.d(TAG, "User wants to see all songs");

                mNavStep = SearchNavStep.VIEW_ALL_SONGS;
                setTitle(getString(R.string.songs));
                ButterKnife.apply(mBaseDisplayViews, DisplayUtils.GONE);
                setupRecyclerViewForTracks();
                mResultsList.setAdapter(mSongResultsAdapter);
                mSongResultsAdapter.updateAdapter(mResultTrackList);
                mResultsList.setVisibility(View.VISIBLE);
                break;
            case R.id.tv_see_all_artists:
                Log.d(TAG, "User wants to see all artists");
                break;
            case R.id.tv_see_all_albums:
                Log.d(TAG, "User wants to see all albums");

                mNavStep = SearchNavStep.VIEW_ALL_ALBUMS;
                setTitle(getString(R.string.albums));
                ButterKnife.apply(mBaseDisplayViews, DisplayUtils.GONE);
                setupRecyclerViewForAlbums();
                mResultsList.setAdapter(mAlbumCardAdapter);
                mAlbumCardAdapter.updateAdapter(mResultAlbumList);
                mResultsList.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void setupRecyclerViewForTracks() {
        // Add recycler view item decoration and layout manager
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, RecyclerView.VERTICAL, false);
        mResultsList.setLayoutManager(layoutManager);
        mResultsList.addItemDecoration(mListDecoration);
    }

    private void setupRecyclerViewForAlbums() {
        // Add recylerview grid layout manager and remove decoration
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        mResultsList.removeItemDecoration(mListDecoration);
        mResultsList.setLayoutManager(layoutManager);
    }
}
