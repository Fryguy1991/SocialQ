package com.chrisfry.socialq.model;

import com.chrisfry.socialq.enums.UserType;

/**
 * Model for access information
 */
public class AccessModel {
    // Access token for access to Spotify services
    private static String mAccessToken = null;
    // Access type for the given access token
    private static UserType mAccessType = UserType.NONE;
    // Time when this access token expires
    private static long mAccessExpireTime = -1;

    public static void setAccess(String token, UserType type, long expireTime) {
        if (token.isEmpty() || type == UserType.NONE) {
            mAccessToken = "";
            mAccessType = UserType.NONE;
            mAccessExpireTime = -1;
        } else {
            mAccessToken = token;
            mAccessType = type;
            mAccessExpireTime = expireTime;
        }
    }

    public static void reset() {
        mAccessToken = "";
        mAccessType = UserType.NONE;
        mAccessExpireTime = -1;
    }

    public static String getAccessToken() {
        return mAccessToken;
    }

    public static UserType getAccessType() {
        return mAccessType;
    }

    public static long getAccessExpireTime() {
        return mAccessExpireTime;
    }
}
