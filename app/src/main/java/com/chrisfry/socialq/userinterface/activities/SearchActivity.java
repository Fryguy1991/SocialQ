package com.chrisfry.socialq.userinterface.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
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

import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindViews;
import butterknife.ButterKnife;
import butterknife.ViewCollections;
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
import com.chrisfry.socialq.userinterface.views.AlbumCardView;
import com.chrisfry.socialq.userinterface.views.ArtistView;
import com.chrisfry.socialq.userinterface.views.QueueItemDecoration;
import com.chrisfry.socialq.userinterface.views.TextViewWithUri;
import com.chrisfry.socialq.userinterface.views.TrackAlbumView;
import com.chrisfry.socialq.utils.DisplayUtils;

import org.jetbrains.annotations.NotNull;

/**
 * Activity for searching Spotify tracks
 */
public class SearchActivity extends BaseActivity implements ISearchView, ISpotifySelectionListener,
        ISpotifySelectionPositionListener, View.OnClickListener {
    private final String TAG = SearchActivity.class.getName();

    // Reference to search presenter
    private ISearchPresenter presenter = new SearchPresenter();

    // UI references
    private ScrollView mResultsScrollView;
    private View mSongsHeader;
    private View mArtistsHeader;
    private View mAlbumsHeader;
    private View mViewAllSongs;
    private View mViewAllArtists;
    private View mViewAllAlbums;
    private View mViewAllArtistAlbums;
    private RecyclerView mResultsList;
    private QueueItemDecoration mListDecoration;
    private EditText mSearchEditText;
    private TextView mNoResultsText;
    private ImageView mAlbumImage;
    private ImageView mArtistImage;

    // Bind lists of all views (header, items, and view all button)
    @BindViews({R.id.tv_song_heading, R.id.cv_track_1, R.id.cv_track_2, R.id.cv_track_3, R.id.tv_see_all_songs})
    List<View> mAllSongViews;

    @BindViews({R.id.tv_artist_heading, R.id.cv_artist_1, R.id.cv_artist_2, R.id.cv_artist_3, R.id.tv_see_all_artists})
    List<View> mAllArtistViews;

    @BindViews({R.id.tv_album_heading, R.id.cv_album_1, R.id.cv_album_2, R.id.cv_album_3, R.id.tv_see_all_albums})
    List<View> mAllAlbumViews;

    @BindViews({R.id.tv_top_track_header, R.id.tv_top_track_1, R.id.tv_top_track_2, R.id.tv_top_track_3,
            R.id.tv_top_track_4, R.id.tv_top_track_5})
    List<TextView> mTopTrackViews;

    @BindViews({R.id.tv_artist_album_header, R.id.cv_artist_album_1, R.id.cv_artist_album_2, R.id.cv_artist_album_3,
            R.id.cv_artist_album_4, R.id.tv_see_all_artist_albums})
    List<View> mArtistAlbumViews;

    // Views for different nav states
    @BindViews({R.id.tv_song_heading, R.id.cv_track_1, R.id.cv_track_2, R.id.cv_track_3, R.id.tv_see_all_songs,
            R.id.tv_artist_heading, R.id.cv_artist_1, R.id.cv_artist_2, R.id.cv_artist_3, R.id.tv_see_all_artists,
            R.id.tv_album_heading, R.id.cv_album_1, R.id.cv_album_2, R.id.cv_album_3, R.id.tv_see_all_albums})
    List<View> mBaseDisplayViews;

    @BindViews({R.id.iv_album_layout_image, R.id.rv_search_results})
    List<View> mAlbumDisplayViews;

    @BindViews({R.id.iv_artist_layout_image, R.id.tv_top_track_header, R.id.tv_top_track_1, R.id.tv_top_track_2,
            R.id.tv_top_track_3, R.id.tv_top_track_4, R.id.tv_top_track_5, R.id.tv_artist_album_header,
            R.id.cv_artist_album_1, R.id.cv_artist_album_2, R.id.cv_artist_album_3, R.id.cv_artist_album_4,
            R.id.tv_see_all_artist_albums})
    List<View> mArtistDisplayViews;

    // Bind lists of item views
    @BindViews({R.id.cv_track_1, R.id.cv_track_2, R.id.cv_track_3})
    List<TrackAlbumView> mSongItemViews;
    @BindViews({R.id.cv_artist_1, R.id.cv_artist_2, R.id.cv_artist_3})
    List<ArtistView> mArtistItemViews;
    @BindViews({R.id.cv_album_1, R.id.cv_album_2, R.id.cv_album_3})
    List<TrackAlbumView> mAlbumItemViews;
    @BindViews({R.id.tv_top_track_1, R.id.tv_top_track_2, R.id.tv_top_track_3, R.id.tv_top_track_4, R.id.tv_top_track_5})
    List<TextViewWithUri> mTopTrackItemViews;
    @BindViews({R.id.cv_artist_album_1, R.id.cv_artist_album_2, R.id.cv_artist_album_3, R.id.cv_artist_album_4})
    List<AlbumCardView> mArtistAlbumItemViews;

    // Recycler view adapters
    private SearchTrackAdapter mSongResultsAdapter;
    private SearchAlbumTrackAdapter mAlbumSongAdapter;
    private AlbumCardAdapter mAlbumCardAdapter;
    private ArtistCardAdapter mArtistCardAdapter;

    // Timer to start searches when edittext is modified
    private Timer mSearchTimer = null;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AppConstants.HANDLER_SEARCH_BY_TEXT:
                    presenter.searchByText(mSearchEditText.getText().toString());
                    break;
            }
        }
    };

    // Receiver for registered broadcasts
    private BroadcastReceiver mSearchBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // For now only action is access code update
            if (intent != null && intent.getAction() != null) {
                switch (intent.getAction()) {
                    case AppConstants.BR_INTENT_ACCESS_TOKEN_UPDATED:
                        Log.d(TAG, "Received broadcast that access token was updated, notifying presenter");
                        presenter.receiveNewAccessToken(AccessModel.getAccessToken());
                        break;
                    default:
                        Log.e(TAG, "Not expecting to receive " + intent.getAction());
                }
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

        // Setup the app toolbar
        Toolbar toolbar = findViewById(R.id.app_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setTitle(R.string.search_activity_name);
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Stop soft keyboard from pushing UI up
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        String accessToken = AccessModel.getAccessToken();
        if (accessToken == null || SystemClock.elapsedRealtime() > AccessModel.getAccessExpireTime()) {
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

        // Register to receive notifications when access token has been updated
        LocalBroadcastManager.getInstance(this).registerReceiver(mSearchBroadcastReceiver, new IntentFilter(AppConstants.BR_INTENT_ACCESS_TOKEN_UPDATED));
    }

    @Override
    protected void onDestroy() {
        // Unregister broadcast receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mSearchBroadcastReceiver);

        super.onDestroy();
    }

    private void initUi() {
        // Initialize UI elements
        mSearchEditText = findViewById(R.id.et_search_edit_text);
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
        mViewAllArtistAlbums = findViewById(R.id.tv_see_all_artist_albums);

        // Results list
        mResultsList = findViewById(R.id.rv_search_results);
        mListDecoration = new QueueItemDecoration(getApplicationContext());

        // Images used for album/artist display
        mAlbumImage = findViewById(R.id.iv_album_layout_image);
        mArtistImage = findViewById(R.id.iv_artist_layout_image);
    }

    private void addListeners() {
        // Add on click listeners to view all buttons
        mViewAllSongs.setOnClickListener(this);
        mViewAllArtists.setOnClickListener(this);
        mViewAllAlbums.setOnClickListener(this);
        mViewAllArtistAlbums.setOnClickListener(this);

        // Add listeners to artist top track items
        for (TextViewWithUri view : mTopTrackItemViews) {
            view.setListener(this);
        }

        // Add listeners to artist album items
        for (AlbumCardView view : mArtistAlbumItemViews) {
            view.setListener(this);
        }

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

        mSearchEditText.addTextChangedListener(new TextWatcher() {
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
            case R.id.tv_see_all_artist_albums:
                Log.d(TAG, "User wants to see all artist albums");

                presenter.viewAllArtistAlbumsRequest();
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
        ViewCollections.run(mAlbumDisplayViews, DisplayUtils.GONE);
        ViewCollections.run(mArtistDisplayViews, DisplayUtils.GONE);
        mNoResultsText.setVisibility(View.GONE);
        mSearchEditText.setVisibility(View.VISIBLE);
        mResultsScrollView.setVisibility(View.VISIBLE);
        mArtistImage.setImageResource(R.color.Transparent);
        mAlbumImage.setImageResource(R.color.Transparent);
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
                    } else {
                        trackView.setArtistImage("");
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
            ViewCollections.run(mAllSongViews, DisplayUtils.GONE);
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
                    } else {
                        artistView.setArtistImage("");
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
            ViewCollections.run(mAllArtistViews, DisplayUtils.GONE);
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
                    } else {
                        albumView.setArtistImage("");
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
            ViewCollections.run(mAllAlbumViews, DisplayUtils.GONE);
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
        mSearchEditText.setVisibility(View.GONE);
        mResultsScrollView.setVisibility(View.GONE);
        setupRecyclerViewWithList();
        mResultsList.setAdapter(mSongResultsAdapter);
        mSongResultsAdapter.updateAdapter(songList);
        mResultsList.setVisibility(View.VISIBLE);
    }

    @Override
    public void showAllArtists(@NotNull List<? extends Artist> artistList, int position) {
        setTitle(getString(R.string.artists));
        mArtistImage.setImageResource(R.color.Transparent);
        mSearchEditText.setVisibility(View.GONE);
        mResultsScrollView.setVisibility(View.GONE);
        ViewCollections.run(mBaseDisplayViews, DisplayUtils.GONE);
        ViewCollections.run(mArtistDisplayViews, DisplayUtils.GONE);
        setupRecyclerViewWithGrid();
        mResultsList.setAdapter(mArtistCardAdapter);
        mArtistCardAdapter.updateAdapter(artistList);
        mResultsList.setVisibility(View.VISIBLE);
        mResultsList.scrollToPosition(position);
    }

    @Override
    public void showAllAlbums(@NotNull List<? extends Album> albumList, int position) {
        setTitle(getString(R.string.albums));
        mAlbumImage.setImageResource(R.color.Transparent);
        mSearchEditText.setVisibility(View.GONE);
        mResultsScrollView.setVisibility(View.GONE);
        ViewCollections.run(mAlbumDisplayViews, DisplayUtils.GONE);
        setupRecyclerViewWithGrid();
        mResultsList.setAdapter(mAlbumCardAdapter);
        mAlbumCardAdapter.setDisplayArtistFlag(true);
        mAlbumCardAdapter.updateAdapter(albumList);
        mResultsList.setVisibility(View.VISIBLE);
        mResultsList.scrollToPosition(position);
    }

    @Override
    public void showAllArtistAlbums(@NotNull Artist artist, @NotNull List<? extends Album> albumList, int position) {
        showAllAlbums(albumList, position);
        mAlbumCardAdapter.setDisplayArtistFlag(false);
        mAlbumCardAdapter.notifyDataSetChanged();
        setTitle(String.format(getString(R.string.artist_albums), artist.name));
    }

    @Override
    public void showAlbum(@NotNull Album album) {
        ViewCollections.run(mAlbumDisplayViews, DisplayUtils.VISIBLE);
        if (album.images.size() > 0) {
            Glide.with(this).load(album.images.get(0).url).into(mAlbumImage);
        } else {
            mAlbumImage.setVisibility(View.GONE);
        }

        mSearchEditText.setVisibility(View.GONE);
        mResultsScrollView.setVisibility(View.GONE);
        setupRecyclerViewWithList();
        mResultsList.setAdapter(mAlbumSongAdapter);
        mAlbumSongAdapter.updateAdapter(album.tracks.items);
        setTitle(album.name);
    }

    @Override
    public void showArtist(@NotNull Artist artist, @NotNull List<? extends Track> topTracks, @NotNull List<? extends Album> albums) {
        mResultsScrollView.scrollTo(0, 0);
        mSearchEditText.setVisibility(View.GONE);
        mResultsScrollView.setVisibility(View.VISIBLE);
        ViewCollections.run(mBaseDisplayViews, DisplayUtils.GONE);
        ViewCollections.run(mAlbumDisplayViews, DisplayUtils.GONE);
        ViewCollections.run(mArtistDisplayViews, DisplayUtils.VISIBLE);

        if (artist.images.size() > 0) {
            Glide.with(getBaseContext()).load(artist.images.get(0).url).into(mArtistImage);
        } else {
            mArtistImage.setVisibility(View.GONE);
        }

        // Load top track information
        int i = 0;
        if (topTracks.size() > 0) {
            for (TextViewWithUri topTrackView : mTopTrackItemViews) {
                if (topTracks.size() > i) {
                    topTrackView.setText(topTracks.get(i).name);
                    topTrackView.setUri(topTracks.get(i).uri);
                    topTrackView.setVisibility(View.VISIBLE);
                } else {
                    topTrackView.setVisibility(View.GONE);
                }
                i++;
            }
        } else {
            ViewCollections.run(mTopTrackViews, DisplayUtils.GONE);
        }
        setTitle(artist.name);

        // Load artist album information
        i = 0;
        if (albums.size() > 0) {
            for (AlbumCardView albumView : mArtistAlbumItemViews) {
                if (albums.size() > i) {
                    albumView.setName(albums.get(i).name);
                    albumView.setUri(albums.get(i).uri);
                    if (albums.get(i).images.size() > 0) {
                        albumView.setImageUrl(albums.get(i).images.get(0).url);
                    } else {
                        albumView.setImageUrl("");
                    }
                } else {
                    // If we're hiding album on the end side we need to set it invisible
                    // (could cause album on start side to become large in size)
                    albumView.setVisibility(i == albums.size() && (i % 2 == 1) ? View.INVISIBLE : View.GONE);
                }
                i++;
            }
            mViewAllArtistAlbums.setVisibility(albums.size() > mArtistAlbumItemViews.size() ? View.VISIBLE : View.GONE);
        } else {
            ViewCollections.run(mArtistAlbumViews, DisplayUtils.GONE);
        }
    }

    @Override
    public void returnToArtist(@NotNull Artist artist) {
        ViewCollections.run(mAlbumDisplayViews, DisplayUtils.GONE);
        mResultsScrollView.setVisibility(View.VISIBLE);
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
