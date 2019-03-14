package com.chrisfry.socialq.enums

enum class SpotifyUserType(private val productType: String) {
    PREMIUM(SpotifyUserType.SPOTIFY_PREMIUM),
    FREE(SpotifyUserType.SPOTIFY_FREE),
    OPEN(SpotifyUserType.SPOTIFY_OPEN);

    companion object {
        const val SPOTIFY_PREMIUM = "premium"
        const val SPOTIFY_FREE = "free"
        const val SPOTIFY_OPEN = "open"

        fun getSpotifyUserTypeFromProductType(type: String): SpotifyUserType {
            for (userType: SpotifyUserType in SpotifyUserType.values()) {
                if (userType.productType == type) {
                    return userType
                }
            }
            // If we can't determine the user type default to free
            return FREE
        }
    }
}
