package com.chrisf.socialq.enums

enum class RequestType(val requestCode: Int) {
    SEARCH_REQUEST(1000),
    SPOTIFY_AUTHENTICATION_REQUEST(1001),
    LOCATION_PERMISSION_REQUEST(1002),
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