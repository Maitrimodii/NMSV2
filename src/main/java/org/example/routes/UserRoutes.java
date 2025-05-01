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
import java.util.Map;

public class UserRoutes extends BaseApi
{
    protected final DbQueryHelper dbHelper;

    private final Jwt jwt;

    private static final Map<String, Boolean> userSchema = Map.of(
            "username", true,
            "password", true
    );

    public UserRoutes(SqlClient client, Jwt jwt)
    {
        super(client, Constants.USER_TABLE, Constants.USER_MODULE, userSchema);

        this.dbHelper = new DbQueryHelper(client);

        this.jwt = jwt;
    }

    private String hashPassword(String password)
    {
        try
        {
            var digest = MessageDigest.getInstance("SHA-256");

            var hash = digest.digest(password.getBytes());

            return Base64.getEncoder().encodeToString(hash);
        }
        catch (NoSuchAlgorithmException e)
        {
            System.err.println("SHA-256 not available: " + e.getMessage());

            return null;
        }
    }

    private void register (RoutingContext ctx)
    {
        var body = ctx.body().asJsonObject();

        if(validate(ctx))
        {
            return;
        }

        var username = body.getString("username");

        var password = body.getString("password");

        var data = new JsonObject()
                .put("username", username)
                .put("password", hashPassword(password));


        dbHelper.insert("users", data)
                .onSuccess(v -> ApiResponse.success(ctx, null, "User registered successfully", 201))
                .onFailure(err ->
                {
                    ApiResponse.error(ctx, err.getMessage(), 400);
                });
    }
    protected void login(RoutingContext ctx)
    {
        var body = ctx.body().asJsonObject();

        if(validate(ctx))
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

                        user.put("token", token);

                        return Future.succeededFuture(user);
                    }
                    else
                    {
                        return Future.failedFuture("Invalid password");
                    }
                })

                .onSuccess(user -> ApiResponse.success(ctx, user, "Login successful", 200))

                .onFailure(err ->
                {
                    ApiResponse.error(ctx, err.getMessage(), 401);
                });
    }

    public Router init(Router router)
    {
        router.post("/register").handler(this::register);

        router.post("/login").handler(this::login);

        return router;
    }
}
