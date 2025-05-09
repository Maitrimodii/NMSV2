package org.example.ApiServer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.sqlclient.SqlClient;
import org.example.Main;
import org.example.constants.Constants;
import org.example.routes.CredentialRoutes;
import org.example.routes.DiscoveryRoutes;
import org.example.routes.ProvisionRoutes;
import org.example.routes.UserRoutes;
import org.example.utils.ApiResponse;
import org.example.utils.Jwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;;

public class HttpServer extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);

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

        try
        {
            var router = setupRouter();

            vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(port)
                    .onSuccess(server -> startPromise.complete())
                    .onFailure(startPromise::fail);
        }
        catch (Exception exception)
        {
            logger.error("Failed to start HTTP server: {}", exception.getMessage());
            startPromise.fail(exception);

        }
    }

    private Router setupRouter()
    {
        try{
            var router = Router.router(vertx);

            // Global handler for request body parsing
            router.route().handler(BodyHandler.create());

            router.route("/api/users/*").subRouter(new UserRoutes(sqlClient,jwt).init(router));

            var jwtHandler = JWTAuthHandler.create(jwt.getAuthProvider());


            router.route("/api/credentials/*")
                    .handler(jwtHandler)
                    .subRouter(new CredentialRoutes(sqlClient).init(Router.router(vertx)));


            router.route("/api/discoveries/*")
                    .handler(jwtHandler)
                    .subRouter(new DiscoveryRoutes(sqlClient).init(Router.router(vertx)));

            router.route("/api/provisions/*")
                    .handler(jwtHandler)
                    .subRouter(new ProvisionRoutes(sqlClient).init(Router.router(vertx)));

            // Global error handler

            router.route().failureHandler(ctx -> {

                var statusCode = ctx.statusCode() > 0 ? ctx.statusCode() : Constants.HTTP_INTERNAL_SERVER_ERROR;

                var message = ctx.failure() != null ? ctx.failure().getMessage() : "Internal Server Error";

                ApiResponse.error(ctx, message, statusCode);
            });

            return router;
        }
        catch (Exception exception)
        {
            logger.error("Failed to setup router: {}", exception.getMessage());
        }
        return null;
    }
}
