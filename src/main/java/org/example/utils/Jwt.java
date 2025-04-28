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

public class Jwt
{

    private static final Logger logger = LoggerFactory.getLogger(Jwt.class);

    private final JWTAuth jwtAuth;
    private final JWTOptions jwtOptions;

    public Jwt()
    {
        var expirationMillis = Constants.DEFAULT_EXPIRATION_MILLIS;

        KeyStoreOptions keyStoreOptions = new KeyStoreOptions()
                .setPath(Constants.KEYSTORE_PATH)
                .setPassword(Constants.KEYSTORE_PASSWORD)
                .setType("JCEKS");

        var jwtAuthOptions = new JWTAuthOptions()
                .setKeyStore(keyStoreOptions);

        this.jwtAuth = JWTAuth.create(Vertx.currentContext().owner(), jwtAuthOptions);

        this.jwtOptions = new JWTOptions().setExpiresInSeconds(Math.toIntExact(expirationMillis / 1000));
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
     * Generate a JWT token with custom claims.
     *
     * @param claims the custom claims to include in the token
     * @return the generated JWT token as a string
     */
    public String generateTokenWithClaims(JsonObject claims)
    {
        logger.info("Generating JWT with custom claims");

        return jwtAuth.generateToken(claims, jwtOptions);
    }

    /**
     * Verifies the validity of a JWT token.
     *
     * @param token the JWT token to verify
     * @return true if the token is valid, false otherwise
     */
    public boolean verifyToken(String token)
    {
        try
        {
            jwtAuth.authenticate(new JsonObject().put("jwt", token));

            logger.info("Token is valid");

            return true;
        }
        catch (Exception e)
        {
            logger.error("Invalid token: {}. Error: {}", token, e.getMessage());

            return false;
        }
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
