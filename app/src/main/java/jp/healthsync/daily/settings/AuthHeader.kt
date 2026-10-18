package jp.healthsync.daily.settings

/**
 * The receiver authenticates with one HTTP header. The owner pastes the full
 * value (for example `Bearer …` or whatever string the receiver issued).
 * The name defaults to `Authorization` and stays editable.
 * This app does not add or remove a scheme prefix.
 */
object AuthHeader {
    const val DEFAULT_NAME = "Authorization"

    private val NAME_TOKEN = Regex("[!#\$%&'*+.^_`|~0-9A-Za-z-]+")

    fun resolve(name: String, value: String): AuthHeaderDecision {
        val headerName = name.trim()
        val headerValue = value.trim().replace("\r", "").replace("\n", "")
        if (headerName.isEmpty() && headerValue.isEmpty()) return AuthHeaderDecision.Omit
        if (headerValue.isEmpty()) return AuthHeaderDecision.Omit
        if (headerName.isEmpty()) {
            return AuthHeaderDecision.Invalid("認証ヘッダー名が空です")
        }
        if (!NAME_TOKEN.matches(headerName)) {
            return AuthHeaderDecision.Invalid("認証ヘッダー名に使えない文字があります")
        }
        return AuthHeaderDecision.Include(headerName, headerValue)
    }
}

sealed class AuthHeaderDecision {
    data class Include(val name: String, val value: String) : AuthHeaderDecision()
    data object Omit : AuthHeaderDecision()
    data class Invalid(val reason: String) : AuthHeaderDecision()
}
