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
    public static final String REDIRECT_URI = "fryredirect://callback";

    // Constant name for base user id
    public static final String BASE_USER_ID = "social_queue_base_user";

    // Intent extra key strings
    public static final String SEARCH_RESULTS_EXTRA_KEY = "search_results_key";
    public static final String BT_DEVICE_ADDRESS_EXTRA_KEY = "device_address_key";
    public static final String BT_DEVICE_EXTRA_KEY = "device_key";
    public static final String ND_ENDPOINT_ID_EXTRA_KEY = "endpoint_id_key";
    public static final String SERVICE_PLAYLIST_ID_KEY = "playlist_key";
    public static final String QUEUE_TITLE_KEY = "queue_title_key";
    public static final String FAIR_PLAY_KEY = "fair_play_key";

    // Message Strings
    public static final String PLAYLIST_ID_MESSAGE = "#PLAYLIST_ID#";
    public static final String UPDATE_QUEUE_MESSAGE = "#QUEUE_UPDATED#";
    public static final String SONG_REQUEST_MESSAGE = "#SONG_REQUEST#";
    public static final String HOST_USER_ID_MESSAGE = "#HOST_USER_ID#";
    public static final String CLIENT_USER_ID_MESSAGE = "#CLIENT_USER_ID#";
    public static final String SONG_REQUEST_MESSAGE_FORMAT = SONG_REQUEST_MESSAGE + "%1$s" + CLIENT_USER_ID_MESSAGE + "%2$s";
    public static final String INITIATE_CLIENT_MESSAGE_FORMAT = HOST_USER_ID_MESSAGE + "%1$s" + PLAYLIST_ID_MESSAGE + "%2$s" + UPDATE_QUEUE_MESSAGE + "%3$s";

    // Spotify search limits
    public static final int SPOTIFY_SEARCH_LIMIT = 50;
    public static final int PLAYLIST_LIMIT = 50;

    // START REGULAR EXPRESSIONS
    // Regex for notifying client queue is updated
    public static final String UPDATE_QUEUE_REGEX = UPDATE_QUEUE_MESSAGE + "(\\S+)";
    // Regex for initiating client
    public static final String INITIATE_CLIENT_REGEX = HOST_USER_ID_MESSAGE + "(\\S+)" + PLAYLIST_ID_MESSAGE + "(\\S+)" + UPDATE_QUEUE_MESSAGE + "([0-9+])";
    // Regex for track request messages (Example: #SONG_REQUEST#spotify:track:6qtg4gz3DhqOHL5BHtSQw8#CLIENT_USER_ID#fry_dev_1
    public static final String FULL_SONG_REQUEST_REGEX = SONG_REQUEST_MESSAGE + "(spotify:track:\\S+)" + CLIENT_USER_ID_MESSAGE + "(\\S+)";

    // Spotify URL Regexs
    public static final String URL_TRACK_SEARCH = "https:\\/\\/api.spotify.com\\/v1\\/search\\?type=track&q=([^&]+).+";
    // Example:  https://api.spotify.com/v1/search?type=track&q=avenged+sevenfold&limit=50
    public static final String URL_ARTIST_SEARCH = "https:\\/\\/api.spotify.com\\/v1\\/search\\?type=artist&q=([^&]+).+";
    // Example: https://api.spotify.com/v1/search?type=artist&q=avenged+sevenfold&limit=50
    public static final String URL_ALBUM_SEARCH = "https:\\/\\/api\\.spotify\\.com\\/v1\\/search\\?type=album&q=([^&]+).+";
    // Example: https://api.spotify.com/v1/search?type=album&q=avenged+sevenfold&limit=50
    // END REGULAR EXPRESSIONS

    // Common Strings
    public static final String INVALID = "INVALID";

    // Handler Message Types
    public static final int HANDLER_ACCESS_TOKEN_REFRESH = 1;
    public static final int HANDLER_SEARCH_BY_TEXT = 2;

    // Spotify URI prefixes
    public static final String SPOTIFY_ALBUM_PREFIX = "spotify:album:";
    public static final String SPOTIFY_ARTIST_PREFIX = "spotify:artist:";
    public static final String SPOTIFY_TRACK_PREFIX = "spotify:track:";
}
