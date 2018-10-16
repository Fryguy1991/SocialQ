package com.chrisfry.socialq.utils;

import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.enums.NearbyDevicesMessage;
import com.chrisfry.socialq.model.AccessModel;
import com.spotify.android.appremote.api.ConnectionParams;

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

    /**
     * Builds a basic payload string for nearby devices.  Basic payload is designed as:
     * <Payload Prefix> + <Payload Content> (both strings)
     *
     * @param payloadPrefix - Prefix for the payload string see NearbyDevicesMessage enum
     * @param payloadContent - Content of the payload string
     * @return Basic payload string
     */
    public static String buildBasicPayload(String payloadPrefix, String payloadContent) {
        return payloadPrefix.concat(payloadContent);
    }

    /**
     * Retrieves content of a basic payload.  Basic payload is designed as:
     * <Payload Prefix> + <Payload Content> (both strings)
     *
     * @param payloadPrefix - Prefix for the payload string see NearbyDevicesMessage enum
     * @param payloadString - Basic payload string to retrieve data from
     * @return Content in the basic payload string
     */
    public static String getBasicPayloadDataFromPayloadString(String payloadPrefix, String payloadString) {
        return payloadString.replace(payloadPrefix, "");
    }

    /**
     * Determines the payload type from the given payload string
     *
     * @param payload - String of the payload who's type we are determining
     * @return Enum type of the payload (see NearbyDevicesMessage)
     */
    public static NearbyDevicesMessage getMessageTypeFromPayload(String payload) {
        if (payload.startsWith(NearbyDevicesMessage.RECEIVE_PLAYLIST_ID.getPayloadPrefix())) {
            return NearbyDevicesMessage.RECEIVE_PLAYLIST_ID;
        }
        if (payload.startsWith(NearbyDevicesMessage.QUEUE_UPDATE.getPayloadPrefix())) {
            return NearbyDevicesMessage.QUEUE_UPDATE;
        }
        if (payload.startsWith(NearbyDevicesMessage.SONG_ADDED.getPayloadPrefix())) {
            return NearbyDevicesMessage.SONG_ADDED;
        }
        if (payload.startsWith(NearbyDevicesMessage.RECEIVE_HOST_USER_ID.getPayloadPrefix())) {
            return NearbyDevicesMessage.RECEIVE_HOST_USER_ID;
        }
        return NearbyDevicesMessage.INVALID;
    }
}
