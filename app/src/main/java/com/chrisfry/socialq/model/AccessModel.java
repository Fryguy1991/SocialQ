package com.chrisfry.socialq.model;

/**
 * Model for access information
 */
public class AccessModel {
    // Client ID specific to this application
    private static final String CLIENT_ID = "0fab62a3895a4fa3aae14bc3e46bc59c";
    // Redirect URI
    private static final String REDIRECT_URI = "fryredirect://callback";
    // Access token for access to Spotify services
    private static String mAccessToken = null;

    public static void setAccessToken(String token) {
        mAccessToken = token;
    }

    public static String getAccessToken() {
        return mAccessToken;
    }
}
