package com.europartes.mobile.data.repository

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retries only idempotent GET requests, at most twice, for transient transport
 * failures or gateway/service-unavailable responses. Never retries writes.
 */
class ReadRetryInterceptor(private val maxRetries: Int = 2) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.method.equals("GET", ignoreCase = true)) {
            return chain.proceed(request)
        }

        var attempt = 0
        var lastException: IOException? = null
        while (attempt <= maxRetries) {
            try {
                val response = chain.proceed(request)
                if (response.code !in TRANSIENT_HTTP_CODES || attempt == maxRetries) {
                    return response
                }
                response.close()
            } catch (error: IOException) {
                lastException = error
                if (attempt == maxRetries) throw error
            }
            Thread.sleep(BACKOFF_MS[attempt.coerceAtMost(BACKOFF_MS.lastIndex)])
            attempt++
        }
        throw lastException ?: IOException("No se pudo completar la solicitud.")
    }

    private companion object {
        val TRANSIENT_HTTP_CODES = setOf(502, 503, 504)
        val BACKOFF_MS = longArrayOf(700L, 1400L)
    }
}
