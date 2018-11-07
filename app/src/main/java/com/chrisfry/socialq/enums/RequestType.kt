package com.chrisfry.socialq.enums

enum class RequestType(val requestCode: Int) {
    SEARCH_REQUEST(1000),
    SPOTIFY_AUTHENTICATION_REQUEST(1001),
    REQUEST_ENABLE_BT(1002),
    REQUEST_DISCOVER_BT(1003),
    LOCATION_PERMISSION_REQUEST(1004),
    NONE(-1);

    companion object {
        /**
         * Retrieve a request type from a request code
         */
        fun getRequestTypeFromRequestCode(code: Int): RequestType {
            enumValues<RequestType>().forEach {
                if (code == it.requestCode) {
                    return it
                }
            }
            return NONE
        }
    }
}