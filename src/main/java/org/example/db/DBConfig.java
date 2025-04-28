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

    /**
     * Creates a PostgreSQL connection pool using the given Vertx instance and configuration.
     *
     * @param vertx  The Vert.x instance.
     * @param config The configuration JsonObject containing database connection details.
     * @return The configured SqlClient (connection pool).
     */

    public static SqlClient createPgPool(Vertx vertx, JsonObject config)
    {
        var dbConfig = config.getJsonObject(Constants.DB);

        var host = dbConfig.getString(Constants.DB_HOST, Constants.DEFAULT_DB_HOST);

        var port = dbConfig.getInteger(Constants.DB_PORT, Constants.DEFAULT_DB_PORT);

        var database = dbConfig.getString(Constants.DB_DATABASE, Constants.DEFAULT_DB_DATABASE);

        var user = dbConfig.getString(Constants.DB_USER, Constants.DEFAULT_DB_USER);

        var password = dbConfig.getString(Constants.DB_PASSWORD, Constants.DEFAULT_DB_PASSWORD);

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
