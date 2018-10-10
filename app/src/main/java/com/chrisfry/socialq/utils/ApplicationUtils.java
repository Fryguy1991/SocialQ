package com.chrisfry.socialq.utils;

import android.util.Log;

import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.model.AccessModel;
import com.spotify.android.appremote.api.ConnectionParams;

import java.util.ArrayList;
import java.util.List;

import kaaes.spotify.webapi.android.models.Track;

/**
 * Utils required for information required across the application
 */
public class ApplicationUtils {
    private final static  String TAG = ApplicationUtils.class.getName();

    public static void setAccessToken(String token) {
        AccessModel.setAccessToken(token);
    }

    public static String getAccessToken() {
        return AccessModel.getAccessToken();
    }

    public static ConnectionParams getConnectionParams() {
        return new ConnectionParams.Builder(AppConstants.CLIENT_ID)
                .setRedirectUri(AppConstants.REDIRECT_URI)
                .showAuthView(true)
                .build();
    }

    public static List<String> convertQueueByteArrayToStringList(byte[] queueByteArray) {
        Log.d(TAG, "CONVERTING FROM BYTE ARRAY TO STRING URIs");
        Log.d(TAG, queueByteArray.toString());

        String byteArrayAsString = new String(queueByteArray);
        ArrayList<String> queueStringList = new ArrayList<>();

        if (byteArrayAsString.startsWith(AppConstants.ND_BEGIN_QUEUE_MESSAGE) &&
                byteArrayAsString.endsWith(AppConstants.ND_END_QUEUE_MESSAGE)) {
            // Correct start and end of string, we should have the entire queue

            byteArrayAsString = byteArrayAsString.replaceFirst(AppConstants.ND_BEGIN_QUEUE_MESSAGE, "");
            while (byteArrayAsString.contains(AppConstants.ND_TRACK_SEPARATOR_MESSAGE)) {
                int indexOfNextTrackSeparator = byteArrayAsString.indexOf(AppConstants.ND_TRACK_SEPARATOR_MESSAGE);

                // Get next track URI in string
                String trackUri = byteArrayAsString.substring(0, indexOfNextTrackSeparator);
                queueStringList.add(trackUri);

                // Cut out the retrieved track URI
                byteArrayAsString = byteArrayAsString.substring(trackUri.length());
                // Cut out the track separator
                byteArrayAsString = byteArrayAsString.replaceFirst(AppConstants.ND_TRACK_SEPARATOR_MESSAGE, "");
            }

            // This check guards against an empty string bookended by BEG and END (empty queue)
            if (byteArrayAsString.indexOf(AppConstants.ND_END_QUEUE_MESSAGE) != 0) {
                // Only last track URI and end message should remain in string
                queueStringList.add(byteArrayAsString.replace(AppConstants.ND_END_QUEUE_MESSAGE, ""));
            }

            Log.d(TAG, "STRING URI LIST");
            Log.d(TAG, queueStringList.toString());
            return queueStringList;

        } else {
            Log.d(TAG, "BYTE ARRAY IS NOT TAGGED CORRECTLY (BEG/END)");
            return queueStringList;
        }
    }

    public static String convertTrackListToQueueString(List<Track> tracks) {
        Log.d(TAG, "CONVERTING FROM TRACK LIST TO QUEUE PAYLOAD STRING");
        Log.d(TAG, tracks.toString());

        // Start queue string with begin message
        String queueString = AppConstants.ND_BEGIN_QUEUE_MESSAGE;

        int trackIndex = 0;
        while (trackIndex < tracks.size()) {
            // Add track uri to queue string
            queueString = queueString.concat(tracks.get(trackIndex).uri.replace("spotify:track:", ""));

            if (trackIndex + 1 < tracks.size()) {
                // Add track separator if not the last track
                queueString = queueString.concat(AppConstants.ND_TRACK_SEPARATOR_MESSAGE);
            }
            trackIndex++;
        }

        // End queue string with end message
        queueString = queueString.concat(AppConstants.ND_END_QUEUE_MESSAGE);

        Log.d(TAG, "PAYLOAD STRING");
        Log.d(TAG, queueString);
        return queueString;
    }
}
