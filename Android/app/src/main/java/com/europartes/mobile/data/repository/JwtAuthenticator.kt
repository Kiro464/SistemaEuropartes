package com.europartes.mobile.data.repository

import com.europartes.mobile.data.local.TokenStore
import com.europartes.mobile.data.remote.AuthApi
import com.europartes.mobile.data.remote.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import java.io.IOException

/**
 * Refreshes once after a 401. Refresh uses the unauthenticated AuthApi client,
 * preventing recursive authenticator calls.
 */
class JwtAuthenticator(
    private val tokenStore: TokenStore,
    private val authApi: AuthApi,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val oldToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val currentToken = tokenStore.accessToken()

        synchronized(this) {
            // Another request may already have refreshed the token.
            if (!currentToken.isNullOrBlank() && currentToken != oldToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refresh = tokenStore.refreshToken()
            if (refresh.isNullOrBlank()) {
                tokenStore.clear()
                SessionInvalidation.notifyRejectedSession()
                return null
            }
            return try {
                val refreshed = runBlocking { authApi.refresh(RefreshRequest(refresh)) }
                if (refreshed.access.isBlank()) return null
                tokenStore.save(refreshed.access, refreshed.refresh ?: refresh)
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${refreshed.access}")
                    .build()
            } catch (error: HttpException) {
                if (error.code() == 400 || error.code() == 401) {
                    tokenStore.clear()
                    SessionInvalidation.notifyRejectedSession()
                }
                null
            } catch (_: IOException) {
                // A transient failure should not erase a potentially valid refresh token.
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
