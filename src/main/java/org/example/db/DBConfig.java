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

        var connectOptions = new PgConnectOptions()
                .setPort(dbConfig.getInteger(Constants.DB_PORT, Constants.DEFAULT_DB_PORT))
                .setHost(dbConfig.getString(Constants.DB_HOST, Constants.DEFAULT_DB_HOST))
                .setDatabase(dbConfig.getString(Constants.DB_DATABASE, Constants.DEFAULT_DB_DATABASE))
                .setUser( dbConfig.getString(Constants.DB_USER, Constants.DEFAULT_DB_USER))
                .setPassword(dbConfig.getString(Constants.DB_PASSWORD, Constants.DEFAULT_DB_PASSWORD));

        var poolOptions = new PoolOptions()
                .setMaxSize(dbConfig.getInteger(Constants.DB_POOL_SIZE, Constants.DEFAULT_DB_POOL_SIZE));

        var client =  PgBuilder
                .client()
                .with(poolOptions)
                .connectingTo(connectOptions)
                .using(vertx)
                .build();

        initializeSchema(client);

        return client;
    }

    private static void initializeSchema(SqlClient client)
    {

        try (var inputStream = DBConfig.class.getClassLoader().getResourceAsStream(Constants.SCHEMA))
        {
            if (inputStream == null)
            {
                throw new RuntimeException("schema.sql not found");
            }

            var schemaSql = new String(inputStream.readAllBytes());

            var queries = schemaSql.split(";(\\s*\\n|\\s*$)");

            for (var query : queries)
            {
                var trimmed = query.trim();

                if (!trimmed.isEmpty())
                {
                    client.query(trimmed).execute()
                            .onFailure(err -> System.err.println("Schema init failed: " + err.getMessage()));
                }
            }
        }
        catch (Exception exception)
        {
            System.err.println("Failed to load schema.sql: " + exception.getMessage());
            throw new RuntimeException(exception);
        }
    }
}
