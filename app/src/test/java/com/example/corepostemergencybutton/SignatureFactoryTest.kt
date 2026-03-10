package com.example.corepostemergencybutton

import com.example.corepostemergencybutton.data.SignatureFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class SignatureFactoryTest {
    @Test
    fun canonicalString_matchesServerContract() {
        val canonical = SignatureFactory.canonicalString(
            method = "get",
            path = "mobile/check",
            timestamp = "1712345678",
        )

        assertEquals("GET\n/mobile/check\n1712345678", canonical)
    }

    @Test
    fun hmacSignature_matchesKnownVector() {
        val signature = SignatureFactory.sign(
            secret = "493a4d365150fc3e474d6449a386dc1298655da7ace5bd56502a8294bfe369ee",
            canonicalString = "GET\n/mobile/check\n1712345678",
        )

        assertEquals(
            "46d96b9d0827fa0fdf9ec98da307ff8966ed88ca792ad9420c7b505bf5eb8a4f",
            signature,
        )
    }
}
