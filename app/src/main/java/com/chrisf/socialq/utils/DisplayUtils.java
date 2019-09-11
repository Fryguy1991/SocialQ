package com.chrisf.socialq.utils;

import android.app.Activity;
import android.view.inputmethod.InputMethodManager;

import com.chrisf.socialq.model.spotify.AlbumSimple;
import com.chrisf.socialq.model.spotify.ArtistSimple;
import com.chrisf.socialq.model.spotify.PlaylistTrack;
import com.chrisf.socialq.model.spotify.Track;

import java.util.List;

/**
 * Utils class for shared display methods
 */
public class DisplayUtils {

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
