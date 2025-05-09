package org.example.db;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import org.example.constants.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBConfig {
    private static final Logger logger = LoggerFactory.getLogger(DBConfig.class);

    /**
     * Creates a PostgreSQL connection pool using the given Vertx instance and configuration.
     *
     * @param vertx  The Vert.x instance.
     * @param config The configuration JsonObject containing database connection details.
     * @return A Future containing the configured SqlClient (connection pool).
     */
    public static Future<SqlClient> createPgPool(Vertx vertx, JsonObject config)
    {

        var dbConfig = config.getJsonObject(Constants.DB);

        var connectOptions = new PgConnectOptions()
                .setPort(dbConfig.getInteger(Constants.DB_PORT, Constants.DEFAULT_DB_PORT))
                .setHost(dbConfig.getString(Constants.DB_HOST, Constants.DEFAULT_DB_HOST))
                .setDatabase(dbConfig.getString(Constants.DB_DATABASE, Constants.DEFAULT_DB_DATABASE))
                .setUser(dbConfig.getString(Constants.DB_USER, Constants.DEFAULT_DB_USER))
                .setPassword(dbConfig.getString(Constants.DB_PASSWORD, Constants.DEFAULT_DB_PASSWORD));

        var poolOptions = new PoolOptions()
                .setMaxSize(dbConfig.getInteger(Constants.DB_POOL_SIZE, Constants.DEFAULT_DB_POOL_SIZE));

        var client = PgBuilder
                .client()
                .with(poolOptions)
                .connectingTo(connectOptions)
                .using(vertx)
                .build();

        // Test the connection
        return client.query("SELECT 1").execute()
                .compose(result -> initializeSchema(vertx, client))
                .map(client)
                .recover(err -> {

                    logger.error("Failed to connect to database", err);

                    client.close();

                    return Future.failedFuture("Failed to connect to database: " + err.getMessage());
                });
    }

    /**
     * Initializes the schema by executing the SQL queries from the schema file.
     *
     * @param vertx  The Vert.x instance.
     * @param client The SqlClient (database connection pool).
     * @return A Future that completes when the schema is initialized.
     */
    private static Future<Void> initializeSchema(Vertx vertx, SqlClient client)
    {
        try
        {
            return vertx.fileSystem().readFile(Constants.SCHEMA)
                    .compose(buffer -> {

                        var schemaSql = buffer.toString();

                        var queries = schemaSql.split(";(\\s*\\n|\\s*$)");

                        return executeQueries(client, queries, 0);
                    })
                    .onFailure(err -> logger.error("Schema initialization failed", err));
        }
        catch (Exception exception)
        {
            logger.error("Schema initialization failed :{}", exception.getMessage());

            return Future.failedFuture("Schema initialization failed :" + exception.getMessage());
        }

    }

    /**
     * Executes schema queries sequentially.
     *
     * @param client The SqlClient.
     * @param queries The array of SQL queries.
     * @param index The current query index.
     * @return A Future that completes when all queries are executed.
     */
    private static Future<Void> executeQueries(SqlClient client, String[] queries, int index) {
        try
        {
            if (index >= queries.length)
            {
                return Future.succeededFuture();
            }

            var query = queries[index].trim();

            if (query.isEmpty())
            {
                return executeQueries(client, queries, index + 1);
            }


            return client.query(query).execute()
                    .compose(result -> executeQueries(client, queries, index + 1))
                    .onFailure(err -> logger.error("Failed executing SQL query: {}", query, err));
        }
        catch (Exception exception)
        {
            logger.error("Failed executing queries" + exception.getMessage());

            return Future.failedFuture("failed executing queries" + exception.getMessage());
        }

    }

}