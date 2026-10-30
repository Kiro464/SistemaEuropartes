package com.europartes.mobile.data.repository

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthenticatedUserFromAccessTokenTest {
    @Test
    fun restoresIdentityClaimsFromFreshAccessToken() {
        val token = tokenWithPayload(
            """{"user_id":17,"username":"ana","nombres":"Ana","apellidos":"Ruiz","grupo":"Vendedor"}""",
        )

        val user = authenticatedUserFromAccessToken(token)

        assertEquals(17, user?.userId)
        assertEquals("ana", user?.username)
        assertEquals("Ana", user?.nombres)
        assertEquals("Ruiz", user?.apellidos)
        assertEquals("Vendedor", user?.grupo)
    }

    @Test
    fun rejectsMalformedOrUnknownRoleClaims() {
        assertNull(authenticatedUserFromAccessToken("not-a-jwt"))
        assertNull(
            authenticatedUserFromAccessToken(
                tokenWithPayload("""{"user_id":17,"username":"ana","grupo":"Rol desconocido"}"""),
            ),
        )
    }

    private fun tokenWithPayload(payload: String): String =
        "header.${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}.signature"
}
