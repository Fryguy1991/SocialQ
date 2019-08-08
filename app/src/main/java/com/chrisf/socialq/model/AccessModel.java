package com.chrisf.socialq.model;

import com.chrisf.socialq.model.spotify.UserPrivate;

/**
 * Model for access information (SHOULD REFACTOR TO STORE/ACCESS THIS INFO IN SHARED PREFS)
 */
public class AccessModel {
    // Authorization code for requesting an access token and refresh token
    private static String mAuthorizationCode = null;
    // Refresh token for requesting a new access token
    private static String mRefreshToken = null;
    // Access token for access to Spotify services
    private static String mAccessToken = null;
    // Time when this access token expires
    private static long mAccessExpireTime = -1;
    // Spotify user object of who has access
    private static UserPrivate mCurrentUser = null;

    public static void setAccess(String token, long expireTime) {
        if (token == null || token.isEmpty()) {
            mAccessToken = "";
            mAccessExpireTime = -1;
        } else {
            mAccessToken = token;
            mAccessExpireTime = expireTime;
        }
    }

    public static void reset() {
        mAccessToken = "";
        mAccessExpireTime = -1;
    }

    public static String getAccessToken() {
        return mAccessToken;
    }

    public static long getAccessExpireTime() {
        return mAccessExpireTime;
    }

    public static String getAuthorizationCode() {
        return mAuthorizationCode;
    }

    public static void setAuthorizationCode(String mAuthorizationCode) {
        AccessModel.mAuthorizationCode = mAuthorizationCode;
    }

    public static void setRefreshToken(String mRefreshToken) {
        AccessModel.mRefreshToken = mRefreshToken;
    }

    public static String getRefreshToken() {
        return mRefreshToken;
    }

    public static void setCurrentUser(UserPrivate user) {
        mCurrentUser = user;
    }

    public static UserPrivate getCurrentUser() {
        return mCurrentUser;
    }
}
