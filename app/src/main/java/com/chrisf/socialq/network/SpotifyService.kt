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
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.JsonArray
import io.reactivex.Completable
import io.reactivex.Single
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject

class SpotifyService @Inject constructor(
        private val spotifyApi: SpotifyApi,
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
        return wrap(spotifyApi.searchTracks(searchTerm, 50, offset))
    }

    fun searchAlbums(
            searchTerm: String,
            offset: Int = 0
    ): Single<ApiResponse<AlbumSimplePager>> {
        return wrap(spotifyApi.searchAlbums(searchTerm, 50, offset))
    }

    fun searchArtists(
            searchTerm: String,
            offset: Int = 0
    ): Single<ApiResponse<ArtistPager>> {
        return wrap(spotifyApi.searchArtists(searchTerm, 50, offset))
    }

    /**
     * ###########
     * # Artists #
     * ###########
     */
    fun getArtist(artistId: String): Single<ApiResponse<Artist>> {
        return wrap(spotifyApi.getArtist(artistId))
    }

    fun getArtistAlbums(
            artistId: String,
            offset: Int = 0
    ): Single<ApiResponse<Pager<AlbumSimple>>> {
        return wrap(spotifyApi.getArtistAlbums(artistId, 50, offset))
    }

    fun getArtistTopTracks(
            artistId: String
    ): Single<ApiResponse<TracksObject>> {
        return wrap(spotifyApi.getArtistTopTracks(artistId))
    }

    /**
     * ##########
     * # ALBUMS #
     * ##########
     */
    fun getFullAlbum(albumId: String): Single<ApiResponse<Album>> {
        return wrap(spotifyApi.getFullAlbum(albumId))
    }

    /**
     * #########
     * # USERS #
     * #########
     */
    fun getCurrentUser(): Single<ApiResponse<UserPrivate>> {
        return wrap(spotifyApi.getCurrentUser())
    }

    fun getUserById(userId: String): Single<ApiResponse<UserPublic>> {
        return wrap(spotifyApi.getUserById(userId))
    }

    /**
     * #############
     * # PLAYLISTS #
     * #############
     */
    fun getCurrentUserPlaylist(offset: Int = 0): Single<ApiResponse<Pager<PlaylistSimple>>> {
        return wrap(spotifyApi.getCurrentUsersPlaylists(50, offset))
    }

    fun getPlaylistTracks(
            playlistId: String,
            limit: Int = 50,
            offset: Int = 0
    ): Single<ApiResponse<Pager<PlaylistTrack>>> {
        return wrap(spotifyApi.getPlaylistTracks(playlistId, limit, offset))
    }

    fun createSocialQPlaylist(userId: String): Single<ApiResponse<Playlist>> {
        val requestBody = mapOf(
                Pair("name", resources.getString(R.string.default_playlist_name)),
                Pair("public", true),
                Pair("collaborative", false),
                Pair("description", resources.getString(R.string.default_playlist_description))
        )

        return wrap(spotifyApi.createPlaylist(userId, requestBody))
    }

    fun changePlaylistName(
            playlistId: String,
            playlistName: String
    ): Single<ApiResponse<Unit>> {
        val requestBody = mapOf<String, Any>(Pair("name", playlistName))

        return wrapEmptyResponse(spotifyApi.changePlaylistDetails(playlistId, requestBody))
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

        return wrap(spotifyApi.addTracksToPlaylist(playlistId, requestBody.toMap()))
    }

    fun followPlaylist(playlistId: String): Single<ApiResponse<Unit>> {
        return wrapEmptyResponse(spotifyApi.followPlaylist(playlistId))
    }

    fun unfollowPlaylist(playlistId: String): Single<ApiResponse<Unit>> {
        return wrapEmptyResponse(spotifyApi.unfollowPlaylist(playlistId))
    }

    private fun <T> wrap(stream: Single<Response<T>>): Single<ApiResponse<T>> {
        return stream.map {
            val body = it.body()

            if (it.isSuccessful && body != null) {
                Success(body)
            } else {
                when (SpotifyErrorCode.parse(it.code())) {
                    SpotifyErrorCode.NOT_MODIFIED -> TODO()
                    SpotifyErrorCode.UNAUTHORIZED,
                    SpotifyErrorCode.FORBIDDEN -> Unauthorized
                    SpotifyErrorCode.BAD_REQUEST,
                    SpotifyErrorCode.NOT_FOUND,
                    SpotifyErrorCode.TOO_MANY_REQUESTS,
                    SpotifyErrorCode.INTERNAL_SERVICE_ERROR,
                    SpotifyErrorCode.BAD_GATEWAY,
                    SpotifyErrorCode.SERVICE_UNAVAILABLE,
                    SpotifyErrorCode.UNKNOWN -> NetworkError(
                            code = it.code(),
                            error = getSpotifyErrorFromErrorBody(it.errorBody())
                    )
                }
            }
        }
    }

    private fun wrapEmptyResponse(stream: Completable): Single<ApiResponse<Unit>> {
        return stream.toSingleDefault(Success(Unit) as ApiResponse<Unit>)
                .onErrorReturn {
                    when (it) {
                        is SocketTimeoutException -> NetworkTimeout
                        is HttpException -> {
                            when (SpotifyErrorCode.parse(it.code())) {
                                SpotifyErrorCode.NOT_MODIFIED -> TODO()
                                SpotifyErrorCode.UNAUTHORIZED,
                                SpotifyErrorCode.FORBIDDEN -> Unauthorized
                                SpotifyErrorCode.BAD_REQUEST,
                                SpotifyErrorCode.NOT_FOUND,
                                SpotifyErrorCode.TOO_MANY_REQUESTS,
                                SpotifyErrorCode.INTERNAL_SERVICE_ERROR,
                                SpotifyErrorCode.BAD_GATEWAY,
                                SpotifyErrorCode.SERVICE_UNAVAILABLE,
                                SpotifyErrorCode.UNKNOWN -> NetworkError(
                                        code = it.code(),
                                        error = getSpotifyErrorFromErrorBody(it.response()?.errorBody())
                                )
                            }
                        }
                        else -> UnknownError
                    }
                }
    }

    /**
     * Parse a [SpotifyError] from an optional [ResponseBody]
     */
    private fun getSpotifyErrorFromErrorBody(errorBody: ResponseBody?): SpotifyError? {
        if (errorBody == null) return null

        val errorBodyString: String
        try {
            errorBodyString = errorBody.string()
        } catch (exception: IOException) {
            return null
        }

        val gson = GsonBuilder().create()
        return try {
            gson.fromJson(errorBodyString, SpotifyError::class.java)
        } catch (exception: JsonSyntaxException) {
            return null
        }
    }
}

sealed class ApiResponse<out T> {
    data class Success<out T>(val body: T) : ApiResponse<T>()

    data class NetworkError(
            val code: Int,
            val error: SpotifyError?
    ) : ApiResponse<Nothing>()

    object NetworkTimeout : ApiResponse<Nothing>()

    object Unauthorized : ApiResponse<Nothing>()

    object UnknownError : ApiResponse<Nothing>()
}

enum class SpotifyErrorCode(val code: Int) {
    // For conditional requests
    NOT_MODIFIED(304),

    // Spotify Errors
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    TOO_MANY_REQUESTS(429),

    // Server Errors
    INTERNAL_SERVICE_ERROR(500),
    BAD_GATEWAY(502),
    SERVICE_UNAVAILABLE(503),

    UNKNOWN(-1);

    companion object {
        fun parse(code: Int?): SpotifyErrorCode {
            if (code == null) return UNKNOWN

            for (value in values()) {
                if (value.code == code) {
                    return value
                }
            }
            return UNKNOWN
        }
    }
}