package com.chrisf.socialq.extensions

import android.content.Context
import android.view.View

/**
 * Calculate the Float pixel value of a DIP (density independent pixel)
 */
fun Context.dipF(dipValue: Int): Float = this.resources.displayMetrics.density * dipValue

/**
 * Calculate the Int pixel value of a DIP (density independent pixel)
 */
fun Context.dip(dipValue: Int): Int = dipF(dipValue).toInt()

/**
 * Calculate the Float pixel value of a DIP (density independent pixel) for a View
 */
fun View.dip(dipValue: Int): Int = context.dip(dipValue)