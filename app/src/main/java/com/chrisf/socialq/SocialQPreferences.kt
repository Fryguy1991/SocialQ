package com.chrisf.socialq

import android.content.SharedPreferences
import com.jakewharton.rxrelay2.BehaviorRelay
import io.reactivex.Observable
import javax.inject.Inject

/**
 * Interface for object responsible for storing values into Android's SharedPreferences
 */
interface SocialQPreferences {
    /**
     * Long term authorization code required to request access/refresh tokens
     */
    var authCode: String?

    /**
     * Short term access token required for all Spotify API calls
     *
     * Note: When updated with a value that is not null and not empty, new access token value will be pushed out to
     * observers on the accessTokenObservable
     */
    var accessToken: String?

    /**
     * Used to observe updates to the access tokens
     */
    val accessTokenObservable: Observable<String>

    /**
     * Long term refresh token used to fetch a new access token
     */
    var refreshToken: String?
}

class DefaultSocialQPreferences @Inject constructor(private val preferences: SharedPreferences) : SocialQPreferences {
    private val authCodeKey = "spotify_auth_code"
    override var authCode: String?
        get() = preferences.getString(authCodeKey, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(authCodeKey, value)
            editor.apply()
        }

    private val accessTokenKey = "spotify_access_token"
    override var accessToken: String?
        get() = preferences.getString(accessTokenKey, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(accessTokenKey, value)
            editor.apply()

            if (!value.isNullOrBlank()) accessTokenUpdateRelay.accept(value)
        }

    private val accessTokenUpdateRelay: BehaviorRelay<String> = BehaviorRelay.create()
    override val accessTokenObservable: Observable<String> = accessTokenUpdateRelay.hide()

    private val refreshTokenKey = "spotify_refresh_token"
    override var refreshToken: String?
        get() = preferences.getString(refreshTokenKey, null)
        set(value) {
            val editor = preferences.edit()
            editor.putString(refreshTokenKey, value)
            editor.apply()
        }
}
