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
import kaaes.spotify.webapi.android.models.AlbumSimple;
import kaaes.spotify.webapi.android.models.Artist;
import kaaes.spotify.webapi.android.models.Track;

import com.chrisfry.socialq.userinterface.adapters.BasicArtistListAdapter;
import com.chrisfry.socialq.userinterface.adapters.SearchTrackListAdapter;
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration;
import com.chrisfry.socialq.utils.ApplicationUtils;

/**
 * Activity for searching Spotify tracks
 */
public class SearchActivity extends AppCompatActivity implements SearchTrackListAdapter.TrackSelectionListener {
    private final String TAG = SearchActivity.class.getName();

    // Spotify search references
    private SpotifyService mSpotifyService;

    // UI references
    private ConstraintLayout mMainLayout;
    private View mSearchButton;
    private ViewGroup mArtistLayout;
    private ViewGroup mSongLayout;
    private ViewGroup mAlbumLayout;
    private EditText mSearchText;
    private TextView mArtistText;
    private TextView mSongText;
    private TextView mAlbumText;
    private RecyclerView mSongResults;
    private RecyclerView mArtistResults;

    @BindViews({R.id.cv_song_result_layout, R.id.cv_artist_result_layout, R.id.cv_album_result_layout})
    List<View> mResultsBaseViews;

    // Search result containers
    private List<Track> mResultTrackList = new ArrayList<>();
    private List<Artist> mResultArtistList = new ArrayList<>();
    private List<AlbumSimple> mResultAlbumList = new ArrayList<>();

    // Recycler view adapters
    private SearchTrackListAdapter mSongResultsAdapter;
    private BasicArtistListAdapter mArtistResultsAdapter = new BasicArtistListAdapter(this);
    // TODO: Album adapter

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_screen);
        ButterKnife.bind(this);

        String accessToken = ApplicationUtils.getAccessToken();
        if (accessToken == null) {
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
        mArtistLayout = findViewById(R.id.cv_artist_result_layout);
        mSongLayout = findViewById(R.id.cv_song_result_layout);
        mAlbumLayout = findViewById(R.id.cv_album_result_layout);
        mSearchText = findViewById(R.id.et_search_edit_text);
        mArtistText = mArtistLayout.findViewById(R.id.tv_result_text);
        mSongText = mSongLayout.findViewById(R.id.tv_result_text);
        mAlbumText = mAlbumLayout.findViewById(R.id.tv_result_text);

        mSongResults = mSongLayout.findViewById(R.id.rv_result_recycler_view);
        mSongResultsAdapter = new SearchTrackListAdapter(new ArrayList<Track>());
        mSongResults.setAdapter(mSongResultsAdapter);
        LinearLayoutManager songsLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mSongResults.setLayoutManager(songsLayoutManager);
        mSongResults.addItemDecoration(new QueueItemDecoration(getApplicationContext()));

        mArtistResults = mArtistLayout.findViewById(R.id.rv_result_recycler_view);
        mArtistResults.setAdapter(mArtistResultsAdapter);
        LinearLayoutManager artistsLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mArtistResults.setLayoutManager(artistsLayoutManager);
        mArtistResults.addItemDecoration(new QueueItemDecoration(getApplicationContext()));
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
                    hideKeyboard();
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

                ConstraintSet constraintSet = new ConstraintSet();
                if (isCurrentlyVisible) {
                    // If closing category show hidden categories (with results)
                    updateVisibilityBasedOnResults();
                    Log.d(TAG, "Showing all result layouts");

                    // Bottom of view should no longer be constrained
                    // Height should return to wrap content
                    constraintSet.clone(mMainLayout);
                    constraintSet.clear(view.getId(), ConstraintSet.BOTTOM);
                    constraintSet.constrainHeight(view.getId(), ConstraintSet.WRAP_CONTENT);
                } else {
                    // If clicking on a category hide other categories
                    Log.d(TAG, "Hiding all but touched layout");
                    for (View baseView : mResultsBaseViews) {
                        if (!(baseView.getId() == view.getId())) {
                            baseView.setVisibility(View.GONE);
                        }
                    }

                    // Bottom of view should be constrained to bottom of parent
                    // Height should match constraint
                    constraintSet.clone(mMainLayout);
                    constraintSet.connect(view.getId(), ConstraintSet.BOTTOM, mMainLayout.getId(), ConstraintSet.BOTTOM, 8);
                    constraintSet.constrainHeight(view.getId(), ConstraintSet.MATCH_CONSTRAINT);
                }
                constraintSet.applyTo(mMainLayout);
            }
        };

        // Add above listener to all base layouts
        for (View baseView : mResultsBaseViews) {
            baseView.setOnClickListener(showResultsClickListener);
        }

        mSongResultsAdapter.setTrackSelectionListener(this);
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

        // Update result views
        closeAnyRecyclerViews();
        updateVisibilityBasedOnResults();
        updateBaseLayoutResultCount();
        updateAdapters();
    }

    private void updateVisibilityBasedOnResults() {
        // Hide expandable views based on results
        mAlbumLayout.setVisibility(mResultAlbumList.size() == 0 ? View.GONE : View.VISIBLE);
        mArtistLayout.setVisibility(mResultArtistList.size() == 0 ? View.GONE : View.VISIBLE);
        mSongLayout.setVisibility(mResultTrackList.size() == 0 ? View.GONE : View.VISIBLE);
    }

    private void updateBaseLayoutResultCount() {
        //Update count display on base layouts
        mSongText.setText(String.format(getString(R.string.number_of_songs), mResultTrackList.size()));
        mArtistText.setText(String.format(getString(R.string.number_of_artists), mResultArtistList.size()));
        mAlbumText.setText(String.format(getString(R.string.number_of_albums), mResultAlbumList.size()));
    }

    private void updateAdapters() {
        mSongResultsAdapter.updateQueueList(mResultTrackList);
        mArtistResultsAdapter.updateAdapter(mResultArtistList);

        // TODO: Update artist and album adapters once we have them
    }

    private void closeAnyRecyclerViews() {
        for(View currentBaseView : mResultsBaseViews) {
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
    public void onTrackSelection(Track track) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY, track.uri);
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
