package org.example.routes;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import org.example.constants.Constants;
import org.example.utils.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProvisionRoutes extends BaseApi
{
    private static final Logger logger = LoggerFactory.getLogger(ProvisionRoutes.class);

    private static SqlClient client;

    public ProvisionRoutes(SqlClient client)
    {
        super(client, Constants.PROVISION_TABLE, Constants.PROVISION_MODULE, Constants.PROVISION_SCEHMA);

        ProvisionRoutes.client = client;

        logger.info("Initialized ProvisionRoutes API with table {}", Constants.PROVISION_TABLE);

    }

    /**
     * Initializes the API routes for the Provision module.
     *
     * @param router the Vert.x Router to register the routes.
     */
    public Router init(Router router) {

        router.get("/").handler(this::findAll);

        router.get("/:id").handler(this::findOne);

        router.delete("/:id").handler(this::delete);

        router.post("/:id").handler(this::startProvision);

        return router;
    }

    /**
     * Starts a new provision process for a discovered device
     *
     * @param ctx the routing context containing the discovery ID
     */
    private void startProvision(RoutingContext ctx)
    {
        var discoveryId = parseId(ctx);

        if (discoveryId == null)
        {
            return;
        }

        // Verify discovery ID exists and is valid, then create provision
        verifyDiscoveryAndProvision(discoveryId)
                .onSuccess(provisionData ->
                {
                    // Insert provision record
                    dbHelper.insert(Constants.PROVISION_TABLE, provisionData)
                            .onSuccess(res -> ApiResponse.success(ctx, provisionData, "Device provisioning started", 201))
                            .onFailure(err -> {
                                logger.error("Failed to create provision entry: {}", err.getMessage());
                                ApiResponse.error(ctx, "Failed to start provisioning", Constants.HTTP_INTERNAL_SERVER_ERROR);
                            });
                })
                .onFailure(err -> {
                    logger.error("Provision verification failed: {}", err.getMessage());
                    ApiResponse.error(ctx, err.getMessage(), Constants.HTTP_BAD_REQUEST);
                });
    }

    /**
     * Verifies that the discovery exists, is in the correct status, and the device is not already provisioned
     *
     * @param discoveryId The ID of the discovery
     * @return Future with the provision data if verification passes
     */
    private Future<JsonObject> verifyDiscoveryAndProvision(Integer discoveryId)
    {
        // First check if discovery exists and is not in pending status
        return dbHelper.fetchOne(Constants.DISCOVERY_TABLE, Constants.FIELD_ID, discoveryId)
                .compose(discovery -> {
                    if (discovery == null) {
                        return Future.failedFuture("Discovery ID not found");
                    }

                    var status = discovery.getString(Constants.STATUS);

                    if (status == null || status.equals(Constants.PENDING))
                    {
                        return Future.failedFuture("Discovery is still pending or in invalid state");
                    }

                    var ip = discovery.getString(Constants.IP);

                    var port = discovery.getInteger(Constants.PORT, 22);

                    var credentialIdsStr = discovery.getString(Constants.CREDENTIAL_IDS);

                    JsonArray credentialIds = null;

                    if (credentialIdsStr != null && !credentialIdsStr.isEmpty())
                    {
                        try {
                            credentialIds = new JsonArray(credentialIdsStr);
                        }
                        catch (Exception e)
                        {
                            logger.error("Failed to parse credential IDs: {}", e.getMessage());
                            return Future.failedFuture("Invalid credential IDs format");
                        }
                    }

                    // Check if IP is already provisioned
                    var finalCredentialIds = credentialIds;

                    return checkExistingProvision(ip)
                            .compose(exists -> {
                                if (exists) {
                                    return Future.failedFuture("Device with IP " + ip + " is already provisioned");
                                }

                                // Create provision data object
                                var provisionData = new JsonObject()
                                        .put(Constants.IP, ip)
                                        .put(Constants.PORT, port)
                                        .put(Constants.CREDENTIAL_IDS, finalCredentialIds);

                                return Future.succeededFuture(provisionData);
                            });
                });
    }

    /**
     * Checks if a device with the given IP is already provisioned
     *
     * @param ip The IP address to check
     * @return Future with boolean result - true if already provisioned
     */
    private Future<Boolean> checkExistingProvision(String ip)
    {
        var query = String.format("SELECT COUNT(*) as count FROM %s WHERE %s = $1",
                Constants.PROVISION_TABLE, Constants.IP);

        return client.preparedQuery(query)
                .execute(Tuple.of(ip))
                .map(rows ->
                {

                    var row = rows.iterator().next();

                    var count = row.getInteger("count");

                    return count > 0;
                });
    }
}