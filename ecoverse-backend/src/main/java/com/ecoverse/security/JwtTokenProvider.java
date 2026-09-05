package com.ecoverse.security;

import com.ecoverse.exception.BadRequestException;
import com.ecoverse.model.RefreshToken;
import com.ecoverse.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    private static final String ISSUER = "ecoverse";
    private static final String AUDIENCE = "ecoverse-api";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private final RefreshTokenRepository refreshTokenRepository;

    public JwtTokenProvider(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate short-lived access token (JWT) with HS256 algorithm,
     * issuer, and audience claims.
     */
    public String generateAccessToken(Long userId, String email) {
        java.util.Date now = new java.util.Date();
        java.util.Date expiryDate = new java.util.Date(now.getTime() + accessExpiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("type", "access")
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate and persist a refresh token in the database.
     * Stores only the SHA-256 hash — the plaintext token is returned to the caller
     * but never stored in the database.
     */
    @Transactional
    public String generateRefreshToken(Long userId) {
        // Revoke all existing refresh tokens for this user (rotation)
        refreshTokenRepository.revokeAllByUserId(userId);

        // Generate new refresh token
        String token = UUID.randomUUID().toString();
        String tokenHash = hashToken(token);
        LocalDateTime expiryDate = LocalDateTime.now().plusDays(7);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(null)  // Plaintext NOT stored — only hash
                .tokenHash(tokenHash)
                .userId(userId)
                .expiryDate(expiryDate)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        return token;
    }

    /**
     * Validate a refresh token and return the associated user ID.
     * Looks up by SHA-256 hash of the incoming token.
     * Implements reuse detection: if a revoked token is reused, ALL tokens for that user
     * are revoked (indicating the token was compromised).
     */
    @Transactional
    public Long validateRefreshToken(String token) {
        String tokenHash = hashToken(token);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        // REUSE DETECTION: if a revoked token is reused, the token was likely stolen.
        // Revoke ALL tokens for this user to force re-authentication on all devices.
        if (Boolean.TRUE.equals(refreshToken.getRevoked())) {
            logger.warn("SECURITY: Reuse of revoked refresh token detected for userId={}. Revoking all tokens.",
                    refreshToken.getUserId());
            refreshTokenRepository.revokeAllByUserId(refreshToken.getUserId());
            throw new BadRequestException("Refresh token has been revoked. Please log in again.");
        }

        // Check if expired
        if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Refresh token has expired. Please log in again.");
        }

        // Update lastUsedAt for audit tracking
        refreshToken.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        return refreshToken.getUserId();
    }

    /**
     * Revoke a specific refresh token by its plaintext value (used during logout).
     * Looks up by SHA-256 hash.
     */
    @Transactional
    public void revokeRefreshToken(String token) {
        String tokenHash = hashToken(token);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(refreshToken -> {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
        });
    }

    /**
     * Revoke all refresh tokens for a user (used during password change/reset, etc.)
     * Sets revoked=true (does NOT delete) to support reuse detection.
     */
    @Transactional
    public void revokeAllUserTokens(Long userId) {
        int revoked = refreshTokenRepository.revokeAllByUserId(userId);
        logger.info("Revoked {} refresh tokens for userId={}", revoked, userId);
    }

    /**
     * Delete all refresh tokens for a user (used during account deletion).
     * This permanently removes tokens, not just marks them revoked.
     */
    @Transactional
    public void deleteAllUserTokens(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    /**
     * Parse JWT claims with issuer and audience validation.
     */
    private Claims parseTokenClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseTokenClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getEmailFromToken(String token) {
        Claims claims = parseTokenClaims(token);
        return claims.get("email", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseTokenClaims(token);
            return true;
        } catch (Exception ex) {
            logger.debug("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Compute SHA-256 hash of a refresh token for secure storage.
     */
    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
