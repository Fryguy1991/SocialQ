package com.chrisf.socialq.network

import com.chrisf.socialq.model.*
import com.chrisf.socialq.model.spotify.*
import com.chrisf.socialq.model.spotify.pager.AlbumSimplePager
import com.chrisf.socialq.model.spotify.pager.ArtistPager
import com.chrisf.socialq.model.spotify.pager.Pager
import com.chrisf.socialq.model.spotify.pager.TrackPager
import io.reactivex.Completable
import io.reactivex.Single
import retrofit2.Response
import retrofit2.http.*
import java.util.HashMap

/**
 * Api for interacting with Spotify's web API
 */
@JvmSuppressWildcards
interface SpotifyApi {
    /**
     * ##########
     * # SEARCH #
     * ##########
     */
    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-search
    @GET("search?type=track&market=from_token")
    fun searchTracks(
            @Query("q") term: String,
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
            ) : Single<Response<TrackPager>>

    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-search
    @GET("search?type=album&market=from_token")
    fun searchAlbums(
            @Query("q") term: String,
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
    ) : Single<Response<AlbumSimplePager>>

    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-search
    @GET("search?type=artist&market=from_token")
    fun searchArtists(
            @Query("q") term: String,
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
    ) : Single<Response<ArtistPager>>

    /**
     * ###########
     * # Artists #
     * ###########
     */
    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-get-an-artist
    @GET("artists/{artist_id}")
    fun getArtist(
            @Path("artist_id") artistId: String
    ) : Single<Response<Artist>>

    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-get-an-artists-albums
    @GET("artists/{artist_id}/albums?country=from_token")
    fun getArtistAlbums(
            @Path("artist_id") artistId: String,
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
    ) : Single<Response<Pager<AlbumSimple>>>

    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-get-an-artists-top-tracks
    @GET("artists/{artist_id}/top-tracks?country=from_token")
    fun getArtistTopTracks(@Path("artist_id") artistId: String) : Single<Response<TracksObject>>

    /**
     * ##########
     * # ALBUMS #
     * ##########
     */
    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-get-an-album
    @GET("albums/{album_id}")
    fun getFullAlbum(@Path("album_id") albumId: String) : Single<Response<Album>>

    /**
     * #########
     * # USERS #
     * #########
     */
    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-get-current-users-profile
    @GET("me")
    fun getCurrentUser(): Single<Response<UserPrivate>>

    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-get-users-profile
    @GET("users/{user_id}")
    fun getUserById(@Path("user_id") userId: String) : Single<Response<UserPublic>>

    /**
     * #############
     * # PLAYLISTS #
     * #############
     */
    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-get-a-list-of-current-users-playlists
    @GET("me/playlists")
    fun getCurrentUsersPlaylists(
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
    ): Single<Response<Pager<PlaylistSimple>>>

    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-get-playlists-tracks
    @GET("playlists/{playlist_id}/tracks")
    fun getPlaylistTracks(
            @Path("playlist_id") playlistId: String,
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
            // market
    ): Single<Response<Pager<PlaylistTrack>>>

    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-create-playlist
    @Headers("Content-Type: application/json")
    @POST("users/{user_id}/playlists")
    fun createPlaylist(
            @Path("user_id") userId: String,
            @Body body: Map<String, Any> = getCreatePlaylistBody()
    ): Single<Response<Playlist>>

    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-change-playlist-details
    @Headers("Content-Type: application/json")
    @PUT("playlists/{playlist_id}")
    fun changePlaylistDetails(
            @Path("playlist_id") playlistId: String,
            @Body body: Map<String, Any>
    ): Completable

    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-add-tracks-to-playlist
    @Headers("Content-Type: application/json")
    @POST("playlists/{playlist_id}/tracks")
    fun addTrackToPlaylist(
            @Path("playlist_id") playlistId: String,
            @Query("uris") trackUri: String,
            @Query("position") position: Int? = null
    ): Single<Response<SnapshotId>>

    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-add-tracks-to-playlist
    @Headers("Content-Type: application/json")
    @POST("playlists/{playlist_id}/tracks")
    fun addTracksToPlaylist(
            @Path("playlist_id") playlistId: String,
            @Body body: Map<String, Any>
    ): Single<Response<SnapshotId>>

    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-follow-playlist
    @Headers("Content-Type: application/json")
    @PUT("playlists/{playlist_id}/followers")
    fun followPlaylist(@Path("playlist_id") playlistId: String): Completable

    // https://developer.spotify.com/documentation/web-api/reference-beta/#endpoint-unfollow-playlist
    @DELETE("playlists/{playlist_id}/followers")
    fun unfollowPlaylist(@Path("playlist_id") playlistId: String): Completable

    companion object {
        const val SPOTIFY_API_BASE_URL = "https://api.spotify.com/v1/"

        fun getCreatePlaylistBody() : Map<String, Any> {
            val requestBody = HashMap<String, Any>()
            requestBody["name"] = "SocialQ Playlist"
            requestBody["public"] = true
            requestBody["collaborative"] = false
            requestBody["description"] = "Playlist created by the SocialQ App."
            return requestBody
        }
    }
}

/**
 * Api for interacting with the SocialQ auth server
 */
interface AuthApi {
    /**
     * Send an auth code or refresh token to the SocialQ auth server.
     */
    @POST("auth")
    fun getAuthTokens(@Body request: AuthTokenRequest): Single<Response<AuthTokens>>

    /**
     * Send an auth code or refresh token to the SocialQ auth server.
     */
    @POST("auth-refresh")
    fun getAccessToken(@Body request: AuthRefreshRequest): Single<Response<AccessToken>>

    companion object {
        const val AUTH_SERVER_BASE_URL = "https://jjgsr55dt3.execute-api.us-east-1.amazonaws.com/prod/"
    }
}