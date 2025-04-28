package org.example.ApiServer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.sqlclient.SqlClient;
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
        router.route()
                .handler(BodyHandler.create());

        // Global error handler

        return router;
    }
}
