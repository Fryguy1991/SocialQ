package com.chrisfry.socialq.userinterface.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.business.dagger.modules.SpotifyModule;
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent;
import com.chrisfry.socialq.business.dagger.modules.components.SpotifyComponent;
import com.chrisfry.socialq.R;

import butterknife.BindViews;
import butterknife.ButterKnife;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Album;
import kaaes.spotify.webapi.android.models.AlbumSimple;
import kaaes.spotify.webapi.android.models.Artist;
import kaaes.spotify.webapi.android.models.Pager;
import kaaes.spotify.webapi.android.models.Track;
import kaaes.spotify.webapi.android.models.TrackSimple;
import kaaes.spotify.webapi.android.models.Tracks;

import com.chrisfry.socialq.model.AccessModel;
import com.chrisfry.socialq.userinterface.adapters.SearchTrackListAdapter;
import com.chrisfry.socialq.userinterface.widgets.ArtistView;
import com.chrisfry.socialq.userinterface.widgets.TrackAlbumView;
import com.chrisfry.socialq.utils.DisplayUtils;

import org.jetbrains.annotations.NotNull;

/**
 * Activity for searching Spotify tracks
 */
public class SearchActivity extends AppCompatActivity implements SearchTrackListAdapter.TrackSelectionListener, TrackAlbumView.TrackAlbumListener, ArtistView.ArtistListener {
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

    // Bind lists of all views (header, items, and view all button)
    @BindViews({R.id.tv_song_heading, R.id.cv_track_1, R.id.cv_track_2, R.id.cv_track_3, R.id.tv_see_all_songs})
    List<View> mAllSongViews;
    @BindViews({R.id.tv_artist_heading, R.id.cv_artist_1, R.id.cv_artist_2, R.id.cv_artist_3, R.id.tv_see_all_artists})
    List<View> mAllArtistViews;
    @BindViews({R.id.tv_album_heading, R.id.cv_album_1, R.id.cv_album_2, R.id.cv_album_3, R.id.tv_see_all_albums})
    List<View> mAllAlbumViews;

    // Bind lists of item views
    @BindViews({R.id.cv_track_1, R.id.cv_track_2, R.id.cv_track_3})
    List<TrackAlbumView> mSongItemViews;
    @BindViews({R.id.cv_artist_1, R.id.cv_artist_2, R.id.cv_artist_3})
    List<ArtistView> mArtistItemViews;
    @BindViews({R.id.cv_album_1, R.id.cv_album_2, R.id.cv_album_3})
    List<TrackAlbumView> mAlbumItemViews;

    private EditText mSearchText;
    private TextView mNoResultsText;

    // Search result containers
    private List<Track> mResultTrackList = new ArrayList<>();
    private List<Artist> mResultArtistList = new ArrayList<>();
    private List<AlbumSimple> mResultAlbumList = new ArrayList<>();

    // Recycler view adapters
//    private SearchTrackListAdapter mSongResultsAdapter;
    // TODO: Album adapter

    private Timer mSearchTimer = null;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == AppConstants.SEARCH_BY_TEXT) {
                searchByText(mSearchText.getText().toString());
            }
        }
    };

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
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
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
    }

    private void addListeners() {
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

                // If current text  is the same as cached text flag to NOT search (as there is now change besides whitespace)
                changeFlag = !currentText.equals(cachedString);

                // If we have a timer currently running and we're flagged for a change cancel current timer
                if (mSearchTimer != null && changeFlag) {
                    Log.d(TAG, "Cancelling search timer");
                    mSearchTimer.cancel();
                }
            }

            @Override
            public void afterTextChanged(final Editable s) {
                if (changeFlag) {
                    // User typed, start timer
                    mSearchTimer = new Timer();
                    mSearchTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            Message message = mHandler.obtainMessage();
                            message.what = AppConstants.SEARCH_BY_TEXT;
                            message.sendToTarget();
                        }
                    }, 650); // 650 ms delay before search is executed
                }
            }
        });
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
            options.put(SpotifyService.LIMIT, 50);

            // Get results from spotify
            mResultArtistList = mSpotifyService.searchArtists(searchText, options).artists.items;
            mResultAlbumList = mSpotifyService.searchAlbums(searchText, options).albums.items;
            mResultTrackList = mSpotifyService.searchTracks(searchText, options).tracks.items;

            if (mResultArtistList.isEmpty() && mResultAlbumList.isEmpty() && mResultTrackList.isEmpty()) {
                // Didn't find anything.  Show no results text and hide results scrollview
                showNoResultsView(searchText);
            } else {
                showResultsView();
            }
        }
    }

    private void showResultsView() {
        if (mResultTrackList.size() > 0) {
            for (int i = 0; i < 3 && i < mResultTrackList.size(); i++) {
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
        } else {
            // If we have no results hide all song views
            ButterKnife.apply(mAllSongViews, DisplayUtils.GONE);
        }

        if (mResultArtistList.size() > 0) {
            for (int i = 0; i < 3 && i < mResultArtistList.size(); i++) {
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
        } else {
            ButterKnife.apply(mAllArtistViews, DisplayUtils.GONE);
        }

        if (mResultAlbumList.size() > 0) {
            for (int i = 0; i < 3 && i < mResultAlbumList.size(); i++) {
                // Load album information
                TrackAlbumView albumView = mAlbumItemViews.get(i);
                if (mResultAlbumList.size() > i) {
                    AlbumSimple albumToShow = mResultAlbumList.get(i);

                    albumView.setName(albumToShow.name);
//                albumView.setArtistName(albumToShow);
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
        } else {
            ButterKnife.apply(mAllAlbumViews, DisplayUtils.GONE);
        }

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
        // TODO: Re-implement when we have all our views implemented
        super.onBackPressed();
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
        } else {
            Log.e(TAG, "Received unexpected URI: " + uri);
        }
    }

    @Override
    public void onArtistSelected(@NotNull String uri) {
        Log.d(TAG, "User selected artist with URI: " + uri);
    }
}
