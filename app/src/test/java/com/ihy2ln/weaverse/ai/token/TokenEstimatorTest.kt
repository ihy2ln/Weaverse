package com.ihy2ln.weaverse.ai.token

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TokenEstimatorTest {
    @Test
    fun `blank text is zero tokens`() {
        assertEquals(0, TokenEstimator.estimate(""))
        assertEquals(0, TokenEstimator.estimate("   "))
    }

    @Test
    fun `estimates roughly four characters per token, rounded up`() {
        assertEquals(1, TokenEstimator.estimate("abcd"))
        assertEquals(2, TokenEstimator.estimate("abcde"))
        assertEquals(3, TokenEstimator.estimate("twelve chars"))
    }

    @Test
    fun `list overload sums each entry's estimate`() {
        assertEquals(TokenEstimator.estimate("abcd") + TokenEstimator.estimate("efgh"), TokenEstimator.estimate(listOf("abcd", "efgh")))
    }
}
