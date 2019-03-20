package com.chrisf.socialq.utils;

import com.chrisf.socialq.enums.NearbyDevicesMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utils required for information required across the application
 */
public class ApplicationUtils {
    private final static String TAG = ApplicationUtils.class.getName();

    private static ArrayList<String> mSearchResults = new ArrayList<>();

    /**
     * Determines the payload type from the given payload string
     *
     * @param payload - String of the payload who's type we are determining
     * @return Enum type of the payload (see NearbyDevicesMessage)
     */
    public static NearbyDevicesMessage getMessageTypeFromPayload(String payload) {
        for (NearbyDevicesMessage enumValue : NearbyDevicesMessage.values()) {
            Matcher matcher = Pattern.compile(enumValue.getRegex()).matcher(payload);
            if (matcher.matches()) {
                return enumValue;
            }
        }
        return NearbyDevicesMessage.INVALID;
    }

    /**
     * Sets the list of results for the host fragment to add
     *
     * @param uris - List of uris to store
     */
    public static void setSearchResults(List<String> uris) {
        mSearchResults = new ArrayList<>(uris);
    }

    /**
     * Clears the list of search results used by host fragment
     */
    public static void resetSearchResults() {
        mSearchResults.clear();
    }

    /**
     * Retrieves the list of search results
     *
     * @return - List of songs added during search fragment interaction
     */
    public static List<String> getSearchResults() {
        return mSearchResults;
    }
}
