package org.example;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
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

    private static Future<Object> startServer(Vertx vertx) {
        return Future.succeededFuture();
    }
}