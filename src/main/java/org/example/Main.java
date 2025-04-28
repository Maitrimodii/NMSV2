package org.example;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.example.ApiServer.HttpServer;
import org.example.constants.Constants;
import org.example.db.DBConfig;
import org.example.utils.ConfigLoader;
import org.example.utils.Jwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main
{

    private static final Logger logger = LoggerFactory.getLogger(Main.class);


    public static void main(String[] args)
    {
        var vertx = Vertx.vertx();

        startServer(vertx)
                .onSuccess(v -> logger.info("HTTP server started successfully"))

                .onFailure(err -> {
                    logger.error("Failed to start server: {}", err.getMessage());
                    vertx.close();
                });
    }

    /**
     * Loads configuration, initializes database pool and HTTP server, and deploys the server verticle.
     *
     * @param vertx The Vert.x instance.
     * @return A Future that completes when the server is successfully deployed.
     */
    private static Future<Object> startServer(Vertx vertx)
    {
        return ConfigLoader.load(vertx)
                .compose(config -> {

                    var pgPool = DBConfig.createPgPool(vertx, config);

                    var server = new HttpServer(pgPool, new Jwt(), config.getInteger(Constants.HTTP_PORT));

                    // Deploy the HttpServer verticle
                    return vertx.deployVerticle(server)
                            .mapEmpty()
                            .onComplete(result ->
                            {
                                if (result.succeeded()) {
                                    Runtime.getRuntime().addShutdownHook(new Thread(() ->
                                    {
                                        logger.info("Shutting down server and closing resources");
                                        pgPool.close();
                                        vertx.close();
                                    }));
                                }
                            });
                });
    }
}