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
    public static final String SERVICE_NAME = "chris.fry.socialq";

    // Intent extra key strings
    public static final String SEARCH_RESULTS_EXTRA_KEY = "search_results_key";
    public static final String BT_DEVICE_ADDRESS_EXTRA_KEY = "device_address_key";
    public static final String BT_DEVICE_EXTRA_KEY = "device_key";

    // Result activity request codes
    public static final int SEARCH_REQUEST = 1337;
    public static final int SPOTIFY_LOGIN_REQUEST = 8675309;
    public static final int REQUEST_ENABLE_BT = 12345;
    public static final int REQUEST_DISCOVER_BT = 54321;

    // Charset Name
    public static final String UTF8_CHARSET_NAME = "UTF-8";
}
