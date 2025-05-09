package org.example.db;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import org.example.constants.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DbQueryHelper
{

    private static final Logger logger = LoggerFactory.getLogger(DbQueryHelper.class);

    private final SqlClient client;

    public DbQueryHelper(SqlClient client)
    {
        this.client = client;
    }

    /**
     * Inserts a new record into the specified table.
     *
     * @param table The table name.
     * @param data  The data to insert as a JsonObject.
     * @return Future representing completion of the insert operation.
     */

    public Future<RowSet<Row>> insert(String table, JsonObject data)
    {
        try {
            var fieldNames = data.stream()
                    .map(Map.Entry::getKey)
                    .toList();

            var columns = String.join(", ", fieldNames);

            var placeholders = IntStream.rangeClosed(1, fieldNames.size())
                    .mapToObj(i -> "$" + i)
                    .collect(Collectors.joining(", "));


            var query = String.format(Constants.SQL_INSERT, table, columns, placeholders);

            var values = Tuple.tuple();

            for (var field : fieldNames) {
                var value = data.getValue(field);

                if (value instanceof JsonArray || value instanceof JsonObject) {
                    values.addValue(value.toString());
                } else {
                    values.addValue(value);
                }
            }

            logger.info("Executing insert query");

            return client
                    .preparedQuery(query)
                    .execute(values)
                    .mapEmpty();
        }
        catch (Exception exception)
        {
            logger.error("Unexpected error during INSERT operation for table {}: {}", table, exception.getMessage(), exception);

            return Future.failedFuture("Unexpected error during insert: " + exception.getMessage());
        }
    }

    /**
     * Updates a record in the specified table by ID column and value.
     *
     * @param table    The table name.
     * @param idColumn The ID column name used for the WHERE clause.
     * @param idValue  The value to match for updating.
     * @param data     The fields to update as a JsonObject.
     * @return Future representing completion of the update operation.
     */
    public Future<Void> update(String table, String idColumn, Object idValue, JsonObject data)
    {

        try
        {
            var fieldNames = data.stream().map(Map.Entry::getKey).toList();

            var setClause = IntStream.rangeClosed(1, fieldNames.size())
                    .mapToObj(i -> fieldNames.get(i - 1) + " = $" + i)
                    .collect(Collectors.joining(", "));

            var query = String.format(Constants.SQL_UPDATE, table, setClause, idColumn, fieldNames.size() + 1);

            var values = Tuple.tuple();

            for (var field : fieldNames)
            {
                values.addValue(data.getValue(field));
            }

            values.addValue(idValue);

            logger.info("Executing update query");

            return client
                    .preparedQuery(query)
                    .execute(values)
                    .mapEmpty();
        }
        catch (Exception exception)
        {
            logger.error("Unexpected error during UPDATE operation for table {}: {}", table, exception.getMessage(), exception);

            return Future.failedFuture("Unexpected error during update: " + exception.getMessage());
        }

    }

    /**
     * Deletes a record from the specified table by ID column and value.
     *
     * @param table    The table name.
     * @param idColumn The ID column name used for the WHERE clause.
     * @param idValue  The value to match for deletion.
     * @return Future representing completion of the delete operation.
     */
    public Future<Void> delete(String table, String idColumn, Object idValue)
    {
        try
        {
            var query = String.format(Constants.SQL_DELETE, table, idColumn);

            logger.info("Executing DELETE query: {}", query);

            return client
                    .preparedQuery(query)
                    .execute(Tuple.of(idValue))
                    .mapEmpty();
        }
        catch(Exception exception)
        {
            logger.error("Unexpected error during DELETE operation for table {}: {}", table, exception.getMessage(), exception);

            return Future.failedFuture("Unexpected error during delete: " + exception.getMessage());
        }

    }

    /**
     * Fetches a single record from the specified table by ID column and value.
     *
     * @param table    The table name.
     * @param idColumn The ID column name used for the WHERE clause.
     * @param idValue  The value to match for fetching.
     * @return Future containing the fetched record as a JsonObject.
     */
    public Future<JsonObject> fetchOne(String table, String idColumn, Object idValue)
    {
        try {
            var query = String.format(Constants.SQL_SELECT_ONE, table, idColumn);

            logger.info("Executing SELECT query: {}", query);

            return client
                    .preparedQuery(query)
                    .execute(Tuple.of(idValue))
                    .map(rows ->
                    {
                        var row = rows.iterator().next();

                        logger.info(String.valueOf(row));

                        return row.toJson();
                    });
        }
        catch (Exception exception)
        {
            logger.error("Unexpected error during FETCH ONE operation for table {}: {}", table, exception.getMessage(), exception);

            return Future.failedFuture("Unexpected error during fetch one: " + exception.getMessage());
        }
    }

    /**
     * Fetches all records from the specified table.
     *
     * @param table The table name.
     * @return Future containing a list of all records as JsonObjects.
     */
    public Future<List<JsonObject>> fetchAll(String table)
    {
        try
        {
            var query = String.format(Constants.SQL_SELECT_ALL, table);

            logger.info("Executing SELECT ALL query: {}", query);

            return client
                    .query(query)
                    .execute()
                    .map(rows ->
                    {
                        var result = new ArrayList<JsonObject>();

                        for (var row : rows)
                        {
                            result.add(row.toJson());
                        }
                        return result;
                    });
        }
        catch (Exception exception)
        {
            logger.error("Unexpected error during FETCH ALL operation for table {}: {}", table, exception.getMessage(), exception);

            return Future.failedFuture("Unexpected error during fetch all: " + exception.getMessage());
        }

    }
}
