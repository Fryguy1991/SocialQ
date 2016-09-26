package userinterface.activities;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;

import chrisfry.spotifydj.R;
import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;

/**
 * Activity for searching Spotify tracks
 */
public class SearchActivity extends Activity {

    private SpotifyApi mApi;
    private SpotifyService mSpotifyService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        String accessToken = savedInstanceState.getString(getString(R.string.access_token_extra));
        if(accessToken == null) {
            Log.d("SearchActivity", "Invalid Access Token");
        } else {
            initSpotifySearchElements(accessToken);
        }
    }

    private void initSpotifySearchElements(String accessToken) {
        // Setup service for searching Spotify library
        mApi = new SpotifyApi();
        mApi.setAccessToken(accessToken);
        mSpotifyService = mApi.getService();
    }
}
