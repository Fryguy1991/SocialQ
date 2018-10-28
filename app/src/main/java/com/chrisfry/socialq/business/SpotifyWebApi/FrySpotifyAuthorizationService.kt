package com.chrisfry.socialq.business.SpotifyWebApi

import com.chrisfry.socialq.model.AuthorizationRefreshResponse
import com.chrisfry.socialq.model.AuthorizationResponse
import com.google.gson.JsonObject
import org.json.JSONObject
import retrofit2.Call
import retrofit2.http.*

interface FrySpotifyAuthorizationService {

    /**
     * See: https://developer.spotify.com/documentation/general/guides/authorization-guide/#authorization-code-flow
     * for access token refresh information
     */
    @Headers("Authorization: Basic " + "MGZhYjYyYTM4OTVhNGZhM2FhZTE0YmMzZTQ2YmM1OWM6MGIyMmNhZjlhNDY3NGFkZWExOGM0Yjk3N2Q4NWEyMDk" + "=")
//    @Headers("Content-Type: application/x-www-form-urlencoded")
    @POST("api/token")
    fun getRefreshedToken(@Body options : Map<String, String>) : Call<AuthorizationRefreshResponse>

    @GET("authorize")
    fun getAuthorizationToken(@QueryMap options : Map<String, String>) : Call<String>
}