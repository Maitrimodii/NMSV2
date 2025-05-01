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


import java.util.Map;

public class DiscoveryRoutes extends BaseApi
{
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryRoutes.class);

    protected final DbQueryHelper dbHelper;

    private static final Map<String, Boolean> discoverySchema = Map.of(
            "name", true,
            "ip", true,
            "port", false,
            "result", false,
            "credential_ids", true
    );

    public DiscoveryRoutes(SqlClient client)
    {
        super(client, Constants.DISCOVERY_TABLE, Constants.DISCOVERY_MODULE, discoverySchema);

        this.dbHelper = new DbQueryHelper(client);

        logger.info("Initialized DiscoveryRoutes API with table {}", Constants.DISCOVERY_TABLE);
    }


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

                .onSuccess(result -> ApiResponse.success(ctx, result, result.getString("error_message"), 200))

                .onFailure(err -> {
                    logger.error("Run discovery failed for id={}: {}", discoveryId, err.getMessage());

                    ApiResponse.error(ctx, err.getMessage(), 400);
                });
    }

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
