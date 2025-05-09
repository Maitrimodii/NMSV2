package org.example;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.example.ApiServer.HttpServer;
import org.example.constants.Constants;
import org.example.db.DBConfig;
import org.example.db.DbQueryHelper;
import org.example.engine.DiscoveryEngine;
import org.example.engine.PollingEngine;
import org.example.utils.ConfigLoader;
import org.example.utils.Jwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

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
     * Loads configuration, initializes database pool and HTTP server, and deploys the server, discovery, and polling verticles.
     *
     * @param vertx The Vert.x instance.
     * @return A Future that completes when the verticles are successfully deployed.
     */
    private static Future<Void> startServer(Vertx vertx)
    {
        return ConfigLoader.load(vertx)
                .compose(config -> DBConfig.createPgPool(vertx, config)

                        .compose(pgPool -> {

                            var dbHelper = new DbQueryHelper(pgPool);

                            // Deploy the HttpServer verticle
                            return vertx.deployVerticle(new HttpServer(pgPool, new Jwt(), config.getInteger(Constants.HTTP_PORT)))

                                    .compose(httpServerId -> {

                                        logger.info("HttpServer verticle deployed successfully with ID: {}", httpServerId);

                                        // Deploy DiscoveryEngine verticle
                                        return vertx.deployVerticle(new DiscoveryEngine(dbHelper))
                                                .compose(discoveryEngineId -> {

                                                    logger.info("DiscoveryEngine verticle deployed successfully with ID: {}", discoveryEngineId);

                                                    // Deploy PollingEngine verticle
                                                    return vertx.deployVerticle(new PollingEngine(dbHelper))

                                                            .compose(pollingEngineId -> {

                                                                logger.info("PollingEngine verticle deployed successfully with ID: {}", pollingEngineId);

                                                                return Future.succeededFuture();
                                                            });
                                                });
                                    })
                                    .onComplete(result -> {
                                        if (result.succeeded())
                                        {
                                            // Add shutdown hook on successful deployment
                                            Runtime.getRuntime().addShutdownHook(new Thread(() -> {

                                                logger.info("Shutting down application and closing resources");

                                                pgPool.close();

                                                vertx.close();
                                            }));
                                        }
                                    });
                        }))
                .mapEmpty();
    }
}