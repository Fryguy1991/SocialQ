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

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.bumptech.glide.Glide;
import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.R;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindViews;
import butterknife.ButterKnife;
import kaaes.spotify.webapi.android.models.Album;
import kaaes.spotify.webapi.android.models.Artist;
import kaaes.spotify.webapi.android.models.Track;

import com.chrisfry.socialq.business.presenters.ISearchPresenter;
import com.chrisfry.socialq.business.presenters.SearchPresenter;
import com.chrisfry.socialq.model.AccessModel;
import com.chrisfry.socialq.userinterface.adapters.ArtistCardAdapter;
import com.chrisfry.socialq.userinterface.adapters.SearchAlbumTrackAdapter;
import com.chrisfry.socialq.userinterface.adapters.SearchTrackAdapter;
import com.chrisfry.socialq.userinterface.adapters.AlbumCardAdapter;
import com.chrisfry.socialq.userinterface.interfaces.ISearchView;
import com.chrisfry.socialq.userinterface.interfaces.ISpotifySelectionListener;
import com.chrisfry.socialq.userinterface.interfaces.ISpotifySelectionPositionListener;
import com.chrisfry.socialq.userinterface.views.ArtistView;
import com.chrisfry.socialq.userinterface.views.QueueItemDecoration;
import com.chrisfry.socialq.userinterface.views.TrackAlbumView;
import com.chrisfry.socialq.utils.DisplayUtils;

import org.jetbrains.annotations.NotNull;

/**
 * Activity for searching Spotify tracks
 */
