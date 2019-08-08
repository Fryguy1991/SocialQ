package com.chrisf.socialq.network

import com.chrisf.socialq.model.spotify.*
import com.chrisf.socialq.model.spotify.pager.AlbumSimplePager
import com.chrisf.socialq.model.spotify.pager.ArtistPager
import com.chrisf.socialq.model.spotify.pager.Pager
import com.chrisf.socialq.model.spotify.pager.TrackPager
import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Response
import retrofit2.http.Path

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

    @GET("artists/{id}")
    fun getArtist(
            @Path("id") artistId: String
    ) : Single<Response<Artist>>

    @GET("artists/{id}/albums?country=from_token")
    fun getArtistAlbums(
            @Path("id") artistId: String,
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
    ) : Single<Response<Pager<AlbumSimple>>>

    @GET("artists/{id}/top-tracks?country=from_token")
    fun getArtistTopTracks(@Path("id") artistId: String) : Single<Response<TracksObject>>

    @GET("albums/{id}")
    fun getFullAlbum(@Path("id") albumId: String) : Single<Response<Album>>

    @GET("me")
    fun getCurrentUser(): Single<Response<UserPrivate>>

    @GET("me/playlists")
    fun getCurrentUsersPlaylsit(
            @Query("limit") limit: Int = 50,
            @Query("offset") offset: Int = 0
    ): Single<Response<Pager<PlaylistSimple>>>

    companion object {
        const val SPOTIFY_BASE_API_ENDPOINT = "https://api.spotify.com/v1/"
    }
}