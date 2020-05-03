package com.chrisf.socialq.userinterface.common

/**
 * Binding for an AlertDialog, contains information for displaying title/message and buttons
 */
data class AlertDialogBinding<Action>(
    val title: String,
    val message: String,
    val isCancelable: Boolean,
    val positiveButtonText: String,
    val positiveAction: Action? = null,
    val negativeButtonText: String? = null,
    val negativeAction: Action? = null,
    val neutralButtonText: String? = null,
    val neutralAction: Action? = null,
    val cancelAction: Action? = null
)