package com.chrisfry.socialq.utils;

import com.chrisfry.socialq.model.AccessModel;

/**
 * Utils required for information required across the application
 */
public class ApplicationUtils {

    public static void setAccessToken(String token) {
        AccessModel.setAccessToken(token);
    }

    public static String getAccessToken() {
        return AccessModel.getAccessToken();
    }

    public static String getRedirectUri() {
        return AccessModel.getRedirectUri();
    }

    public static String getClientId() {
        return AccessModel.getClientId();
    }
}
