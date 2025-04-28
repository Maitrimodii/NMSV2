package org.example.ApiServer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.sqlclient.SqlClient;
import org.example.routes.CredentialRoutes;
import org.example.routes.DiscoveryRoutes;
import org.example.routes.UserRoutes;
import org.example.utils.ApiResponse;
import org.example.utils.Jwt;;

public class HttpServer extends AbstractVerticle
{
    private final SqlClient sqlClient;

    private final Jwt jwt;

    private final int port;

    public HttpServer(SqlClient sqlClient, Jwt jwt, int port)
    {
        this.sqlClient = sqlClient;

        this.jwt = jwt;

        this.port = port;
    }

    @Override
    public void start(Promise<Void> startPromise)
    {

        var router = setupRouter();

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(server -> startPromise.complete())
                .onFailure(err ->
                {
                    startPromise.fail(err);
                });

    }

    private Router setupRouter()
    {
        var router = Router.router(vertx);

        // Global handler for request body parsing
        router.route().handler(BodyHandler.create());

        router.route("/api/users/*")
                .subRouter(new CredentialRoutes(sqlClient).init(router));

        var jwtHandler = JWTAuthHandler.create(jwt.getAuthProvider());

        router.route("/api/credentials/*")
                .handler(jwtHandler)
                .subRouter(new CredentialRoutes(sqlClient).init(router));

        router.route("/api/discoveries/*")
                .handler(jwtHandler)
                .subRouter(new DiscoveryRoutes(sqlClient).init(router));

        // Global error handler

        router.route().failureHandler(ctx -> {
            var statusCode = ctx.statusCode() > 0 ? ctx.statusCode() : 500;
            var message = ctx.failure() != null ? ctx.failure().getMessage() : "Internal Server Error";
            ApiResponse.error(ctx, message, statusCode);
        });

        return router;
    }
}
