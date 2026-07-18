package com.itmk.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/** JWT 生成与验证。验证器只初始化一次，避免每个请求重复构建。 */
@Component
@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtUtils {
    private String issuer;
    private String audience;
    private String secret;
    /** Access Token 有效期，单位：分钟。 */
    private int expiration;
    /** Refresh Token 有效期，单位：分钟。 */
    private int refreshExpiration;

    private Algorithm algorithm;
    private JWTVerifier verifier;

    @PostConstruct
    public void init() {
        algorithm = Algorithm.HMAC256(secret);
        verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .withAudience(audience)
                .build();
    }

    public String generateToken(Map<String, String> claims) {
        Calendar expiresAt = Calendar.getInstance();
        expiresAt.add(Calendar.MINUTE, expiration);

        JWTCreator.Builder builder = JWT.create();
        claims.forEach(builder::withClaim);
        return builder
                .withJWTId(UUID.randomUUID().toString())
                .withIssuer(issuer)
                .withAudience(audience)
                .withIssuedAt(new Date())
                .withExpiresAt(expiresAt.getTime())
                .sign(algorithm);
    }

    public boolean verify(String token) {
        try {
            verifier.verify(token);
            return true;
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    public DecodedJWT jwtDecode(String token) {
        try {
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            throw new IllegalArgumentException("Token 无效或已过期", e);
        }
    }
}
