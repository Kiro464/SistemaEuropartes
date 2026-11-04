package com.europartes.mobile.data.sync

import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

private fun Throwable.chain(): Sequence<Throwable> = generateSequence(this) { it.cause }

/** The server could not answer, so the last copy saved in Room is the best available view. */
fun Throwable.canUseOfflineCopy(): Boolean = chain().any { error ->
    (error is HttpException && error.code() >= 500) || (error is IOException && error !is HttpIOException)
} && !isAuthorizationFailure()

fun Throwable.isAuthorizationFailure(): Boolean =
    chain().filterIsInstance<HttpException>().any { it.code() == 401 || it.code() == 403 }

/** The connection failed before any request bytes were sent, so retrying cannot duplicate a write. */
fun Throwable.neverReachedServer(): Boolean = chain().any { error ->
    error is UnknownHostException || error is ConnectException || error is NoRouteToHostException || error is SSLException
}

/** Marks an [IOException] that only wraps an HTTP rejection, which is not a connectivity problem. */
class HttpIOException(message: String, cause: Throwable) : IOException(message, cause)
