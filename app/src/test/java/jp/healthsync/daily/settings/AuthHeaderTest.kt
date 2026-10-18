package jp.healthsync.daily.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthHeaderTest {
    @Test
    fun defaultName_isAuthorization() {
        assertEquals("Authorization", AuthHeader.DEFAULT_NAME)
    }

    @Test
    fun pastedValue_isSentUnchangedAsideFromSurroundingWhitespace() {
        val decision = AuthHeader.resolve(" Authorization ", " Bearer abc.def\n")
        assertEquals(AuthHeaderDecision.Include("Authorization", "Bearer abc.def"), decision)
    }

    @Test
    fun customName_isKept() {
        val decision = AuthHeader.resolve("X-Sender-Key", "sk_live_1")
        assertEquals(AuthHeaderDecision.Include("X-Sender-Key", "sk_live_1"), decision)
    }

    @Test
    fun blankValue_omitsTheHeader() {
        assertEquals(AuthHeaderDecision.Omit, AuthHeader.resolve("Authorization", "  "))
        assertEquals(AuthHeaderDecision.Omit, AuthHeader.resolve("", ""))
    }

    @Test
    fun valueWithoutName_isInvalid() {
        val decision = AuthHeader.resolve(" ", "secret")
        assertEquals(AuthHeaderDecision.Invalid("認証ヘッダー名が空です"), decision)
    }

    @Test
    fun nameWithSpace_isInvalid() {
        val decision = AuthHeader.resolve("Auth Header", "secret")
        assertEquals(AuthHeaderDecision.Invalid("認証ヘッダー名に使えない文字があります"), decision)
    }
}
