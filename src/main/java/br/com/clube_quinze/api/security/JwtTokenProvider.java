package br.com.clube_quinze.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final JwtProperties properties;
    private final Clock clock;
    private final Key signingKey;

    public JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        String secret = properties.getSecret();
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (DecodingException | IllegalArgumentException ex) {
            // secret is probably not Base64-encoded, use raw UTF-8 bytes
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        // HS256 requires a key of at least 256 bits (32 bytes)
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret is too short for HS256 (" + keyBytes.length + " bytes). " +
                    "Provide a base64-encoded 32+ byte secret or a plain secret with at least 32 characters.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Long userId, String username, String role) {
        Instant now = clock.instant();
        Instant expiry = now.plus(properties.accessTokenTtl());
        return Jwts.builder()
                .setSubject(username)
                .setId(String.valueOf(userId))
                .addClaims(Map.of("role", role))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(Long userId, String username) {
        Instant now = clock.instant();
        Instant expiry = now.plus(properties.refreshTokenTtl());
        return Jwts.builder()
                .setSubject(username)
                .setId(userId + "-" + UUID.randomUUID())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return getClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token) {
        try {
            getAllClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private <T> T getClaim(String token, Function<Claims, T> extractor) {
        Claims claims = getAllClaims(token);
        return extractor.apply(claims);
    }

    private Claims getAllClaims(String token) {
        return Jwts.parserBuilder()
                .setClock(() -> Date.from(clock.instant()))
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
