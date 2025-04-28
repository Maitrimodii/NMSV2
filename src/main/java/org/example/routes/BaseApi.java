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
            return false;
        }

        // Iterate through the schema to check for required fields
        for (Map.Entry<String, Boolean> entry : schema.entrySet()) {
            String fieldName = entry.getKey();
            Boolean isRequired = entry.getValue();

            // If the field is required and not present in the request body, return false
            if (isRequired && !body.containsKey(fieldName)) {
                ApiResponse.error(ctx, "Missing required field: " + fieldName, 400);
                return false;
            }

            // Additional check for empty or null values in required fields
            if (isRequired && body.getValue(fieldName) == null) {
                ApiResponse.error(ctx, "Field " + fieldName + " cannot be null", 400);
                return false;
            }
        }

        return true;
    }


    protected void create(RoutingContext ctx)
    {
        var body = ctx.body().asJsonObject();

        if (!validate(ctx)) {
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
        var id = ctx.pathParam(FIELD_ID);

        var body = ctx.body().asJsonObject();

        if (!validate(ctx)) {
            return;
        }

        dbHelper.update(tableName, FIELD_ID, id, body)
                .onSuccess(res -> ApiResponse.success(ctx, null, moduleName + " updated successfully", 200))
                .onFailure(err -> {
                    logger.error("Failed to update {} with id {}: {}", moduleName, id, err.getMessage());
                    ApiResponse.error(ctx, "Failed to update " + moduleName, 500);
                });
    }

    protected void delete(RoutingContext ctx)
    {
        var id = ctx.pathParam(FIELD_ID);

        dbHelper.delete(tableName, FIELD_ID, id)
                .onSuccess(res -> ApiResponse.success(ctx, null, moduleName + " deleted successfully", 200))
                .onFailure(err -> {
                    logger.error("Failed to delete {} with id {}: {}", moduleName, id, err.getMessage());
                    ApiResponse.error(ctx, "Failed to delete " + moduleName, 500);
                });
    }

    protected void findOne(RoutingContext ctx)
    {
        var id = ctx.pathParam(FIELD_ID);

        dbHelper.fetchOne(tableName, FIELD_ID, id)
                .onSuccess(row -> {
                    if (row != null) {
                        ApiResponse.success(ctx, row, moduleName + " found", 200);
                    } else {
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
        dbHelper.fetchAll(tableName)
                .onSuccess(rows -> ApiResponse.success(ctx, rows, moduleName + " list fetched", 200))
                .onFailure(err -> {
                    logger.error("Failed to fetch all {}: {}", moduleName, err.getMessage());
                    ApiResponse.error(ctx, "Failed to fetch all " + moduleName, 500);
                });
    }
}
