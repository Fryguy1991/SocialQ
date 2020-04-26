package com.chrisf.socialq.userinterface.common

/**
 * Binding for an AlertDialog, contains information for displaying title/message and buttons
 */
data class AlertDialogBinding<Action>(
    val title: String,
    val message: String,
    val positiveButtonText: String,
    val positiveAction: Action?,
    val negativeButtonText: String?,
    val negativeAction: Action?,
    val neutralButtonText: String?,
    val neutralAction: Action?,
    val isCancelable: Boolean,
    val cancelAction: Action?
)