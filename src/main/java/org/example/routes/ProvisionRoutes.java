package org.example.routes;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.SqlClient;
import org.example.constants.Constants;

import java.util.Map;

public class ProvisionRoutes extends BaseApi
{
    public ProvisionRoutes(SqlClient client)
    {
        super(client, Constants.PROVISION_TABLE, Constants.PROVISION_MODULE, ProvisionSchema);

    }

    private static final Map<String, Boolean> ProvisionSchema = Map.of(
            "ip", true,
            "port", false,
            "credential_ids",true
    );


    /**
     * Initializes the API routes for the Credential module.
     *
     * @param router the Vert.x Router to register the routes.
     * @return
     */
    public Router init(Router router)
    {

        router.get("/").handler(this::findAll);

        router.get("/:id").handler(this::findOne);

        router.delete("/:id").handler(this::delete);

        router.post(":/id").handler(this::startProvision);

        return router;
    }

    public void startProvision(RoutingContext ctx)
    {

    }
}
