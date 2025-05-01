package org.example.utils;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import org.example.constants.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;


public class Jwt
{

    private static final Logger logger = LoggerFactory.getLogger(Jwt.class);

    private final JWTAuth jwtAuth;
    private final JWTOptions jwtOptions;
    private final JWTOptions refreshTokenOptions;

    public Jwt()
    {
        KeyStoreOptions keyStoreOptions = new KeyStoreOptions()
                .setPath(Constants.KEYSTORE_PATH)
                .setPassword(Constants.KEYSTORE_PASSWORD)
                .setType("JCEKS");

        var jwtAuthOptions = new JWTAuthOptions()
                .setKeyStore(keyStoreOptions);

        this.jwtAuth = JWTAuth.create(Vertx.currentContext().owner(), jwtAuthOptions);

        this.jwtOptions = new JWTOptions().setExpiresInSeconds(Math.toIntExact(Constants.DEFAULT_EXPIRATION_MILLIS / 1000));

        this.refreshTokenOptions = new JWTOptions()
                .setExpiresInSeconds(Math.toIntExact(Constants.DEFAULT_REFRESH_EXPIRATION_MILLIS / 1000));

    }

    /**
     * Generates a JWT token for the given username.
     *
     * @param username the username for which to generate the token
     * @return the generated JWT token as a string
     */
    public String generateToken(String username)
    {
        logger.info("Generating JWT for username: [REDACTED]");

        var claims = new JsonObject().put("sub", username);

        return jwtAuth.generateToken(claims, jwtOptions);
    }

    /**
     * Generates a refresh token for the given username.
     *
     * @param username the username for which to generate the token
     * @return the generated refresh token as a string
     */
    public String generateRefreshToken(String username)
    {
        logger.info("Generating refresh token for username: [REDACTED]");

        var claims = new JsonObject()
                .put("sub", username)
                .put("type", "refresh")
                .put("jti", UUID.randomUUID().toString()); // Unique token ID

        return jwtAuth.generateToken(claims, refreshTokenOptions);
    }

    /**
     * Returns the JWTAuth instance used for JWT-related operations.
     *
     * @return the JWTAuth instance
     */
    public JWTAuth getAuthProvider()
    {
        return jwtAuth;
    }
}
