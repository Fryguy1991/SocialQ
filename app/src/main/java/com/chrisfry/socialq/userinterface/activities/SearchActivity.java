package com.chrisfry.socialq.userinterface.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration;
import com.chrisfry.socialq.userinterface.widgets.SearchArtistView;
import com.chrisfry.socialq.utils.DisplayUtils;

import org.jetbrains.annotations.NotNull;

/**
 * Activity for searching Spotify tracks
 */
public class SearchActivity extends AppCompatActivity implements SearchTrackListAdapter.TrackSelectionListener, SearchArtistView.SearchArtistViewPresenter {
    private final String TAG = SearchActivity.class.getName();

    // Spotify search references
    private SpotifyService mSpotifyService;

    // UI references
    private ConstraintLayout mMainLayout;
    private View mSearchButton;
    private SearchArtistView mSearchArtistView;
    private ViewGroup mSongLayout;
    private ViewGroup mAlbumLayout;
    private EditText mSearchText;
    private TextView mSongText;
    private TextView mAlbumText;
    private RecyclerView mSongResults;

    @BindViews({R.id.cv_song_result_layout, R.id.cv_album_result_layout})
    List<View> mResultsBaseViews;

    // Search result containers
    private List<Track> mResultTrackList = new ArrayList<>();
    private List<Artist> mResultArtistList = new ArrayList<>();
    private List<AlbumSimple> mResultAlbumList = new ArrayList<>();

