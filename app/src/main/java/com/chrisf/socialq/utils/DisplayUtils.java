package com.chrisf.socialq.utils;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.inputmethod.InputMethodManager;

import com.chrisf.socialq.model.spotify.Album;
import com.chrisf.socialq.model.spotify.AlbumSimple;
import com.chrisf.socialq.model.spotify.ArtistSimple;
import com.chrisf.socialq.model.spotify.PlaylistTrack;
import com.chrisf.socialq.model.spotify.Track;
import com.chrisf.socialq.model.spotify.TrackSimple;
import com.spotify.sdk.android.player.Metadata;

import java.util.List;

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
        return getArtistStringFromListOfArtists(track.getArtists());
    }

    public static String getTrackArtistString(Track track) {
        return getArtistStringFromListOfArtists(track.getArtists());
    }

    private static String getArtistStringFromListOfArtists(List<ArtistSimple> trackArtists) {
        String artistString = "";
        for(ArtistSimple artist : trackArtists) {
            if(artistString.isEmpty()) {
                artistString = artistString.concat(artist.getName());
            } else {
                artistString = artistString.concat(", " + artist.getName());
            }
        }
        return artistString;
    }

    public static String getTrackArtistString(PlaylistTrack track) {
        return getArtistStringFromListOfArtists(track.getTrack().getArtists());
    }

    public static String getTrackArtistString(Metadata.Track track) {
        return track.artistName;
    }

    public static String getAlbumArtistString(Album album) {
        return getArtistStringFromListOfArtists(album.getArtists());
    }

    public static String getAlbumArtistString(AlbumSimple album) {
        String artistString = "";
        for(com.chrisf.socialq.model.spotify.ArtistSimple artist : album.getArtists()) {
            if(artistString.isEmpty()) {
                artistString = artistString.concat(artist.getName());
            } else {
                artistString = artistString.concat(", " + artist.getName());
            }
        }
        return artistString;
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
