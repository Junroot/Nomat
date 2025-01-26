package ilpak.nomat.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

@Service
class TokenService(
    @Value("\${jwt.key}")
    private val key: String
) {
    private val secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(key))

    fun getNewToken(playerId: Long): String {
        return Jwts.builder()
            .subject(playerId.toString())
            .expiration(Calendar.getInstance().also { it.add(Calendar.DATE, EXPIRATION_DAYS) }.time)
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()
    }

    fun getPlayerId(token: String): Long? {
        return kotlin.runCatching {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
                .subject
                .toLongOrNull()
        }.getOrNull()
    }

    companion object {
        const val EXPIRATION_DAYS = 30
        const val TOKEN_COOKIE_KEY = "token"
    }
}
