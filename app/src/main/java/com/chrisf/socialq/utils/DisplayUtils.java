package com.chrisf.socialq.utils;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.spotify.sdk.android.player.Metadata;

import java.util.List;

import androidx.annotation.NonNull;
import kaaes.spotify.webapi.android.models.Album;
import kaaes.spotify.webapi.android.models.ArtistSimple;
import kaaes.spotify.webapi.android.models.PlaylistTrack;
import kaaes.spotify.webapi.android.models.TrackSimple;

/**
 * Utils class for shared display methods
 */
public class DisplayUtils {

    /**
     * Returns an artist string format is as follows: artist1, artist2, artist3...
     *
     * @param track - track to get artist string from
     * @return - string containing artists in above format
     */
    public static String getTrackArtistString(TrackSimple track) {
        return getArtistStringFromList(track.artists);
    }

    private static String getArtistStringFromList(List<ArtistSimple> trackArtists) {
        String artistString = "";
        for(ArtistSimple artist : trackArtists) {
            if(artistString.isEmpty()) {
                artistString = artistString.concat(artist.name);
            } else {
                artistString = artistString.concat(", " + artist.name);
            }
        }
        return artistString;
    }

    public static String getTrackArtistString(PlaylistTrack track) {
        return getTrackArtistString(track.track);
    }

    public static String getTrackArtistString(Metadata.Track track) {
        return track.artistName;
    }

    public static String getAlbumArtistString(Album album) {
        return getArtistStringFromList(album.artists);
    }

    public static butterknife.Setter<View, Integer> getVisibilitySetter() {
        return new butterknife.Setter<View, Integer>() {
            @Override
            public void set(View view, Integer value, int index) {
                view.setVisibility(value);
            }
        };
    }

    /**
     * Returns a pixel value based on the conversion from DP given context's display metrics
     *
     * @param context - Context to retrieve display metrics from
     * @param dpValue - Desired DP value
     * @return - (Rounded to nearest whole) DP conversion to pixels
     */
    public static int convertDpToPixels(Context context, int dpValue) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpValue, context.getResources().getDisplayMetrics()));
    }

    public static butterknife.Action<View> GONE = new butterknife.Action<View>() {
        @Override
        public void apply(@NonNull View view, int index) {
            view.setVisibility(View.GONE);
        }
    };

    public static butterknife.Action<View> VISIBLE = new butterknife.Action<View>() {
        @Override
        public void apply(@NonNull View view, int index) {
            view.setVisibility(View.VISIBLE);
        }
    };

    public static void hideSoftKeyboard(Activity activity) {
        InputMethodManager inputMethodManager =
                (InputMethodManager) activity.getSystemService(
                        Activity.INPUT_METHOD_SERVICE);
        if (activity.getCurrentFocus() != null) {
            inputMethodManager.hideSoftInputFromWindow(
                    activity.getCurrentFocus().getWindowToken(), 0);
        }
    }
}
