package com.chrisf.socialq.network

import com.chrisf.socialq.model.spotify.ErrorBody
import com.chrisf.socialq.model.spotify.SpotifyError
import com.google.gson.JsonIOException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.lang.Exception
import java.lang.NumberFormatException

class SpotifyErrorTypeAdapter : TypeAdapter<SpotifyError>() {
    override fun write(out: JsonWriter?, value: SpotifyError?) {
        throw IllegalStateException("This type not meant for writing")
    }

    override fun read(`in`: JsonReader?): SpotifyError? {
        val jsonTree = try {
            JsonParser().parse(`in`).asJsonObject
        } catch (exception: Exception) {
            when (exception) {
                is JsonIOException,
                is JsonSyntaxException -> return null
                else -> throw exception
            }
        }

        val errorBody = jsonTree.getAsJsonObject("error")

        val statusCode = try {
            errorBody.getAsJsonPrimitive("status").asInt
        } catch (exception: NumberFormatException) {
            return null
        }
        val message = errorBody.getAsJsonPrimitive("message").asString

        return SpotifyError(ErrorBody(statusCode, message))
    }
}
