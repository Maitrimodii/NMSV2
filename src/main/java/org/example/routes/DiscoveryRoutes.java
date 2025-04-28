package org.example.routes;

import io.vertx.ext.web.Router;
import io.vertx.sqlclient.SqlClient;

import java.util.Map;

public class DiscoveryRoutes extends BaseApi
{

    private static final Map<String, Boolean> discoverySchema = Map.of(
            "name", true,
            "ip", true,
            "protocol", false,
            "port", false
    );


    protected DiscoveryRoutes(SqlClient client, String tableName, String moduleName, Map<String, Boolean> Schema)
    {
        super(client, tableName, moduleName, Schema);
    }

    public void setup(Router router)
    {
        router.post("/discovery").handler(this::create);

        router.put("/discovery/:id").handler(this::update);

        router.delete("/discovery/:id").handler(this::delete);

        router.get("/discovery/:id").handler(this::findOne);

        router.get("/discovery").handler(this::findAll);
    }
}
