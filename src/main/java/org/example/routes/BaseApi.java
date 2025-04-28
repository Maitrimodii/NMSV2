package org.example.routes;

import io.vertx.ext.web.Router;
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
    // Schema is a map of field names to booleans indicating if they are required
    private static final Logger logger = LoggerFactory.getLogger(BaseApi.class);

    protected BaseApi(SqlClient client, String tableName, String moduleName, Map<String, Boolean> Schema)
    {
        this.tableName = tableName;
        this.moduleName = moduleName;
        this.dbHelper = new DbQueryHelper(client);
        this.schema = schema;
    }

    protected void create(RoutingContext ctx)
    {
        var body = ctx.body().asJsonObject();
        dbHelper.insert(tableName, body)
                .onSuccess(res -> ApiResponse.success(ctx, null, moduleName + " created successfully", 201))
                .onFailure(err -> {
                    logger.error("Failed to create {}: {}", moduleName, err.getMessage());
                    ApiResponse.error(ctx, "Failed to create " + moduleName, 500);
                });
    }

    protected void update(RoutingContext ctx)
    {
        var id = ctx.pathParam("id");
        var body = ctx.body().asJsonObject();

        dbHelper.update(tableName, "id", id, body)
                .onSuccess(res -> ApiResponse.success(ctx, null, moduleName + " updated successfully", 200))
                .onFailure(err -> {
                    logger.error("Failed to update {} with id {}: {}", moduleName, id, err.getMessage());
                    ApiResponse.error(ctx, "Failed to update " + moduleName, 500);
                });
    }

    protected void delete(RoutingContext ctx)
    {
        var id = ctx.pathParam("id");

        dbHelper.delete(tableName, "id", id)
                .onSuccess(res -> ApiResponse.success(ctx, null, moduleName + " deleted successfully", 200))
                .onFailure(err -> {
                    logger.error("Failed to delete {} with id {}: {}", moduleName, id, err.getMessage());
                    ApiResponse.error(ctx, "Failed to delete " + moduleName, 500);
                });
    }

    protected void findOne(RoutingContext ctx)
    {
        var id = ctx.pathParam("id");

        dbHelper.fetchOne(tableName, "id", id)
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
