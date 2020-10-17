package com.chrisf.socialq.utils;

import com.chrisf.socialq.enums.NearbyDevicesMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utils required for information required across the application
 */
public class ApplicationUtils {
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
}
