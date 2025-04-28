package org.example.routes;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
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

    private Future<Object> validateCredentialIDs(JsonArray credentialIDs)
    {

        if (credentialIDs == null || credentialIDs.isEmpty())
        {
            return Future.failedFuture("credential_ids must be present and not empty");
        }

        var future = Future.succeededFuture();

        for (int i = 0; i < credentialIDs.size(); i++) {

            var  id = credentialIDs.getInteger(i);

            future = future.compose(v ->
                    dbHelper.fetchOne(Constants.CREDENTIAL_TABLE, FIELD_ID, id)

                            .compose(result ->
                            {
                                if (result == null)
                                {
                                    return Future.failedFuture("Credential ID " + id + " does not exist.");
                                }
                                return Future.succeededFuture();
                            })
            );
        }
        return future;
    }

    public void create(RoutingContext ctx)
    {

        var body = ctx.body().asJsonObject();

        if(!validate(ctx))
        {
            return;
        }

        var credentialIDs = body.getJsonArray("credential_ids", new JsonArray());

        validateCredentialIDs(credentialIDs)

                .onSuccess(v -> {

                    dbHelper.insert(Constants.DISCOVERY_TABLE, body)

                            .onSuccess(results ->
                                    ApiResponse.success(ctx, null, "Discovery created successfully", 201))

                            .onFailure(err ->
                            {
                                ApiResponse.error(ctx, err.getMessage(), 400);
                            });
                })
                .onFailure(err -> {
                    ApiResponse.error(ctx, err.getMessage(), 400);
                });
    }

    public Router init(Router router)
    {
        router.post("/").handler(this::create);

        logger.info("inisde the init method of discovery routes");

        router.get("/").handler(this::findAll);

        router.put("/:id").handler(this::update);

        router.delete("/:id").handler(this::delete);

        router.get("/:id").handler(this::findOne);


        return router;
    }
}
