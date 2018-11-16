package com.chrisfry.socialq.model;

import com.chrisfry.socialq.enums.UserType;

/**
 * Model for access information
 */
public class AccessModel {
    // Access token for access to Spotify services
    private static String mAccessToken = null;
    // Time when this access token expires
    private static long mAccessExpireTime = -1;

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
}