    // Recycler view adapters
    private SearchTrackListAdapter mSongResultsAdapter;
    // TODO: Album adapter

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_screen);
        ButterKnife.bind(this);

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
        mMainLayout = findViewById(R.id.search_main);
        mSearchButton = findViewById(R.id.btn_search);
        mSongLayout = findViewById(R.id.cv_song_result_layout);
        mAlbumLayout = findViewById(R.id.cv_album_result_layout);
        mSearchText = findViewById(R.id.et_search_edit_text);
        mSongText = mSongLayout.findViewById(R.id.tv_result_text);
        mAlbumText = mAlbumLayout.findViewById(R.id.tv_result_text);

        mSongResults = mSongLayout.findViewById(R.id.rv_result_recycler_view);
        mSongResultsAdapter = new SearchTrackListAdapter(new ArrayList<TrackSimple>());
        mSongResults.setAdapter(mSongResultsAdapter);
        LinearLayoutManager songsLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mSongResults.setLayoutManager(songsLayoutManager);
        mSongResults.addItemDecoration(new QueueItemDecoration(getApplicationContext()));

        mSearchArtistView = findViewById(R.id.cv_artist_result_layout);
        mSearchArtistView.setPresenter(this);
    }

    private void addListeners() {
        mMainLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                view.requestFocus();
                hideKeyboard();
                return true;
            }
        });

        mSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Don't search if search text is empty
                if (!mSearchText.getText().toString().isEmpty()) {
                    mSearchText.clearFocus();
                    searchByText(mSearchText.getText().toString());
                    mSongResults.setVisibility(View.GONE);
                }
            }
        });
        View.OnClickListener showResultsClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                View resultsView = view.findViewById(R.id.rv_result_recycler_view);
                boolean isCurrentlyVisible = resultsView.getVisibility() == View.VISIBLE;

                // Hide recycler view and rotate arrow
                resultsView.setVisibility(isCurrentlyVisible ? View.GONE : View.VISIBLE);
                view.findViewById(R.id.iv_result_arrow).setRotation(isCurrentlyVisible ? 0 : 180);

                if (isCurrentlyVisible) {
                    // If closing category show hidden categories (with results)
                    closeResultLayout(view);
                } else {
                    // If clicking on a category hide other categories
                    expandResultsView(view);
                }
            }
        };

        // Add above listener to all base layouts
        for (View baseView : mResultsBaseViews) {
            baseView.setOnClickListener(showResultsClickListener);
        }

        mSongResultsAdapter.setTrackSelectionListener(this);
    }

    private void expandResultsView(View view) {
        ConstraintSet constraintSet = new ConstraintSet();
        Log.d(TAG, "Hiding all but touched layout");
        for (View baseView : mResultsBaseViews) {
            if (!(baseView.getId() == view.getId())) {
                baseView.setVisibility(View.GONE);
            }
        }

        // Bottom of view should be constrained to bottom of parent
        // Height should match constraint
        constraintSet.clone(mMainLayout);
        constraintSet.connect(
                view.getId(),
                ConstraintSet.BOTTOM, mMainLayout.getId(),
                ConstraintSet.BOTTOM,
                DisplayUtils.convertDpToPixels(this,8));
        constraintSet.constrainHeight(view.getId(), ConstraintSet.MATCH_CONSTRAINT);
        constraintSet.applyTo(mMainLayout);
    }

    private void closeResultLayout(View view) {
        ConstraintSet constraintSet = new ConstraintSet();
        updateVisibilityBasedOnResults();
        Log.d(TAG, "Showing all result layouts");

        // Bottom of view should no longer be constrained
        // Height should return to wrap content
        constraintSet.clone(mMainLayout);
        constraintSet.clear(view.getId(), ConstraintSet.BOTTOM);
        constraintSet.constrainHeight(view.getId(), ConstraintSet.WRAP_CONTENT);
        constraintSet.applyTo(mMainLayout);
    }

    private void hideKeyboard() {
        InputMethodManager inputManager = ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE));
        if (inputManager != null && getCurrentFocus() != null) {
            inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void searchByText(String searchText) {
        Log.d(TAG, "Searching for: " + searchText);

        // Create options to set limit for search results to 50 items
        Map<String, Object> options = new HashMap<>();
        options.put(SpotifyService.LIMIT, 50);

        // Get results from spotify
        mResultArtistList = mSpotifyService.searchArtists(searchText, options).artists.items;
//        mResultAlbumList = mSpotifyService.searchAlbums(searchText, options).albums.items;
        mResultTrackList = mSpotifyService.searchTracks(searchText, options).tracks.items;

        if (mResultArtistList.isEmpty() && mResultAlbumList.isEmpty() && mResultTrackList.isEmpty()) {
            // Didn't find anything.  Toast to let user know
            Toast.makeText(this, String.format(getString(R.string.no_results_found), searchText), Toast.LENGTH_LONG).show();
        } else {
            // Don't hide keyboard if no results are found
            hideKeyboard();
        }

        // Update result views
        closeAnyRecyclerViews();
        mSearchArtistView.resetSearchArtistView();
        closeResultLayout(mSearchArtistView);

        updateVisibilityBasedOnResults();

        updateBaseLayoutResultCount();
        updateAdapters();

        // Load custom artist search view with results
        mSearchArtistView.loadArtistSearchResults(mResultArtistList);
    }

    private void updateVisibilityBasedOnResults() {
        // Hide expandable views based on results
        mAlbumLayout.setVisibility(mResultAlbumList.size() == 0 ? View.GONE : View.VISIBLE);
        mSearchArtistView.setVisibility(mResultArtistList.size() == 0 ? View.GONE : View.VISIBLE);
        mSongLayout.setVisibility(mResultTrackList.size() == 0 ? View.GONE : View.VISIBLE);
    }

    private void updateBaseLayoutResultCount() {
        //Update count display on base layouts
        mSongText.setText(String.format(getString(R.string.number_of_songs), mResultTrackList.size()));
        mAlbumText.setText(String.format(getString(R.string.number_of_albums), mResultAlbumList.size()));
    }

    private void updateAdapters() {
        mSongResultsAdapter.updateQueueList(mResultTrackList);

        // TODO: Update album adapter once we have them
    }

    private void closeAnyRecyclerViews() {
        for(View currentBaseView : mResultsBaseViews) {
            // Hide recycler view and rotate arrow (if needed)
            currentBaseView.findViewById(R.id.rv_result_recycler_view).setVisibility(View.GONE);
            currentBaseView.findViewById(R.id.iv_result_arrow).setRotation(0);
            // Bottom of view should be constrained to bottom of parent
            // Height should match constraint
            ConstraintSet constraintSet = new ConstraintSet();
            constraintSet.clone(mMainLayout);
            constraintSet.clear(currentBaseView.getId(), ConstraintSet.BOTTOM);
            constraintSet.constrainHeight(currentBaseView.getId(), ConstraintSet.WRAP_CONTENT);
            constraintSet.applyTo(mMainLayout);
        }
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
    public void requestArtistResultsExpansion() {
        expandResultsView(mSearchArtistView);
    }

    @Override
    public void requestArtistResultsClosure() {
        closeResultLayout(mSearchArtistView);
    }

    @Override
    public void notifyArtistSelected(@NotNull String artistId) {
        String artistName = mSpotifyService.getArtist(artistId).name;
        Pager<Album> albums = mSpotifyService.getArtistAlbums(artistId);
        mSearchArtistView.showArtistAlbums(artistName, albums.items);
    }

    @Override
    public void notifyAlbumSelected(@NotNull String albumId) {
        Album albumToShow = mSpotifyService.getAlbum(albumId);
        mSearchArtistView.showAlbumTracks("not_implemented", albumToShow.name, albumToShow.tracks.items);
    }

    @Override
    public void notifyTopTracksSelected(@NotNull String artistId) {
        Artist artistToSHow = mSpotifyService.getArtist(artistId);
        Tracks artistTopTracks = mSpotifyService.getArtistTopTrack(artistId, "US");
        mSearchArtistView.showArtistTopTracks(artistToSHow.name, artistTopTracks.tracks);
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
        if (mSongLayout.getVisibility() == View.VISIBLE && mSongResults.getVisibility() == View.VISIBLE) {
            // If track results are showing close them
            closeAnyRecyclerViews();
        } else if (mSearchArtistView.getVisibility() == View.VISIBLE && mSearchArtistView.handleBackPressed()) {
            // mSearchArtistView.handleBackPressed will handle operation of back (if applicable)
            return;
        } else {
            super.onBackPressed();
        }
    }
}
