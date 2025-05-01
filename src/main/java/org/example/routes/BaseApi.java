package org.example.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.SqlClient;
import org.example.db.DbQueryHelper;
import org.example.utils.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Set;

public abstract class BaseApi {

    protected final String tableName;
    protected final String moduleName;
    protected final DbQueryHelper dbHelper;
    protected final JsonSchema jsonSchema;
    private static final Logger logger = LoggerFactory.getLogger(BaseApi.class);
    public static final String FIELD_ID = "id";
    private static final ObjectMapper mapper = new ObjectMapper();

    protected BaseApi(SqlClient client, String tableName, String moduleName, String schemaPath) {
        this.tableName = tableName;
        this.moduleName = moduleName;
        this.dbHelper = new DbQueryHelper(client);

        // Load JSON schema from resources
        try {
            InputStream schemaStream = getClass().getClassLoader().getResourceAsStream(schemaPath);
            if (schemaStream == null) {
                throw new IllegalArgumentException("Schema file not found: " + schemaPath);
            }
            JsonNode schemaNode = mapper.readTree(schemaStream);
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            this.jsonSchema = factory.getSchema(schemaNode);
            logger.info("Initialized {} API with table {} and schema {}", moduleName, tableName, schemaPath);
        } catch (Exception e) {
            logger.error("Failed to load JSON schema for {}: {}", moduleName, e.getMessage());
            throw new RuntimeException("Schema initialization failed", e);
        }
    }

    /**
     * Validates the request body against the JSON schema.
     *
     * @param ctx the routing context containing the request body.
     * @return true if validation fails, false if successful.
     */
    protected boolean validate(RoutingContext ctx) {
        var body = ctx.body().asJsonObject();
        if (body == null) {
            ApiResponse.error(ctx, "Request body cannot be null", 400);
            return true;
        }

        try {
            // Convert Vert.x JsonObject to Jackson JsonNode
            JsonNode bodyNode = mapper.readTree(body.encode());
            // Validate against schema
            Set<ValidationMessage> errors = jsonSchema.validate(bodyNode);
            if (!errors.isEmpty()) {
                // Build error message
                StringBuilder errorMsg = new StringBuilder("Validation errors: ");
                for (ValidationMessage error : errors) {
                    errorMsg.append(error.getMessage()).append("; ");
                }
                ApiResponse.error(ctx, errorMsg.toString(), 400);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Validation failed for {}: {}", moduleName, e.getMessage());
            ApiResponse.error(ctx, "Validation error: " + e.getMessage(), 400);
            return true;
        }
    }

    protected Long parseId(RoutingContext ctx) {
        var idParam = ctx.pathParam(FIELD_ID);
        try {
            return Long.parseLong(idParam);
        } catch (NumberFormatException e) {
            logger.error("Invalid ID format: {}", idParam);
            ApiResponse.error(ctx, "Invalid ID format for '" + FIELD_ID + "'", 400);
            return null;
        }
    }

    protected void create(RoutingContext ctx) {
        var body = ctx.body().asJsonObject();
        if (validate(ctx)) {
            return;
        }

        dbHelper.insert(tableName, body)
                .onSuccess(res -> ApiResponse.success(ctx, null, moduleName + " created successfully", 201))
                .onFailure(err -> {
                    logger.error("Failed to create {}: {}", moduleName, err.getMessage());
                    ApiResponse.error(ctx, "Failed to create " + moduleName, 500);
                });
    }

    protected void update(RoutingContext ctx) {
        var id = parseId(ctx);
        if (id == null) {
            return;
        }

        var body = ctx.body().asJsonObject();
        if (validate(ctx)) {
            return;
        }

        dbHelper.update(tableName, FIELD_ID, id, body)
                .onSuccess(res -> ApiResponse.success(ctx, null, moduleName + " updated successfully", 200))
                .onFailure(err -> {
                    logger.error("Failed to update {} with id {}: {}", moduleName, id, err.getMessage());
                    ApiResponse.error(ctx, "Failed to update " + moduleName, 500);
                });
    }

    protected void delete(RoutingContext ctx) {
        var id = parseId(ctx);
        if (id == null) {
            return;
        }

        dbHelper.delete(tableName, FIELD_ID, id)
                .onSuccess(res -> ApiResponse.success(ctx, null, moduleName + " deleted successfully", 200))
                .onFailure(err -> {
                    logger.error("Failed to delete {} with id {}: {}", moduleName, id, err.getMessage());
                    ApiResponse.error(ctx, "Failed to delete " + moduleName, 500);
                });
    }

    protected void findOne(RoutingContext ctx) {
        var id = parseId(ctx);
        if (id == null) {
            return;
        }

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

    protected void findAll(RoutingContext ctx) {
        logger.info("Fetching all {} records", moduleName);
        dbHelper.fetchAll(tableName)
                .onSuccess(rows -> ApiResponse.success(ctx, rows, moduleName + " list fetched", 200))
                .onFailure(err -> {
                    logger.error("Failed to fetch all {}: {}", moduleName, err.getMessage());
                    ApiResponse.error(ctx, "Failed to fetch all " + moduleName, 500);
                });
    }
}