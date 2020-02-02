package com.chrisf.socialq.network

import android.content.res.Resources
import androidx.annotation.Size
import com.chrisf.socialq.R
import com.chrisf.socialq.model.spotify.*
import com.chrisf.socialq.model.spotify.pager.AlbumSimplePager
import com.chrisf.socialq.model.spotify.pager.ArtistPager
import com.chrisf.socialq.model.spotify.pager.Pager
import com.chrisf.socialq.model.spotify.pager.TrackPager
import com.chrisf.socialq.network.ApiResponse.*
import com.google.gson.JsonArray
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import retrofit2.Response
import java.lang.IllegalStateException
import java.net.SocketTimeoutException
import javax.inject.Inject

class SpotifyService @Inject constructor(
        private val spotifyApi: SpotifyApi,
        private val responseWrapper: ResponseWrapper,
        private val resources: Resources
) {
    /**
     * ##########
     * # SEARCH #
     * ##########
     */
    fun searchTracks(
            searchTerm: String,
            offset: Int = 0
    ): Single<ApiResponse<TrackPager>> {
        return responseWrapper.wrap(spotifyApi.searchTracks(searchTerm, 50, offset))
    }

    fun searchAlbums(
            searchTerm: String,
            offset: Int = 0
    ): Single<ApiResponse<AlbumSimplePager>> {
        return responseWrapper.wrap(spotifyApi.searchAlbums(searchTerm, 50, offset))
    }

    fun searchArtists(
            searchTerm: String,
            offset: Int = 0
    ): Single<ApiResponse<ArtistPager>> {
        return responseWrapper.wrap(spotifyApi.searchArtists(searchTerm, 50, offset))
    }

    /**
     * ###########
     * # Artists #
     * ###########
     */
    fun getArtist(artistId: String): Single<ApiResponse<Artist>> {
        return responseWrapper.wrap(spotifyApi.getArtist(artistId))
    }

    fun getArtistAlbums(
            artistId: String,
            offset: Int = 0
    ): Single<ApiResponse<Pager<AlbumSimple>>> {
        return responseWrapper.wrap(spotifyApi.getArtistAlbums(artistId, 50, offset))
    }

    fun getArtistTopTracks(
            artistId: String
    ): Single<ApiResponse<TracksObject>> {
        return responseWrapper.wrap(spotifyApi.getArtistTopTracks(artistId))
    }

    /**
     * ##########
     * # ALBUMS #
     * ##########
     */
    fun getFullAlbum(albumId: String): Single<ApiResponse<Album>> {
        return responseWrapper.wrap(spotifyApi.getFullAlbum(albumId))
    }

    /**
     * #########
     * # USERS #
     * #########
     */
    fun getCurrentUser(): Single<ApiResponse<UserPrivate>> {
        return responseWrapper.wrap(spotifyApi.getCurrentUser())
    }

    fun getUserById(userId: String): Single<ApiResponse<UserPublic>> {
        return responseWrapper.wrap(spotifyApi.getUserById(userId))
    }

    /**
     * #############
     * # PLAYLISTS #
     * #############
     */
    fun getCurrentUserPlaylist(offset: Int = 0): Single<ApiResponse<Pager<PlaylistSimple>>> {
        return responseWrapper.wrap(spotifyApi.getCurrentUsersPlaylists(50, offset))
    }

    fun getPlaylistTracks(
            playlistId: String,
            limit: Int = 50,
            offset: Int = 0
    ): Single<ApiResponse<Pager<PlaylistTrack>>> {
        return responseWrapper.wrap(spotifyApi.getPlaylistTracks(playlistId, limit, offset))
    }

    fun createSocialQPlaylist(userId: String): Single<ApiResponse<Playlist>> {
        val requestBody = mapOf(
                Pair("name", resources.getString(R.string.default_playlist_name)),
                Pair("public", true),
                Pair("collaborative", false),
                Pair("description", resources.getString(R.string.default_playlist_description))
        )

        return responseWrapper.wrap(spotifyApi.createPlaylist(userId, requestBody))
    }

    fun changePlaylistName(
            playlistId: String,
            playlistName: String
    ): Single<ApiResponse<Unit>> {
        val requestBody = mapOf<String, Any>(Pair("name", playlistName))

        return responseWrapper.wrapEmptyResponse(spotifyApi.changePlaylistDetails(playlistId, requestBody))
    }

    fun addTracksToPlaylist(
            playlistId: String,
            @Size(min = 1, max = 100) trackUris: List<String>,
            position: Int? = null
    ): Single<ApiResponse<SnapshotId>> {
        val requestBody = mutableMapOf<String, Any>()
        if (position != null) {
            requestBody["position"] = position
        }
        requestBody["uris"] = JsonArray().apply { trackUris.forEach { add(it) } }

        return responseWrapper.wrap(spotifyApi.addTracksToPlaylist(playlistId, requestBody.toMap()))
    }

    fun followPlaylist(playlistId: String): Single<ApiResponse<Unit>> {
        return responseWrapper.wrapEmptyResponse(spotifyApi.followPlaylist(playlistId))
    }

    fun unfollowPlaylist(playlistId: String): Single<ApiResponse<Unit>> {
        return responseWrapper.wrapEmptyResponse(spotifyApi.unfollowPlaylist(playlistId))
    }
}

class ResponseWrapper @Inject constructor() {
    fun <T> wrap(stream: Single<Response<T>>): Single<ApiResponse<T>> {
        return stream.subscribeOn(Schedulers.io())
                .map {
                    when {
                        !it.isSuccessful -> NetworkError(it.code())
                        it.body() == null -> UnknownError(IllegalStateException("Bad Response"))
                        else -> Success(it.body()!!)
                    }
                }
                .onErrorResumeNext {
                    Single.just(
                            if (it is SocketTimeoutException) {
                                NetworkTimeout
                            } else {
                                UnknownError(it)
                            }
                    )
                }
    }

    fun wrapEmptyResponse(stream: Completable): Single<ApiResponse<Unit>> {
        return stream.toSingleDefault(Success(Unit) as ApiResponse<Unit>)
                .onErrorResumeNext {
                    Single.just(
                            if (it is SocketTimeoutException) {
                                NetworkTimeout
                            } else {
                                UnknownError(it)
                            }
                    )
                }
    }
}

sealed class ApiResponse<out T> {
    data class Success<out T>(val body: T) : ApiResponse<T>()
    data class NetworkError(val code: Int) : ApiResponse<Nothing>()
    object NetworkTimeout : ApiResponse<Nothing>()
    data class UnknownError(val exception: Throwable) : ApiResponse<Nothing>()
}

enum class SpotifyErrorCodes(val code: Int) {
    NOT_MODIFIED(304),
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    TOO_MANY_REQUESTS(429),
    INTERNAL_SERVICE_ERROR(500),
    BAD_GATEWAY(502),
    SERVICE_UNAVAILABLE(503)
}