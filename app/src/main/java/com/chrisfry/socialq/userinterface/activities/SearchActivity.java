package com.chrisfry.socialq.userinterface.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.business.dagger.modules.SpotifyModule;
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent;
import com.chrisfry.socialq.business.dagger.modules.components.SpotifyComponent;
import com.chrisfry.socialq.R;
import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.AlbumSimple;
import kaaes.spotify.webapi.android.models.Artist;
import kaaes.spotify.webapi.android.models.Track;
import com.chrisfry.socialq.userinterface.adapters.TrackListAdapter;
import com.chrisfry.socialq.userinterface.widgets.QueueItemDecoration;
import com.chrisfry.socialq.utils.ApplicationUtils;

/**
 * Activity for searching Spotify tracks
 */
public class SearchActivity extends Activity implements TrackListAdapter.TrackSelectionListener {

    // Spotify search references
    private SpotifyApi mApi;
    private SpotifyService mSpotifyService;

    // UI references
    private View mMainLayout;
    private View mSearchButton;
    private View mArtistLayout;
    private View mSongLayout;
    private View mAlbumLayout;
    private EditText mSearchText;
    private TextView mArtistText;
    private TextView mSongText;
    private TextView mAlbumText;
    private RecyclerView mSearchResults;

    // Search result containers
    private List<Track> mResultTrackList;
    private List<Artist> mResultArtistList;
    private List<AlbumSimple> mResultAlbumList;

    private TrackListAdapter mSearchResultsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_screen);

        String accessToken = ApplicationUtils.getAccessToken();
        if(accessToken == null) {
            Log.d("SearchActivity", "Invalid Access Token");
            Toast.makeText(this, "Invalid Access Token", Toast.LENGTH_LONG).show();
            finish();
        } else {
            initSpotifySearchElements(accessToken);
        }

        // Show up nav icon
        getActionBar().setDisplayHomeAsUpEnabled(true);

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
        mArtistLayout = findViewById(R.id.ll_artist_search_layout);
        mSongLayout = findViewById(R.id.ll_song_search_layout);
        mAlbumLayout = findViewById(R.id.ll_album_search_layout);
        mSearchText = (EditText) findViewById(R.id.et_search_edit_text);
        mArtistText = (TextView) findViewById(R.id.tv_artists_search);
        mSongText = (TextView) findViewById(R.id.tv_songs_search);
        mAlbumText = (TextView) findViewById(R.id.tv_albums_search);

        mSearchResults = (RecyclerView) findViewById(R.id.rv_search_results);
        mSearchResultsAdapter = new TrackListAdapter(new ArrayList<Track>());
        mSearchResults.setAdapter(mSearchResultsAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mSearchResults.setLayoutManager(layoutManager);
        mSearchResults.addItemDecoration(new QueueItemDecoration(getApplicationContext()));
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
                if(!mSearchText.getText().toString().isEmpty()) {
                    mSearchText.clearFocus();
                    hideKeyboard();
                    searchByText(mSearchText.getText().toString());
                    mSearchResults.setVisibility(View.GONE);
                }
            }
        });
        View.OnClickListener showResultsClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSearchResults.setVisibility(
                        mSearchResults.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        };
        mSongLayout.setOnClickListener(showResultsClickListener);

        mSearchResultsAdapter.setTrackSelectionListener(this);
    }

    private void hideKeyboard() {
        InputMethodManager inputManager = ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE));
        if (inputManager != null && getCurrentFocus() != null) {
            inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void searchByText(String searchText) {
        Log.d("SearchActivity", "Searching for: " + searchText);

        // Get results from spotify
        // TODO: Spotify service object only returns maximum of 20 items.  Change?
        mResultArtistList = mSpotifyService.searchArtists(searchText).artists.items;
        mResultAlbumList = mSpotifyService.searchAlbums(searchText).albums.items;
        mResultTrackList = mSpotifyService.searchTracks(searchText).tracks.items;

        updateUiAfterSearch();
    }

    private void updateUiAfterSearch() {
        //Hide layouts if no results. If results are found display number of each type of item
        // TODO: Re-show album and artist results and handle selection
//        if(mResultArtistList.size() > 0) {
//            mArtistLayout.setVisibility(View.VISIBLE);
//            mArtistText.setText(
//                    String.format(getString(R.string.number_of_artists), mResultArtistList.size()));
//        } else {
//            mArtistLayout.setVisibility(View.GONE);
//        }
//
//        if(mResultAlbumList.size() > 0) {
//            mAlbumLayout.setVisibility(View.VISIBLE);
//            mAlbumText.setText(
//                    String.format(getString(R.string.number_of_albums), mResultAlbumList.size()));
//        } else {
//            mAlbumLayout.setVisibility(View.GONE);
//        }

        if(mResultTrackList.size() > 0) {
            mSongLayout.setVisibility(View.VISIBLE);
            mSongText.setText(
                    String.format(getString(R.string.number_of_songs), mResultTrackList.size()));
            mSearchResultsAdapter.updateQueueList(mResultTrackList);
            mSearchResults.setVisibility(View.VISIBLE);
        } else {
            mSongLayout.setVisibility(View.GONE);
            mSearchResults.setVisibility(View.GONE);
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
        resultIntent.putExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY,
                track.uri.replace("spotify:track:", ""));
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
