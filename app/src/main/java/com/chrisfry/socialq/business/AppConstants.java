package com.chrisfry.socialq.business;

import java.util.UUID;

/**
 * Class used to retrieve constant values
 */

public class AppConstants {
    // UUID Information
    public static final String UUID_STRING = "2c865811-87e4-498b-9a95-b961edf2440d";
    public static final UUID APPLICATION_UUID = UUID.fromString(UUID_STRING);

    // Service Name
    public static final String SERVICE_NAME = "com.chrisfry.socialq";
    // Client ID specific to this application
    public static final String CLIENT_ID = "0fab62a3895a4fa3aae14bc3e46bc59c";
    // Redirect URI
    public  static final String REDIRECT_URI = "fryredirect://callback";

    // Intent extra key strings
    public static final String SEARCH_RESULTS_EXTRA_KEY = "search_results_key";
    public static final String BT_DEVICE_ADDRESS_EXTRA_KEY = "device_address_key";
    public static final String BT_DEVICE_EXTRA_KEY = "device_key";
    public static final String ND_ENDPOINT_ID_EXTRA_KEY = "endpoint_id_key";
    public static final String SERVICE_PLAYLIST_ID_KEY = "playlist_key";

    // Result activity request codes
    public static final int SEARCH_REQUEST = 1337;
    public static final int SPOTIFY_LOGIN_REQUEST = 8675309;
    public static final int REQUEST_ENABLE_BT = 12345;
    public static final int REQUEST_DISCOVER_BT = 54321;

    // Message Strings
    public static final String PLAYLIST_ID_MESSAGE = "#PLAYLIST_ID#";
    public static final String UPDATE_QUEUE_MESSAGE = "#QUEUE_UPDATED#";
    public static final String SONG_ADDED_MESSAGE = "#SONG_ADDED#";
    public static final String HOST_USER_ID_MESSAGE = "#HOST_USER_ID#";

    // Charset Name
    public static final String UTF8_CHARSET_NAME = "UTF-8";

    // Common Strings
    public static final String INVALID = "INVALID";
}