public class SearchActivity extends AppCompatActivity implements ISearchView, ISpotifySelectionListener,
        ISpotifySelectionPositionListener, View.OnClickListener {
    private final String TAG = SearchActivity.class.getName();

    // Reference to search presenter
    private ISearchPresenter presenter = new SearchPresenter();

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
    // TODO: Include views for top tracks and first few albums. Also consider not using square image for artists.
    // Artist image size is much more erratic
    @BindViews({R.id.iv_artist_album_image})
    List<View> mArtistDisplayViews;

    // Bind lists of item views
    @BindViews({R.id.cv_track_1, R.id.cv_track_2, R.id.cv_track_3})
    List<TrackAlbumView> mSongItemViews;
    @BindViews({R.id.cv_artist_1, R.id.cv_artist_2, R.id.cv_artist_3})
    List<ArtistView> mArtistItemViews;
    @BindViews({R.id.cv_album_1, R.id.cv_album_2, R.id.cv_album_3})
    List<TrackAlbumView> mAlbumItemViews;

    // Recycler view adapters
    private SearchTrackAdapter mSongResultsAdapter;
    private SearchAlbumTrackAdapter mAlbumSongAdapter;
    private AlbumCardAdapter mAlbumCardAdapter;
    private ArtistCardAdapter mArtistCardAdapter;
    // TODO: Artist adapter

    // Timer to start searches when edittext is modified
    private Timer mSearchTimer = null;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AppConstants.HANDLER_SEARCH_BY_TEXT:
                    presenter.searchByText(mSearchText.getText().toString());
                    break;
            }
        }
    };

    private void sendHandlerMessage(int msgWhat) {
        Message message = mHandler.obtainMessage();
        message.what = msgWhat;
        message.sendToTarget();
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
            presenter.receiveNewAccessToken(accessToken);
        }

        // Show up nav icon
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initUi();
        addListeners();
        initAdapters();
        presenter.attach(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case android.R.id.home:
                presenter.backOrUpNavigation();
                return true;
            default:
                // Didn't handle item selected return false
                return false;
        }
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
            }

            @Override
            public void afterTextChanged(final Editable s) {
                if (changeFlag) {
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
        mAlbumCardAdapter.setListener(this);
        mArtistCardAdapter = new ArtistCardAdapter();
        mArtistCardAdapter.setListener(this);
    }

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

    @Override
    public void onBackPressed() {
        presenter.backOrUpNavigation();
    }

    @Override
    public void onSelection(@NotNull String uri) {
        presenter.itemSelected(uri);
    }

    @Override
    public void onSelection(@NotNull String uri, int position) {
        presenter.itemSelected(uri, position);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.tv_see_all_songs:
                Log.d(TAG, "User wants to see all songs");

                presenter.viewAllSongsRequest();
                break;
            case R.id.tv_see_all_artists:
                Log.d(TAG, "User wants to see all artists");

                presenter.viewAllArtistsRequest();
                break;
            case R.id.tv_see_all_albums:
                Log.d(TAG, "User wants to see all albums");

                presenter.viewAllAlbumsRequest();
                break;
        }
    }

    private void setupRecyclerViewWithList() {
        // Add recycler view item decoration and layout manager
        mResultsList.removeItemDecoration(mListDecoration);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, RecyclerView.VERTICAL, false);
        mResultsList.setLayoutManager(layoutManager);
        mResultsList.addItemDecoration(mListDecoration);
    }

    private void setupRecyclerViewWithGrid() {
        // Add recylerview grid layout manager and remove decoration
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        mResultsList.removeItemDecoration(mListDecoration);
        mResultsList.setLayoutManager(layoutManager);
    }

    @Override
    public void showBaseResultsView() {
        ButterKnife.apply(mAlbumDisplayViews, DisplayUtils.GONE);
        ButterKnife.apply(mArtistDisplayViews, DisplayUtils.GONE);
        mNoResultsText.setVisibility(View.GONE);
        ButterKnife.apply(mBaseDisplayViews, DisplayUtils.VISIBLE);
        setTitle(getString(R.string.search_activity_name));
    }

    @Override
    public void showBaseSongResults(@NotNull List<? extends Track> songList) {
        if (songList.size() > 0) {
            for (int i = 0; i < 3; i++) {
                // Load song information
                TrackAlbumView trackView = mSongItemViews.get(i);
                if (songList.size() > i) {
                    Track trackToShow = songList.get(i);

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
            mViewAllSongs.setVisibility(songList.size() > 3 ? View.VISIBLE : View.GONE);

            showBaseResultsView();
        } else {
            // If we have no results hide all song views
            ButterKnife.apply(mAllSongViews, DisplayUtils.GONE);
        }
    }

    @Override
    public void showBaseArtistResults(@NotNull List<? extends Artist> artistList) {
        if (artistList.size() > 0) {
            for (int i = 0; i < 3; i++) {
                // Load artist information
                ArtistView artistView = mArtistItemViews.get(i);
                if (artistList.size() > i) {
                    Artist artistToShow = artistList.get(i);

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
            mViewAllArtists.setVisibility(artistList.size() > 3 ? View.VISIBLE : View.GONE);

            showBaseResultsView();
        } else {
            ButterKnife.apply(mAllArtistViews, DisplayUtils.GONE);
        }
    }

    @Override
    public void showBaseAlbumResults(@NotNull List<? extends Album> albumList) {
        if (albumList.size() > 0) {
            for (int i = 0; i < 3 && i < albumList.size(); i++) {
                // Load album information
                TrackAlbumView albumView = mAlbumItemViews.get(i);
                if (albumList.size() > i) {
                    Album albumToShow = albumList.get(i);

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
            mViewAllAlbums.setVisibility(albumList.size() > 3 ? View.VISIBLE : View.GONE);

            showBaseResultsView();
        } else {
            ButterKnife.apply(mAllAlbumViews, DisplayUtils.GONE);
        }
    }

    @Override
    public void showNoResultsView(@NotNull String searchTerm) {
        mNoResultsText.setText(String.format(getString(R.string.no_results_found), searchTerm));
        mResultsScrollView.setVisibility(View.GONE);
        mNoResultsText.setVisibility(View.VISIBLE);
    }

    @Override
    public void showEmptyBaseView() {
        mResultsScrollView.setVisibility(View.GONE);
        mNoResultsText.setVisibility(View.GONE);
    }

    @Override
    public void showAllSongs(@NotNull List<? extends Track> songList) {
        setTitle(getString(R.string.songs));
        ButterKnife.apply(mBaseDisplayViews, DisplayUtils.GONE);
        setupRecyclerViewWithList();
        mResultsList.setAdapter(mSongResultsAdapter);
        mSongResultsAdapter.updateAdapter(songList);
        mResultsList.setVisibility(View.VISIBLE);
    }

    @Override
    public void showAllArtists(@NotNull List<? extends Artist> artistList, int position) {
        setTitle(getString(R.string.artists));
        ButterKnife.apply(mBaseDisplayViews, DisplayUtils.GONE);
        ButterKnife.apply(mArtistDisplayViews, DisplayUtils.GONE);
        setupRecyclerViewWithGrid();
        mResultsList.setAdapter(mArtistCardAdapter);
        mArtistCardAdapter.updateAdapter(artistList);
        mResultsList.setVisibility(View.VISIBLE);
        mResultsList.scrollToPosition(position);
    }

    @Override
    public void showAllAlbums(@NotNull List<? extends Album> albumList, int position) {
        setTitle(getString(R.string.albums));
        ButterKnife.apply(mBaseDisplayViews, DisplayUtils.GONE);
        ButterKnife.apply(mAlbumDisplayViews, DisplayUtils.GONE);
        setupRecyclerViewWithGrid();
        mResultsList.setAdapter(mAlbumCardAdapter);
        mAlbumCardAdapter.updateAdapter(albumList);
        mResultsList.setVisibility(View.VISIBLE);
        mResultsList.scrollToPosition(position);
    }

    @Override
    public void showAlbum(@NotNull Album album) {
        if (album.images.size() > 0) {
            Glide.with(this).load(album.images.get(0).url).into(mArtistAlbumImage);
        }

        ButterKnife.apply(mBaseDisplayViews, DisplayUtils.GONE);
        setupRecyclerViewWithList();
        mResultsList.setAdapter(mAlbumSongAdapter);
        mAlbumSongAdapter.updateAdapter(album.tracks.items);
        ButterKnife.apply(mAlbumDisplayViews, DisplayUtils.VISIBLE);
        setTitle(album.name);
    }

    @Override
    public void showArtist(@NotNull Artist artist) {
        // TODO: show artist view when implemented
        // Below only shows artist image (when selected) and changes actionbar title
        ButterKnife.apply(mBaseDisplayViews, DisplayUtils.GONE);
        mResultsList.setVisibility(View.GONE);
        ButterKnife.apply(mArtistDisplayViews, DisplayUtils.VISIBLE);
        if (artist.images.size() > 0) {
            Glide.with(this).load(artist.images.get(0).url).into(mArtistAlbumImage);
        } else {
            mArtistAlbumImage.setVisibility(View.GONE);
        }
        setTitle(artist.name);
    }

    @Override
    public void sendTrackToHost(@NotNull String uri) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(AppConstants.SEARCH_RESULTS_EXTRA_KEY, uri);
        setResult(RESULT_OK, resultIntent);
        finish();
        presenter.detach();
    }

    @Override
    public void closeSearchView() {
        super.onBackPressed();
        presenter.detach();
    }

    @Override
    public void requestNewAccessToken() {
        // TODO: Implement access token refreshing
    }

    @Override
    public void initiateView() {
        showEmptyBaseView();
    }
}
