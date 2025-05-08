package org.example.routes;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.SqlClient;
import org.example.constants.Constants;
import org.example.db.DbQueryHelper;
import org.example.utils.ApiResponse;
import org.example.utils.Jwt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;


public class UserRoutes extends BaseApi
{
    protected final DbQueryHelper dbHelper;

    private final Jwt jwt;

    public UserRoutes(SqlClient client, Jwt jwt)
    {
        super(client, Constants.USER_TABLE, Constants.USER_MODULE, Constants.USER_SCEHMA);

        this.dbHelper = new DbQueryHelper(client);

        this.jwt = jwt;
    }

    /**
     * Validates the request body for user registration and login.
     * @param password password to be hashed
     * @return String of hashed password
     */
    private String hashPassword(String password)
    {
        try
        {
            var digest = MessageDigest.getInstance(Constants.ALGORITHM);

            var hash = digest.digest(password.getBytes());

            return Base64.getEncoder().encodeToString(hash);
        }
        catch (NoSuchAlgorithmException exception)
        {
            System.err.println(Constants.ALGORITHM + " not available: " + exception.getMessage());

            return null;
        }
    }


    private void register (RoutingContext ctx)
    {
        var body = ctx.body().asJsonObject();

        if(!validate(ctx))
        {
            return;
        }

        var username = body.getString("username");

        var password = body.getString("password");

        var data = new JsonObject()
                .put("username", username)
                .put("password", hashPassword(password));


        dbHelper.insert(Constants.USER_TABLE, data)
                .onSuccess(v -> ApiResponse.success(ctx, null, "User registered successfully", Constants.HTTP_OK))
                .onFailure(err ->
                {
                    ApiResponse.error(ctx, err.getMessage(), Constants.HTTP_BAD_REQUEST);
                });
    }


    protected void login(RoutingContext ctx)
    {
        var body = ctx.body().asJsonObject();

        if(!validate(ctx))
        {
            return;
        }

        var username = body.getString("username");

        var password = body.getString("password");

        dbHelper.fetchOne(Constants.USER_TABLE, "username", username)
                .compose(user ->
                {
                    if (user == null)
                    {
                        return Future.failedFuture("User not found");
                    }

                    var storedPassword = user.getString("password");

                    var hashedInputPassword = hashPassword(password);

                    if (storedPassword.equals(hashedInputPassword))
                    {
                        user.remove("password");

                        var token = jwt.generateToken(username);
                        var refreshToken = jwt.generateRefreshToken(username);

                        user.put("token", token);

                        user.put("refreshToken", refreshToken);

                        return Future.succeededFuture(user);
                    }
                    else
                    {
                        return Future.failedFuture("Invalid password");
                    }
                })

                .onSuccess(user -> ApiResponse.success(ctx, user, "Login successful", Constants.HTTP_OK))

                .onFailure(err ->
                {
                    ApiResponse.error(ctx, err.getMessage(), Constants.HTTP_UNAUTHORIZED);
                });
    }

    private void refresh(RoutingContext ctx)
    {
        var token = ctx.request().getHeader("Authorization");

        if (token == null || !token.startsWith("Bearer "))
        {
            ApiResponse.error(ctx, "Missing or invalid token", Constants.HTTP_UNAUTHORIZED);
            return;
        }

        var refreshToken = token.substring(7);

        // Verify the refresh token
        jwt.getAuthProvider().authenticate(new JsonObject().put("token", refreshToken))
                .compose(user -> {
                    // Check if this is a refresh token
                    var tokenInfo = user.principal();

                    if (!"refresh".equals(tokenInfo.getString("type")))
                    {
                        return Future.failedFuture("Invalid token type");
                    }

                    // Extract username from token
                    var username = tokenInfo.getString("sub");

                    if (username == null)
                    {
                        return Future.failedFuture("Invalid token");
                    }

                    // Generate new tokens
                    var newAccessToken = jwt.generateToken(username);

                    var newRefreshToken = jwt.generateRefreshToken(username);

                    // Return the new tokens
                    JsonObject response = new JsonObject()
                            .put("token", newAccessToken)
                            .put("refreshToken", newRefreshToken)
                            .put("username", username);

                    return Future.succeededFuture(response);
                })

                .onSuccess(tokens -> ApiResponse.success(ctx, tokens, "Token refreshed successfully", Constants.HTTP_OK))

                .onFailure(err -> ApiResponse.error(ctx, err.getMessage(), Constants.HTTP_UNAUTHORIZED));
    }

    public Router init(Router router)
    {
        router.post("/register").handler(this::register);

        router.post("/login").handler(this::login);

        router.post("/refresh").handler(this::refresh);

        return router;
    }
}
