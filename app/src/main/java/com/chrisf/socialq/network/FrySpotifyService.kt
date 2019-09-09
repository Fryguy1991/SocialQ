package com.chrisf.socialq.network

import com.chrisf.socialq.model.spotify.*
import com.chrisf.socialq.model.spotify.pager.AlbumSimplePager
import com.chrisf.socialq.model.spotify.pager.ArtistPager
import com.chrisf.socialq.model.spotify.pager.Pager
import com.chrisf.socialq.model.spotify.pager.TrackPager
import com.google.gson.JsonArray
import io.reactivex.Single
import retrofit2.Response
import retrofit2.http.*
import java.util.HashMap

@JvmSuppressWildcards
interface FrySpotifyService {

    @GET("search?type=track&market=from_token")
    fun searchTracks(
            @Query("q") term: String,
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
            ) : Single<Response<TrackPager>>

    @GET("search?type=album&market=from_token")
    fun searchAlbums(
            @Query("q") term: String,
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
    ) : Single<Response<AlbumSimplePager>>

    @GET("search?type=artist&market=from_token")
    fun searchArtists(
            @Query("q") term: String,
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
    ) : Single<Response<ArtistPager>>

    @GET("artists/{artist_id}")
    fun getArtist(
            @Path("artist_id") artistId: String
    ) : Single<Response<Artist>>

    @GET("artists/{artist_id}/albums?country=from_token")
    fun getArtistAlbums(
            @Path("artist_id") artistId: String,
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
    ) : Single<Response<Pager<AlbumSimple>>>

    @GET("artists/{artist_id}/top-tracks?country=from_token")
    fun getArtistTopTracks(@Path("artist_id") artistId: String) : Single<Response<TracksObject>>

    @GET("albums/{album_id}")
    fun getFullAlbum(@Path("album_id") albumId: String) : Single<Response<Album>>

    @GET("me")
    fun getCurrentUser(): Single<Response<UserPrivate>>

    @GET("me/playlists")
    fun getCurrentUsersPlaylists(
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
    ): Single<Response<Pager<PlaylistSimple>>>

    @GET("playlists/{playlist_id}/tracks")
    fun getPlaylistTracks(
            @Path("playlist_id") playlistId: String,
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
            // market
    ): Single<Response<Pager<PlaylistTrack>>>

    @GET("users/{user_id}")
    fun getUserById(@Path("user_id") userId: String) : Single<Response<UserPublic>>

    @Headers("Content-Type: application/json")
    @POST("users/{user_id}/playlists")
    fun createSocialQPlaylist(
            @Path("user_id") userId: String,
            @Body body: Map<String, Any> = getCreatePlaylistBody()
    ): Single<Response<Playlist>>

    @Headers("Content-Type: application/json")
    @PUT("playlists/{playlist_id}")
    fun changePlaylistDetails(
            @Path("playlist_id") playlistId: String,
            @Body body: Map<String, Any>
    ): Single<Response<Void>>

    @Headers("Content-Type: application/json")
    @POST("playlists/{playlist_id}/tracks")
    fun addTrackToPlaylist(
            @Path("playlist_id") playlistId: String,
            @Query("uris") trackUri: String,
            @Query("position") position: Int? = null
    ): Single<Response<SnapshotId>>

    @Headers("Content-Type: application/json")
    @POST("playlists/{playlist_id}/tracks")
    fun addTracksToPlaylist(
            @Path("playlist_id") playlistId: String,
            @Body body: Map<String, Any>
    ): Single<Response<SnapshotId>>

    @Headers("Content-Type: application/json")
    @PUT("playlists/{playlist_id}/followers")
    fun followPlaylist(@Path("playlist_id") playlistId: String): Single<Response<Void>>

    @DELETE("playlists/{playlist_id}/followers")
    fun unfollowPlaylist(@Path("playlist_id") playlistId: String): Single<Response<Void>>

    companion object {
        const val SPOTIFY_BASE_API_ENDPOINT = "https://api.spotify.com/v1/"

        fun getCreatePlaylistBody() : Map<String, Any> {
            val requestBody = HashMap<String, Any>()
            requestBody["name"] = "SocialQ Playlist"
            requestBody["public"] = true
            requestBody["collaborative"] = false
            requestBody["description"] = "Playlist created by the SocialQ App."
            return requestBody
        }

        fun getPlaylistDetailsBody(playlistName: String) : Map<String, Any> {
            val requestBody = HashMap<String, Any>()
            requestBody["name"] = playlistName
            return requestBody
        }

        fun getAddTracksToPlaylistBody(tracksArray: JsonArray, position: Int? = null) : Map<String, Any>{
            val requestBody = HashMap<String, Any>()
            requestBody["uris"] = tracksArray
            if (position != null) {
                requestBody["position"] = position
            }
            return requestBody
        }
    }
}