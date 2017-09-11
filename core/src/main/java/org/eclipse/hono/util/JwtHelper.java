/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.util;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.spec.SecretKeySpec;

import org.eclipse.hono.config.KeyLoader;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import io.vertx.core.Vertx;

/**
 * A utility class for generating JWT tokens asserting the registration status of devices.
 *
 */
public abstract class JwtHelper {

    private static final Key DUMMY_KEY = new SecretKeySpec(new byte[]{ 0x00,  0x01 }, SignatureAlgorithm.HS256.getJcaName());
    private final Vertx vertx;

    /**
     * The signature algorithm used for signing.
     */
    protected SignatureAlgorithm algorithm;
    /**
     * The secret key used for signing.
     */
    protected Key key;
    /**
     * The lifetime of created tokens.
     */
    protected Duration tokenLifetime;

    /**
     * see {@link JwtParser#setAllowedClockSkewSeconds}
     */
    protected int allowedClockSkew = 10;

    /**
     * Specifies the maximum number of entries the validation cache may contain.
     * This directly reflects the performance of registration token validation.
     */
    protected Long validationCacheMaxSize;

    /**
     * Creates a new helper for a vertx instance.
     * 
     * @param vertx The vertx instance to use for loading key material from the file system.
     */
    protected JwtHelper(final Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * Gets the bytes representing the UTF8 encoding of a secret.
     * 
     * @param secret The string to get the bytes for.
     * @return The bytes.
     */
    protected static final byte[] getBytes(final String secret) {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Sets the secret to use for signing tokens asserting the
     * registration status of devices.
     * 
     * @param secret The secret to use.
     * @throws NullPointerException if secret is {@code null}.
     * @throws IllegalArgumentException if the secret is &lt; 32 bytes.
     */
    protected final void setSharedSecret(final byte[] secret) {
        if (Objects.requireNonNull(secret).length < 32) {
            throw new IllegalArgumentException("shared secret must be at least 32 bytes");
        }
        this.algorithm = SignatureAlgorithm.HS256;
        this.key = new SecretKeySpec(secret, SignatureAlgorithm.HS256.getJcaName());
    }

    /**
     * Sets the path to a PKCS8 PEM file containing the RSA private key to use for signing tokens
     * asserting the registration status of devices.
     * 
     * @param keyPath The absolute path to the file.
     * @throws NullPointerException if the path is {@code null}.
     * @throws IllegalArgumentException if the key cannot be read from the file.
     */
    protected final void setPrivateKey(final String keyPath) {
        Objects.requireNonNull(keyPath);
        this.algorithm = SignatureAlgorithm.RS256;
        this.key = KeyLoader.fromFiles(vertx, keyPath, null).getPrivateKey();
        if (key == null) {
            throw new IllegalArgumentException("cannot load private key");
        }
    }

    /**
     * Sets the path to a PEM file containing a certificate holding a public key to use for validating the
     * signature of tokens asserting the registration status of devices.
     * 
     * @param keyPath The absolute path to the file.
     * @throws NullPointerException if the path is {@code null}.
     * @throws IllegalArgumentException if the key cannot be read from the file.
     */
    protected final void setPublicKey(final String keyPath) {
        Objects.requireNonNull(keyPath);
        this.algorithm = SignatureAlgorithm.RS256;
        this.key = KeyLoader.fromFiles(vertx, null, keyPath).getPublicKey();
        if (key == null) {
            throw new IllegalArgumentException("cannot load private key");
        }
    }

    /**
     * Gets the duration being used for calculating the <em>exp</em> claim of tokens
     * created by this class.
     * <p>
     * Clients should always check if a token is expired before using any information
     * contained in the token.
     *  
     * @return The duration.
     */
    public final Duration getTokenLifetime() {
        return tokenLifetime;
    }

    /**
     * Checks if a token is expired.
     * 
     * @param token The token to check.
     * @param allowedClockSkewSeconds The allowed clock skew in seconds.
     * @return {@code true} if the token is expired according to the current system time (including allowed skew).
     */
    public static final boolean isExpired(final String token, final int allowedClockSkewSeconds) {
        Instant now = Instant.now().minus(Duration.ofSeconds(allowedClockSkewSeconds));
        return isExpired(token, now);
    }

    /**
     * Checks if a token is expired.
     * 
     * @param token The token to check.
     * @param now The instant of time the token's expiration time should be checked against.
     * @return {@code true} if the token is expired according to the given instant of time.
     * @throws NullPointerException if the token is {@code null}.
     * @throws IllegalArgumentException if the given token contains no <em>exp</em> claim.
     */
    public static final boolean isExpired(final String token, final Instant now) {

        if (token == null) {
            throw new NullPointerException("token must not be null");
        } else {
            Date exp = getExpiration(token);
            return exp.before(Date.from(now));
        }
    }

    /**
     * Gets the value of the <em>exp</em> claim of a JWT.
     * 
     * @param token The token.
     * @return The expiration.
     * @throws NullPointerException if the token is {@code null}.
     * @throws IllegalArgumentException if the given token contains no <em>exp</em> claim.
     */
    public static final Date getExpiration(final String token) {

        if (token == null) {
            throw new NullPointerException("token must not be null");
        }

        final AtomicReference<Date> result = new AtomicReference<Date>();

        try {
            Jwts.parser().setSigningKeyResolver(new SigningKeyResolverAdapter(){
                @SuppressWarnings("rawtypes")
                @Override
                public Key resolveSigningKey(JwsHeader header, Claims claims) {
                    Date exp = claims.getExpiration();
                    if (exp != null) {
                        result.set(exp);
                    }
                    return DUMMY_KEY;
                }
            }).parse(token);
        } catch (JwtException e) {
            // expected since we do not know the signing key
        }

        if (result.get() == null) {
            throw new IllegalArgumentException("token contains no exp claim");
        } else {
            return result.get();
        }
    }

    /**
     * Sets the maximum number of entries the validation cache may contain.
     * This directly reflects the performance of registration token validation.
     *
     * @param validationCacheMaxSize number of cache entries
     * @throws IllegalArgumentException if the given max cache size is negative
     */
    public void setValidationCacheMaxSize(Long validationCacheMaxSize) {
        Objects.requireNonNull(validationCacheMaxSize);
        if (validationCacheMaxSize < 0L) {
            throw new IllegalArgumentException("Validation cache size must not be negative.");
        }
        this.validationCacheMaxSize = validationCacheMaxSize;
    }
}
