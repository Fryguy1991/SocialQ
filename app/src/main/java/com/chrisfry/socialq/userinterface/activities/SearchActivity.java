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
import com.chrisfry.socialq.userinterface.widgets.SearchArtistView;
import com.chrisfry.socialq.userinterface.widgets.TrackAlbumView;
import com.chrisfry.socialq.utils.DisplayUtils;

import org.jetbrains.annotations.NotNull;

/**
 * Activity for searching Spotify tracks
 */
public class SearchActivity extends AppCompatActivity implements SearchTrackListAdapter.TrackSelectionListener, SearchArtistView.SearchArtistViewPresenter, TrackAlbumView.TrackAlbumListener, ArtistView.ArtistListener {
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
    private TrackAlbumView mTrack1;
    private TrackAlbumView mTrack2;
    private TrackAlbumView mTrack3;
    private List<TrackAlbumView> mTrackViewList = new ArrayList<>();
    private TrackAlbumView mAlbum1;
    private TrackAlbumView mAlbum2;
    private TrackAlbumView mAlbum3;
    private List<TrackAlbumView> mAlbumViewList = new ArrayList<>();
    private ArtistView mArtist1;
    private ArtistView mArtist2;
    private ArtistView mArtist3;
    private List<ArtistView> mArtistViewList = new ArrayList<>();
    private Group mSongGroup;
    private Group mArtistGroup;
    private Group mAlbumGroup;

//    @BindViews({})

    //    private View mSearchButton;
//    private SearchArtistView mSearchArtistView;
//    private ViewGroup mSongLayout;
//    private ViewGroup mAlbumLayout;
    private EditText mSearchText;
    private TextView mNoResultsText;
//    private TextView mSongText;
//    private TextView mAlbumText;
//    private RecyclerView mSongResults;

//    @BindViews({R.id.cv_song_result_layout, R.id.cv_album_result_layout})
//    List<View> mResultsBaseViews;

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
//        mSearchButton = findViewById(R.id.btn_search);
//        mSongLayout = findViewById(R.id.cv_song_result_layout);
//        mAlbumLayout = findViewById(R.id.cv_album_result_layout);
        mSearchText = findViewById(R.id.et_search_edit_text);
        mNoResultsText = findViewById(R.id.tv_no_results);
//        mSongText = mSongLayout.findViewById(R.id.tv_result_text);
//        mAlbumText = mAlbumLayout.findViewById(R.id.tv_result_text);
//
//        mSongResults = mSongLayout.findViewById(R.id.rv_result_recycler_view);
//        mSongResultsAdapter = new SearchTrackListAdapter(new ArrayList<TrackSimple>());
//        mSongResults.setAdapter(mSongResultsAdapter);
//        LinearLayoutManager songsLayoutManager = new LinearLayoutManager(this, RecyclerView.VERTICAL, false);
//        mSongResults.setLayoutManager(songsLayoutManager);
//        mSongResults.addItemDecoration(new QueueItemDecoration(getApplicationContext()));
//
//        mSearchArtistView = findViewById(R.id.cv_artist_result_layout);
//        mSearchArtistView.setPresenter(this);

        mResultsScrollView = findViewById(R.id.sv_search_scroll_view);

//        mSongGroup = findViewById(R.id.group_song_views);
//        mArtistGroup = findViewById(R.id.group_artist_views);
//        mAlbumGroup = findViewById(R.id.group_album_views);

        mSongsHeader = findViewById(R.id.tv_song_heading);
        mArtistsHeader = findViewById(R.id.tv_artist_heading);
        mAlbumsHeader = findViewById(R.id.tv_album_heading);

        mViewAllSongs = findViewById(R.id.tv_see_all_songs);
        mViewAllArtists = findViewById(R.id.tv_see_all_artists);
        mViewAllAlbums = findViewById(R.id.tv_see_all_albums);

        mTrack1 = findViewById(R.id.cv_track_1);
        mTrack2 = findViewById(R.id.cv_track_2);
        mTrack3 = findViewById(R.id.cv_track_3);
        mTrackViewList.add(mTrack1);
        mTrackViewList.add(mTrack2);
        mTrackViewList.add(mTrack3);

        for (TrackAlbumView trackView : mTrackViewList) {
            trackView.setListener(this);
        }

        mAlbum1 = findViewById(R.id.cv_album_1);
        mAlbum2 = findViewById(R.id.cv_album_2);
        mAlbum3 = findViewById(R.id.cv_album_3);
        mAlbumViewList.add(mAlbum1);
        mAlbumViewList.add(mAlbum2);
        mAlbumViewList.add(mAlbum3);

        for (TrackAlbumView albumView : mAlbumViewList) {
            albumView.setListener(this);
        }

