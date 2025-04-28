package org.example.db;

import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.SqlClient;
import org.example.constants.Constants;

public class DBConfig
{

    public static SqlClient createPgPool(Vertx vertx, JsonObject config)
    {
        var dbConfig = config.getJsonObject(Constants.DB);

        var host = dbConfig.getString(Constants.DB_HOST);

        var port = dbConfig.getInteger(Constants.DB_PORT);

        var database = dbConfig.getString(Constants.DB_DATABASE);

        var user = dbConfig.getString(Constants.DB_USER);

        var password = dbConfig.getString(Constants.DB_PASSWORD );

        var connectOptions = new PgConnectOptions()
                .setPort(port)
                .setHost(host)
                .setDatabase(database)
                .setUser(user)
                .setPassword(password);

        var poolOptions = new PoolOptions()
                .setMaxSize(dbConfig.getInteger(Constants.DB_POOL_SIZE, Constants.DEFAULT_DB_POOL_SIZE));

        return PgBuilder
                .client()
                .with(poolOptions)
                .connectingTo(connectOptions)
                .using(vertx)
                .build();
    }
}
