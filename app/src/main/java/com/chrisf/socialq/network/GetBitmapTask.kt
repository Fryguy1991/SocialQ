package com.chrisf.socialq.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import timber.log.Timber
import java.io.IOException
import java.net.URL


class GetBitmapTask(private val listener: BitmapListener) : AsyncTask<String, Void, Bitmap>() {
    override fun doInBackground(vararg params: String?): Bitmap? {
        try {
            if (!params[0].isNullOrBlank()) {
                val url = URL(params[0])
                return BitmapFactory.decodeStream(url.openConnection().getInputStream())
            }
            Timber.e("Failed to retrieve bitmap")
        } catch (e: IOException) {
            Timber.e(e)
        }
        return null
    }

    override fun onPostExecute(result: Bitmap?) {
        listener.displayBitmap(result)
    }
}

interface BitmapListener {
    fun displayBitmap(bitmap: Bitmap?)
}