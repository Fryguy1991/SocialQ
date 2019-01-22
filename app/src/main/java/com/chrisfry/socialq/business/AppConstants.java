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

    // Tags for media session
    public static final String HOST_MEDIA_SESSION_TAG = "socialq_host_media_session";
    public static final String CLIENT_MEDIA_SESSION_TAG = "socialq_client_media_session";

    // Constant name for base user id
    public static final String BASE_USER_ID = "social_queue_base_user";

    // Intent extra key strings
    public static final String SEARCH_RESULTS_EXTRA_KEY = "search_results_key";
    public static final String ND_ENDPOINT_ID_EXTRA_KEY = "endpoint_id_key";
    public static final String SERVICE_PLAYLIST_ID_KEY = "playlist_key";
    public static final String QUEUE_TITLE_KEY = "queue_title_key";
    public static final String FAIR_PLAY_KEY = "fair_play_key";
    public static final String IS_HOST_KEY = "is_socialq_host_key";

    // Message Prefix Strings
    private static final String PLAYLIST_ID_PREFIX = "#PLAYLIST_ID#";
    private static final String QUEUE_UPDATE_PREFIX = "#QUEUE_UPDATED#";
    private static final String SONG_REQUEST_PREFIX = "#SONG_REQUEST#";
    private static final String HOST_USER_ID_PREFIX = "#HOST_USER_ID#";
    private static final String CLIENT_USER_ID_PREFIX = "#CLIENT_USER_ID#";

    // Message Format String
    public static final String SONG_REQUEST_MESSAGE_FORMAT = SONG_REQUEST_PREFIX + "%1$s" + CLIENT_USER_ID_PREFIX + "%2$s";
    public static final String INITIATE_CLIENT_MESSAGE_FORMAT = HOST_USER_ID_PREFIX + "%1$s" + PLAYLIST_ID_PREFIX + "%2$s" + QUEUE_UPDATE_PREFIX + "%3$s";
    public static final String QUEUE_UPDATE_MESSAGE_FORMAT = QUEUE_UPDATE_PREFIX + "%1$s";
    public static final String HOST_DISCONNECT_MESSAGE = "#HOST_DISCONNECT#";

    // START REGULAR EXPRESSIONS
    // Regex for notifying client queue is updated
    public static final String UPDATE_QUEUE_REGEX = QUEUE_UPDATE_PREFIX + "([0-9]+)";
    // Regex for initiating client
    public static final String INITIATE_CLIENT_REGEX = HOST_USER_ID_PREFIX + "(.+)" + PLAYLIST_ID_PREFIX + "(.+)" + QUEUE_UPDATE_PREFIX + "([0-9]+)";
    // Regex for track request messages (Example: #SONG_REQUEST#spotify:track:6qtg4gz3DhqOHL5BHtSQw8#CLIENT_USER_ID#fry_dev_1
    public static final String FULL_SONG_REQUEST_REGEX = SONG_REQUEST_PREFIX + "(spotify:track:.+)" + CLIENT_USER_ID_PREFIX + "(.+)";

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

    // Spotify search limits
    public static final int SPOTIFY_SEARCH_LIMIT = 50;
    public static final int PLAYLIST_LIMIT = 50;
    public static final int PLAYLIST_TRACK_LIMIT = 100;

    // Service IDs
    public static final int HOST_SERVICE_ID = 1;
    public static final int CLIENT_SERVICE_ID = 2;

    // Auth JSON Keys
    public static final String JSON_BODY_KEY = "body";
    public static final String JSON_ACCESS_TOKEN_KEY = "access_token";
    public static final String JSON_REFRESH_TOEKN_KEY = "refresh_token";
    public static final String JSON_EXPIRES_IN_KEY = "expires_in";

    // Spotify Parameters
    public static final String PARAM_FROM_TOKEN = "from_token";

    // Notification Pending Intent Request Codes
    public static final String ACTION_REQUEST_PLAY = "socialq_notification_play";
    public static final String ACTION_REQUEST_PAUSE = "socialq_notification_pause";
    public static final String ACTION_REQUEST_NEXT = "socialq_notification_next";
    public static final String ACTION_REQUEST_PLAY_PAUSE = "socialq_notification_play_pause";
    public static final String ACTION_NOTIFICATION_SEARCH = "socialq_notification_search";
}