        mArtist1 = findViewById(R.id.cv_artist_1);
        mArtist2 = findViewById(R.id.cv_artist_2);
        mArtist3 = findViewById(R.id.cv_artist_3);
        mArtistViewList.add(mArtist1);
        mArtistViewList.add(mArtist2);
        mArtistViewList.add(mArtist3);

        for (ArtistView artistView : mArtistViewList) {
            artistView.setListener(this);
        }
    }

    private void addListeners() {
//        mMainLayout.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View view, MotionEvent motionEvent) {
//                view.requestFocus();
//                hideKeyboard();
//                return true;
//            }
//        });
//
//        mSearchButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                // Don't search if search text is empty
//                if (!mSearchText.getText().toString().isEmpty()) {
//                    mSearchText.clearFocus();
//                    searchByText(mSearchText.getText().toString());
//                    mSongResults.setVisibility(View.GONE);
//                }
//            }
//        });
//        View.OnClickListener showResultsClickListener = new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                View resultsView = view.findViewById(R.id.rv_result_recycler_view);
//                boolean isCurrentlyVisible = resultsView.getVisibility() == View.VISIBLE;
//
//                // Hide recycler view and rotate arrow
//                resultsView.setVisibility(isCurrentlyVisible ? View.GONE : View.VISIBLE);
//                view.findViewById(R.id.iv_result_arrow).setRotation(isCurrentlyVisible ? 0 : 180);
//
//                if (isCurrentlyVisible) {
//                    // If closing category show hidden categories (with results)
//                    closeResultLayout(view);
//                } else {
//                    // If clicking on a category hide other categories
//                    expandResultsView(view);
//                }
//            }
//        };

//        // Add above listener to all base layouts
//        for (View baseView : mResultsBaseViews) {
//            baseView.setOnClickListener(showResultsClickListener);
//        }
//
//        mSongResultsAdapter.setTrackSelectionListener(this);


        mSearchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mSearchTimer != null) {
                    mSearchTimer.cancel();
                }
            }

            @Override
            public void afterTextChanged(final Editable s) {
                // User typed, start timer
                mSearchTimer = new Timer();
                mSearchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Message message = mHandler.obtainMessage();
                        message.what = AppConstants.SEARCH_BY_TEXT;
                        message.sendToTarget();
                    }
                }, 650); // 500ms delay before search is executed
            }
        });
    }
//
//    private void expandResultsView(View view) {
//        ConstraintSet constraintSet = new ConstraintSet();
//        Log.d(TAG, "Hiding all but touched layout");
//        for (View baseView : mResultsBaseViews) {
//            if (!(baseView.getId() == view.getId())) {
//                baseView.setVisibility(View.GONE);
//            }
//        }
//
//        // Bottom of view should be constrained to bottom of parent
//        // Height should match constraint
//        constraintSet.clone(mMainLayout);
//        constraintSet.connect(
//                view.getId(),
//                ConstraintSet.BOTTOM, mMainLayout.getId(),
//                ConstraintSet.BOTTOM,
//                DisplayUtils.convertDpToPixels(this,8));
//        constraintSet.constrainHeight(view.getId(), ConstraintSet.MATCH_CONSTRAINT);
//        constraintSet.applyTo(mMainLayout);
//    }
//
//    private void closeResultLayout(View view) {
//        ConstraintSet constraintSet = new ConstraintSet();
//        updateVisibilityBasedOnResults();
//        Log.d(TAG, "Showing all result layouts");
//
//        // Bottom of view should no longer be constrained
//        // Height should return to wrap content
//        constraintSet.clone(mMainLayout);
//        constraintSet.clear(view.getId(), ConstraintSet.BOTTOM);
//        constraintSet.constrainHeight(view.getId(), ConstraintSet.WRAP_CONTENT);
//        constraintSet.applyTo(mMainLayout);
//    }

    private void hideKeyboard() {
        InputMethodManager inputManager = ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE));
        if (inputManager != null && getCurrentFocus() != null) {
            inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
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
//                Toast.makeText(this, String.format(getString(R.string.no_results_found), searchText), Toast.LENGTH_LONG).show();
                showNoResultsView(searchText);
            } else {
                showResultsView();
            }

