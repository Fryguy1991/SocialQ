package com.chrisfry.socialq.utils;

import android.view.View;

import com.spotify.sdk.android.player.Metadata;

import java.util.List;

import butterknife.ButterKnife;
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
        List<ArtistSimple> trackArtists = track.artists;
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

    public static ButterKnife.Setter<View, Integer> getVisibilitySetter() {
        return  new ButterKnife.Setter<View, Integer>() {
            @Override
            public void set(View view, Integer value, int index) {
                view.setVisibility(value);
            }
        };
    }
}
