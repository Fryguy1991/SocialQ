package com.chrisf.socialq.enums

/**
 * Enum for representing PayloadTransferUpdate.Status
 * see: https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/PayloadTransferUpdate.Status
 */
enum class PayloadTransferUpdateStatus(val intValue: Int) {
    SUCCESS(1),
    FAILURE(2),
    IN_PROGRESS(3),
    CANCELED(4),
    NONE(-1),;

    companion object {
        fun getStatusFromConstant(intValue: Int): PayloadTransferUpdateStatus {
            for (status in enumValues<PayloadTransferUpdateStatus>()) {
                if (intValue == status.intValue) {
                    return status
                }
            }
            return NONE
        }
    }
}