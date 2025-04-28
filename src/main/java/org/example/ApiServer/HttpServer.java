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
import org.example.utils.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServer extends AbstractVerticle
{

    private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);

    private final SqlClient sqlClient;

    private final JwtUtil jwtUtil;

    private final int port;

    public HttpServer(SqlClient sqlClient, JwtUtil jwtUtil, int port)
    {
        this.sqlClient = sqlClient;

        this.jwtUtil = jwtUtil;

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
        router.route()
                .handler(BodyHandler.create());

        var userRoutes = new UserRoutes(sqlClient, jwtUtil);

        router.route("/api/*")
                .subRouter(userRoutes.configureRoutes(vertx));

        var jwtHandler = JWTAuthHandler.create(jwtUtil.getAuthProvider());

        router.route("/api/secured/*");

        // Credential routes
        var credentialRoutes = new CredentialRoutes(sqlClient);

        router.route("/api/credentials/*").handler(jwtHandler).
                subRouter(credentialRoutes.configureRoutes(vertx));

        // Discovery routes
        var discoveryRoutes = new DiscoveryRoutes(sqlClient);

        router.route("/api/secured/discoveries/*")
                .subRouter(discoveryRoutes.configureRoutes(vertx));

        // Global error handler
        router.route().failureHandler(ctx ->
        {
            var statusCode = ctx.statusCode() > 0 ? ctx.statusCode() : 500;

            var message = ctx.failure() != null ? ctx.failure().getMessage() : "Internal Server Error";

            ApiResponse.error(ctx, message, statusCode);
        });

        return router;
    }
}
