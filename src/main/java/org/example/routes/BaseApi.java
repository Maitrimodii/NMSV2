package org.example.routes;

import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.SqlClient;
import org.example.db.DbQueryHelper;
import org.example.utils.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public abstract class BaseApi
{

    protected final String tableName;

    protected final String moduleName;

    protected final DbQueryHelper dbHelper;

    protected final Map<String, Boolean> schema;

    private static final Logger logger = LoggerFactory.getLogger(BaseApi.class);

    public static final String FIELD_ID = "id";

    protected BaseApi(SqlClient client, String tableName, String moduleName, Map<String, Boolean> Schema)
    {
        this.tableName = tableName;

        this.moduleName = moduleName;

        this.dbHelper = new DbQueryHelper(client);

        this.schema = Schema;

        logger.info("Initialized {} API with table {}", moduleName, tableName);
    }

    /**
     * Validates the fields based on the schema.
     * Checks if all required fields are present in the request body.
     *
     * @param ctx the routing context containing the request body.
     * @return true if validation is successful, false otherwise.
     */
    protected boolean validate(RoutingContext ctx)
    {
        var body = ctx.body().asJsonObject();

        if(body == null)
        {
            ApiResponse.error(ctx, "Request body cannot be null", 400);
            return true;
        }

        // Iterate through the schema to check for required fields
        for (var entry : schema.entrySet())
        {
            String fieldName = entry.getKey();
            Boolean isRequired = entry.getValue();

            // If the field is required and not present in the request body, return false
            if (isRequired && !body.containsKey(fieldName))
            {
                ApiResponse.error(ctx, "Missing required field: " + fieldName, 400);
                return true;
            }

            // Additional check for empty or null values in required fields
            if (isRequired && body.getValue(fieldName) == null)
            {
                ApiResponse.error(ctx, "Field " + fieldName + " cannot be null", 400);
                return true;
            }
        }

        return false;
    }

    protected Long parseId(RoutingContext ctx)
    {
        var idParam = ctx.pathParam(FIELD_ID);

        try
        {
            return Long.parseLong(idParam);
        }
        catch (NumberFormatException e)
        {
            logger.error("Invalid ID format: {}", idParam);
            ApiResponse.error(ctx, "Invalid ID format for '" + FIELD_ID + "'", 400);
            return null;
        }
    }

    protected void create(RoutingContext ctx)
    {
        var body = ctx.body().asJsonObject();

        if (validate(ctx))
        {
            return;
        }

        dbHelper.insert(tableName, body)
                .onSuccess(res -> ApiResponse.success(ctx, null, moduleName + " created successfully", 201))
                .onFailure(err -> {
                    logger.error("Failed to create {}: {}", moduleName, err.getMessage());
                    ApiResponse.error(ctx, "Failed to create " + moduleName, 500);
                });
    }

    protected void update(RoutingContext ctx)
    {
        var id = parseId(ctx);

        if(id == null)
        {
            return;
        }

        var body = ctx.body().asJsonObject();

        dbHelper.update(tableName, FIELD_ID, id, body)
                .onSuccess(res -> ApiResponse.success(ctx, null, moduleName + " updated successfully", 200))
                .onFailure(err -> {
                    logger.error("Failed to update {} with id {}: {}", moduleName, id, err.getMessage());
                    ApiResponse.error(ctx, "Failed to update " + moduleName, 500);
                });
    }

    protected void delete(RoutingContext ctx)
    {
        var id = parseId(ctx);

        dbHelper.delete(tableName, FIELD_ID, id)
                .onSuccess(res -> ApiResponse.success(ctx, null, moduleName + " deleted successfully", 200))
                .onFailure(err -> {
                    logger.error("Failed to delete {} with id {}: {}", moduleName, id, err.getMessage());
                    ApiResponse.error(ctx, "Failed to delete " + moduleName, 500);
                });
    }

    protected void findOne(RoutingContext ctx)
    {
        var id = parseId(ctx);

        dbHelper.fetchOne(tableName, FIELD_ID, id)
                .onSuccess(row -> {
                    if (row != null)
                    {
                        ApiResponse.success(ctx, row, moduleName + " found", 200);
                    }
                    else
                    {
                        ApiResponse.error(ctx, moduleName + " not found", 404);
                    }
                })
                .onFailure(err -> {
                    logger.error("Failed to fetch {} with id {}: {}", moduleName, id, err.getMessage());
                    ApiResponse.error(ctx, "Failed to fetch " + moduleName, 500);
                });
    }

    protected void findAll(RoutingContext ctx)
    {
        logger.info("Fetching all {} records", moduleName, tableName);
        dbHelper.fetchAll(tableName)
                .onSuccess(rows -> ApiResponse.success(ctx, rows, moduleName + " list fetched", 200))
                .onFailure(err -> {
                    logger.error("Failed to fetch all {}: {}", moduleName, err.getMessage());
                    ApiResponse.error(ctx, "Failed to fetch all " + moduleName, 500);
                });
    }
}
