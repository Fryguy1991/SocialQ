package com.chrisfry.socialq.business;

import java.util.UUID;

/**
 * Class used to retrieve constant values
 */

public class AppConstants {
    // UUID Information
    public static final String UUID_STRING = "2c865811-87e4-498b-9a95-b961edf2440d";
    public static final UUID APPLICATION_UUID = UUID.fromString(UUID_STRING);

    public static final String AWS_SERVER_URL = "http://54.86.80.241/";

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
    public static final String ND_ENDPOINT_ID_EXTRA_KEY = "hostEndpointId";
    public static final String SERVICE_PLAYLIST_ID_KEY = "playlist_key";
    public static final String QUEUE_TITLE_KEY = "queueTitle";
    public static final String FAIR_PLAY_KEY = "isFairplay";
    public static final String BASE_PLAYLIST_ID_KEY = "basePlaylistId";
    public static final String IS_HOST_KEY = "is_socialq_host_key";

    // Message Prefix Strings
    private static final String PLAYLIST_ID_PREFIX = "#PLAYLIST_ID#";
    private static final String CURRENTLY_PLAYING_UPDATE_PREFIX = "#CURRENTLY_PLAYING#";
    private static final String SONG_REQUEST_PREFIX = "#SONG_REQUEST#";
    private static final String HOST_USER_ID_PREFIX = "#HOST_USER_ID#";
    private static final String CLIENT_USER_ID_PREFIX = "#CLIENT_USER_ID#";
    private static final String NEW_SONG_ADDED_PREFIX = "#NEW_SONG_ADDED#";

    // Message Format Strings
    public static final String SONG_REQUEST_MESSAGE_FORMAT = SONG_REQUEST_PREFIX + "%1$s" + CLIENT_USER_ID_PREFIX + "%2$s";
    public static final String INITIATE_CLIENT_MESSAGE_FORMAT = HOST_USER_ID_PREFIX + "%1$s" + PLAYLIST_ID_PREFIX + "%2$s" + CURRENTLY_PLAYING_UPDATE_PREFIX + "%3$s";
    public static final String CURRENTLY_PLAYING_UPDATE_MESSAGE_FORMAT = CURRENTLY_PLAYING_UPDATE_PREFIX + "%1$s";
    public static final String HOST_DISCONNECT_MESSAGE = "#HOST_DISCONNECT#";
    public static final String NEW_SONG_ADDED_MESSAGE_FORMAT = NEW_SONG_ADDED_PREFIX + "%1$s";

    // Format for a SocialQ Host name
    private static final String QUEUE_NAME_PREFIX = "#QUEUE_NAME#";
    private static final String OWNER_NAME_PREFIX = "#OWNER_NAME#";
    private static final String IS_FAIR_PLAY_PREFIX = "#IS_FAIR_PLAY#";
    public static final String FAIR_PLAY_TRUE_CHARACTER = "T";
    public static final String FAIR_PLAY_FALSE_CHARACTER = "F";
    public static final String NEARBY_HOST_NAME_FORMAT = QUEUE_NAME_PREFIX + "%1$s" + OWNER_NAME_PREFIX + "%2$s" + IS_FAIR_PLAY_PREFIX + "%3$s";

    // START REGULAR EXPRESSIONS
    // Regex for notifying clients of the currently playing playlist index
    public static final String CURRENTLY_PLAYING_UPDATE_REGEX = CURRENTLY_PLAYING_UPDATE_PREFIX + "([0-9]+)";
    // Regex for initiating client
    public static final String INITIATE_CLIENT_REGEX = HOST_USER_ID_PREFIX + "(.+)" + PLAYLIST_ID_PREFIX + "(.+)" + CURRENTLY_PLAYING_UPDATE_PREFIX + "([0-9]+)";
    // Regex for track request messages (Example: #SONG_REQUEST#spotify:track:6qtg4gz3DhqOHL5BHtSQw8#CLIENT_USER_ID#fry_dev_1
    public static final String FULL_SONG_REQUEST_REGEX = SONG_REQUEST_PREFIX + "(spotify:track:.+)" + CLIENT_USER_ID_PREFIX + "(.+)";
    // Regex for notifying clients that a track was added to the queue (match group is index of track that was added)
    public static final String NEW_SONG_ADDED_REGEX = NEW_SONG_ADDED_PREFIX + "([0-9]+)";
    // Regex for SocialQ host advertising name
    public static final String NEARBY_HOST_NAME_REGEX = QUEUE_NAME_PREFIX + "(.*)" + OWNER_NAME_PREFIX + "(.*)" + IS_FAIR_PLAY_PREFIX + "([" + FAIR_PLAY_TRUE_CHARACTER + FAIR_PLAY_FALSE_CHARACTER + "]{1})";

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
    public static final int ACCESS_SERVICE_ID = 3;

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

    // Broadcast Receiver Intent Strings
    public static final String BR_INTENT_ACCESS_TOKEN_UPDATED = "access_token_updated";
}