//        // Update result views
//        closeAnyRecyclerViews();
//        mSearchArtistView.resetSearchArtistView();
//        closeResultLayout(mSearchArtistView);
//
//        updateVisibilityBasedOnResults();
//
//        updateBaseLayoutResultCount();
//        updateAdapters();
//
//        // Load custom artist search view with results
//        mSearchArtistView.loadArtistSearchResults(mResultArtistList);
        }
    }

    private void showResultsView() {
        for (int i = 0; i < 3; i++) {
            // Load track information
            TrackAlbumView trackView = mTrackViewList.get(i);
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

            // Load album information
            TrackAlbumView albumView = mAlbumViewList.get(i);
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

            // Load artist information
            ArtistView artistView = mArtistViewList.get(i);
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

        // If we have more than 3 tracks show button to see all
        if (mResultTrackList.size() > 3) {
            mViewAllSongs.setVisibility(View.VISIBLE);
        } else {
            mViewAllSongs.setVisibility(View.GONE);
        }

        // If we have more than 3 albums show button to see all
        if (mResultAlbumList.size() > 3) {
            mViewAllAlbums.setVisibility(View.VISIBLE);
        } else {
            mViewAllAlbums.setVisibility(View.GONE);
        }

        // If we have more than 3 artists show button to see all
        if (mResultArtistList.size() > 3) {
            mViewAllArtists.setVisibility(View.VISIBLE);
        } else {
            mViewAllArtists.setVisibility(View.GONE);
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

    private void clearResultsView() {
        for (int i = 0; i < mBaseResultLayout.getChildCount(); i++) {
        }
    }

    //    private void updateVisibilityBasedOnResults() {
//        // Hide expandable views based on results
//        mAlbumLayout.setVisibility(mResultAlbumList.size() == 0 ? View.GONE : View.VISIBLE);
//        mSearchArtistView.setVisibility(mResultArtistList.size() == 0 ? View.GONE : View.VISIBLE);
//        mSongLayout.setVisibility(mResultTrackList.size() == 0 ? View.GONE : View.VISIBLE);
//    }
//
//    private void updateBaseLayoutResultCount() {
//        //Update count display on base layouts
//        mSongText.setText(String.format(getString(R.string.number_of_songs), mResultTrackList.size()));
//        mAlbumText.setText(String.format(getString(R.string.number_of_albums), mResultAlbumList.size()));
//    }
//
//    private void updateAdapters() {
//        mSongResultsAdapter.updateQueueList(mResultTrackList);
//
//        // TODO: Update album adapter once we have them
//    }
//
//    private void closeAnyRecyclerViews() {
//        for(View currentBaseView : mResultsBaseViews) {
//            // Hide recycler view and rotate arrow (if needed)
//            currentBaseView.findViewById(R.id.rv_result_recycler_view).setVisibility(View.GONE);
//            currentBaseView.findViewById(R.id.iv_result_arrow).setRotation(0);
//            // Bottom of view should be constrained to bottom of parent
//            // Height should match constraint
//            ConstraintSet constraintSet = new ConstraintSet();
//            constraintSet.clone(mMainLayout);
//            constraintSet.clear(currentBaseView.getId(), ConstraintSet.BOTTOM);
//            constraintSet.constrainHeight(currentBaseView.getId(), ConstraintSet.WRAP_CONTENT);
//            constraintSet.applyTo(mMainLayout);
//        }
//    }
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
    public void requestArtistResultsExpansion() {
//        expandResultsView(mSearchArtistView);
    }

    @Override
    public void requestArtistResultsClosure() {
//        closeResultLayout(mSearchArtistView);
    }

    @Override
    public void notifyArtistSelected(@NotNull String artistId) {
        String artistName = mSpotifyService.getArtist(artistId).name;
        Pager<Album> albums = mSpotifyService.getArtistAlbums(artistId);
//        mSearchArtistView.showArtistAlbums(artistName, albums.items);
    }

    @Override
    public void notifyAlbumSelected(@NotNull String albumId) {
        Album albumToShow = mSpotifyService.getAlbum(albumId);
//        mSearchArtistView.showAlbumTracks("not_implemented", albumToShow.name, albumToShow.tracks.items);
    }

    @Override
    public void notifyTopTracksSelected(@NotNull String artistId) {
        Artist artistToSHow = mSpotifyService.getArtist(artistId);
        Tracks artistTopTracks = mSpotifyService.getArtistTopTrack(artistId, "US");
//        mSearchArtistView.showArtistTopTracks(artistToSHow.name, artistTopTracks.tracks);
    }

    @Override
    public void notifyTrackSelected(@NotNull String uri) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY, uri);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // TODO: Handle album search closing when album view is implemented.
//        if (mSongLayout.getVisibility() == View.VISIBLE && mSongResults.getVisibility() == View.VISIBLE) {
//            // If track results are showing close them
//            closeAnyRecyclerViews();
//        } else if (mSearchArtistView.getVisibility() == View.VISIBLE && mSearchArtistView.handleBackPressed()) {
//            // mSearchArtistView.handleBackPressed will handle operation of back (if applicable)
//            return;
//        } else {
        super.onBackPressed();
//        }
    }

    @Override
    public void onSelection(@NotNull String uri) {

    }

    @Override
    public void onArtistSelected(@NotNull String uri) {

    }
}
