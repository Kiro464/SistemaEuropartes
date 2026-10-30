package com.europartes.mobile.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Notifies the active UI when the API explicitly rejects the stored refresh token. */
object SessionInvalidation {
    private val mutableEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events = mutableEvents.asSharedFlow()

    fun notifyRejectedSession() {
        mutableEvents.tryEmit(Unit)
    }
}
