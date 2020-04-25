package com.chrisf.socialq.extensions

import io.reactivex.Observable
import java.util.concurrent.TimeUnit

/**
 * Throttle emissions every 300ms. Useful for avoiding double clicks and such
 */
fun <T> Observable<T>.filterEmissions() : Observable<T> =
        this.throttleFirst(300, TimeUnit.MILLISECONDS)
