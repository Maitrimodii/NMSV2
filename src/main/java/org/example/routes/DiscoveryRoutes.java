package org.example.routes;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.SqlClient;
import org.example.constants.Constants;
import org.example.utils.ApiResponse;
import org.example.db.DbQueryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DiscoveryRoutes extends BaseApi
{
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryRoutes.class);

    protected final DbQueryHelper dbHelper;

    public DiscoveryRoutes(SqlClient client)
    {
        super(client, Constants.DISCOVERY_TABLE, Constants.DISCOVERY_MODULE, Constants.DISCOVERY_SCEHMA);

        this.dbHelper = new DbQueryHelper(client);

        logger.info("Initialized DiscoveryRoutes API with table {}", Constants.DISCOVERY_TABLE);
    }


    /**
     * Initiates a discovery process for a specific discovery profile.
     * <p>
     * This method performs the following steps:
     * <ul>
     *   <li>Extracts the discovery ID from the request path.</li>
     *   <li>Fetches the corresponding discovery record from the database.</li>
     *   <li>Parses the credential IDs and prepares a payload.</li>
     *   <li>Sends the payload to the {@code DiscoveryEngine} via the event bus.</li>
     *   <li>Handles success or failure response and sends appropriate API response.</li>
     * </ul>
     *
     * @param ctx the Vert.x {@link RoutingContext} containing the HTTP request context
     */
    public void runDiscovery(RoutingContext ctx)
    {
        var discoveryId = ctx.pathParam(FIELD_ID);

        var id = Integer.parseInt(discoveryId);

        // Fetch discovery profile
        dbHelper.fetchOne(Constants.DISCOVERY_TABLE, FIELD_ID, id)
                .compose(discovery -> {
                    logger.info(String.valueOf(discovery));

                    // Prepare payload for DiscoveryEngine
                    var payload = new JsonObject()
                            .put(Constants.REQUEST_TYPE, Constants.DISCOVERY)
                            .put(Constants.DISCOVERY_ID, id)
                            .put(Constants.IP, discovery.getString(Constants.IP))
                            .put(Constants.PORT, discovery.getInteger(Constants.PORT, 22))
                            .put(Constants.CREDENTIAL_IDS, discovery.getString(Constants.CREDENTIAL_IDS, "[]"));

                    logger.info(String.valueOf(payload));

                    var credentialIdsStr = discovery.getString(Constants.CREDENTIAL_IDS, "[]");

                    try
                    {
                        var credentialIdsArray = new JsonArray(credentialIdsStr);

                        logger.info(String.valueOf(credentialIdsArray));

                        payload.put(Constants.CREDENTIAL_IDS, credentialIdsArray);
                    }
                    catch (Exception e)
                    {
                        logger.error("Failed to parse credential_ids for discovery id={}: {}", discoveryId, e.getMessage());

                        return Future.failedFuture("Invalid credential_ids format");
                    }

                    // Send to event bus

                    return ctx.vertx().eventBus().request(Constants.DISCOVERY_ADDRESS, payload)
                            .map(message -> (JsonObject) message.body());
                })

                .onSuccess(result -> ApiResponse.success(ctx, result, result.getString("error_message"), Constants.HTTP_OK))

                .onFailure(err -> {
                    logger.error("Run discovery failed for id={}: {}", discoveryId, err.getMessage());

                    ApiResponse.error(ctx, err.getMessage(), Constants.HTTP_BAD_REQUEST);
                });
    }

    /**
     * Initializes the routes for the discovery API.
     * @param router
     * @return
     */
    public Router init(Router router)
    {
        router.post("/").handler(this::create);

        logger.info("inside the init method of discovery routes");

        router.get("/").handler(this::findAll);

        router.put("/:id").handler(this::update);

        router.delete("/:id").handler(this::delete);

        router.get("/:id").handler(this::findOne);

        router.post("/:id").handler(this::runDiscovery);

        return router;
    }
}
