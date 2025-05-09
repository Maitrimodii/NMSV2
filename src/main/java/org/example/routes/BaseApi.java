package org.example.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.SqlClient;
import org.example.constants.Constants;
import org.example.db.DbQueryHelper;
import org.example.utils.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseApi
{

    protected final String tableName;

    protected final String moduleName;

    protected final DbQueryHelper dbHelper;

    protected final JsonSchema jsonSchema;

    private static final Logger logger = LoggerFactory.getLogger(BaseApi.class);

    public static final String FIELD_ID = "id";

    private static final ObjectMapper mapper = new ObjectMapper();

    protected BaseApi(SqlClient client, String tableName, String moduleName, String schemaPath)
    {
        this.tableName = tableName;

        this.moduleName = moduleName;

        this.dbHelper = new DbQueryHelper(client);

        // Load JSON schema from resources
        try(var schemaStream = getClass().getClassLoader().getResourceAsStream(schemaPath))
        {
            if (schemaStream == null)
            {
                throw new IllegalArgumentException("Schema file not found: " + schemaPath);
            }
            var schemaNode = mapper.readTree(schemaStream);

            var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

            this.jsonSchema = factory.getSchema(schemaNode);

            logger.info("Initialized {} API with table {} and schema {}", moduleName, tableName, schemaPath);
        }
        catch (Exception exception)
        {
            logger.error("Failed to load JSON schema for {}: {}", moduleName, exception.getMessage());

            throw new RuntimeException("Schema initialization failed", exception);
        }
    }

    /**
     * Validates the request body against the JSON schema.
     *
     * @param ctx the routing context containing the request body.
     * @return false if validation fails, true if successful.
     */
    protected boolean validate(RoutingContext ctx)
    {
        var body = ctx.body().asJsonObject();

        if (body == null)
        {
            ApiResponse.error(ctx, "Request body cannot be null", Constants.HTTP_BAD_REQUEST);

            return false;
        }

        try
        {
            // Convert Vert.x JsonObject to Jackson JsonNode
            var bodyNode = mapper.readTree(body.encode());

            // Validate against schema
            var errors = jsonSchema.validate(bodyNode);

            if (!errors.isEmpty())
            {
                // Build error message
                var errorMsg = new StringBuilder("Validation errors: ");

                for (var error : errors)
                {
                    errorMsg.append(error.getMessage()).append("; ");
                }

                logger.error(errorMsg.toString());

                ApiResponse.error(ctx, "fields are required", Constants.HTTP_BAD_REQUEST);

                return false;
            }
            return true;
        }
        catch (Exception exception)
        {
            logger.error("Validation failed for {}: {}", moduleName, exception.getMessage());

            ApiResponse.error(ctx, "Validation error: " , Constants.HTTP_BAD_REQUEST);

            return false;
        }
    }

    /**
     * Parses the ID from the request path parameter.
     *
     * @param ctx the routing context containing the request.
     * @return the parsed ID, or null if parsing fails.
     */
    protected Integer parseId(RoutingContext ctx)
    {
        var idParam = ctx.pathParam(FIELD_ID);

        try
        {
            return Integer.parseInt(idParam);
        }
        catch (Exception exception)
        {
            logger.error("Invalid ID format: {}", idParam);

            ApiResponse.error(ctx, "Invalid ID format for '" + FIELD_ID + "'", Constants.HTTP_BAD_REQUEST);

            return null;
        }
    }

    /**
     * Creates a new record in the table.
     *
     * @param ctx the routing context containing the request.
     */
    protected void create(RoutingContext ctx)
    {
        try
        {

            var body = ctx.body().asJsonObject();

            if (!validate(ctx))
            {
                return;
            }

            dbHelper.insert(tableName, body)
                    .onSuccess(res -> ApiResponse.success(ctx, null, moduleName + " created successfully", 201))
                    .onFailure(err -> {
                        logger.error("Failed to create {}: {}", moduleName, err.getMessage());

                        ApiResponse.error(ctx, "Failed to create " + moduleName, Constants.HTTP_INTERNAL_SERVER_ERROR);
                    });
        }
        catch (Exception exception)
        {
            logger.error("Failed to create {}: {}", moduleName, exception.getMessage());
            ApiResponse.error(ctx, "Failed to create " + moduleName, Constants.HTTP_INTERNAL_SERVER_ERROR);
        }

    }

    /**
     * Updates a record with the specified ID in the table.
     *
     * @param ctx the routing context containing the request.
     */
    protected void update(RoutingContext ctx)
    {
        try
        {
            var id = parseId(ctx);

            if (id == null)
            {
                return;
            }

            var body = ctx.body().asJsonObject();

            if (validate(ctx))
            {
                return;
            }

            dbHelper.update(tableName, FIELD_ID, id, body)
                    .onSuccess(res -> ApiResponse.success(ctx, null, moduleName + " updated successfully", Constants.HTTP_OK))
                    .onFailure(err -> {
                        logger.error("Failed to update {} with id {}: {}", moduleName, id, err.getMessage());
                        ApiResponse.error(ctx, "Failed to update " + moduleName, Constants.HTTP_INTERNAL_SERVER_ERROR);
                    });
        }
        catch (Exception exception)
        {
            logger.error("Failed to update {}: {}", moduleName,exception.getMessage());
            ApiResponse.error(ctx, "Failed to update " + moduleName, Constants.HTTP_INTERNAL_SERVER_ERROR);
        }

    }

    /**
     * Deletes a record with the specified ID from the table.
     *
     * @param ctx the routing context containing the request.
     */
    protected void delete(RoutingContext ctx)
    {
        try
        {
            var id = parseId(ctx);
            if (id == null)
            {
                return;
            }

            dbHelper.delete(tableName, FIELD_ID, id)
                    .onSuccess(res -> ApiResponse.success(ctx, null, moduleName + " deleted successfully", Constants.HTTP_OK))
                    .onFailure(err -> {
                        logger.error("Failed to delete {} with id {}: {}", moduleName, id, err.getMessage());
                        ApiResponse.error(ctx, "Failed to delete " + moduleName, Constants.HTTP_INTERNAL_SERVER_ERROR);
                    });
        }
        catch (Exception exception)
        {
            logger.error("Failed to delete {} : {}", moduleName, exception.getMessage());

            ApiResponse.error(ctx, "Failed to delete " + moduleName, Constants.HTTP_INTERNAL_SERVER_ERROR);
        }

    }

    /**
     * Fetch record with id from the specified table.
     * @param ctx the routing context containing the request.
     */
    protected void findOne(RoutingContext ctx)
    {
        try
        {
            var id = parseId(ctx);

            if (id == null)
            {
                return;
            }

            dbHelper.fetchOne(tableName, FIELD_ID, id)
                    .onSuccess(row -> {
                        if (row != null) {
                            ApiResponse.success(ctx, row, moduleName + " found", Constants.HTTP_OK);
                        } else {
                            ApiResponse.error(ctx, moduleName + " not found", Constants.HTTP_NOT_FOUND);
                        }
                    })
                    .onFailure(err -> {
                        logger.error("Failed to fetch {} with id {}: {}", moduleName, id, err.getMessage());
                        ApiResponse.error(ctx, "Failed to fetch " + moduleName, Constants.HTTP_INTERNAL_SERVER_ERROR);
                    });
        }
        catch (Exception exception)
        {
            logger.error("Failed to fetch {}: {}", moduleName, exception.getMessage());

            ApiResponse.error(ctx, "Failed to fetch " + moduleName, Constants.HTTP_INTERNAL_SERVER_ERROR);
        }

    }

    /**
     * Fetches all records from the specified table.
     *
     * @param ctx the routing context containing the request.
     */
    protected void findAll(RoutingContext ctx)
    {
        try
        {
            logger.info("Fetching all {} records", moduleName);

            dbHelper.fetchAll(tableName)
                    .onSuccess(rows -> ApiResponse.success(ctx, rows, moduleName + " list fetched", Constants.HTTP_OK))
                    .onFailure(err -> {
                        logger.error("Failed to fetch all {}: {}", moduleName, err.getMessage());
                        ApiResponse.error(ctx, "Failed to fetch all " + moduleName, Constants.HTTP_INTERNAL_SERVER_ERROR);
                    });
        }
        catch (Exception exception)
        {
            logger.error("Failed to fetch all {}: {}", moduleName, exception.getMessage());

            ApiResponse.error(ctx, "Failed to fetch all " + moduleName, Constants.HTTP_INTERNAL_SERVER_ERROR);
        }

    }
}