package org.example.routes;

import io.vertx.ext.web.Router;
import io.vertx.sqlclient.SqlClient;
import org.example.constants.Constants;

import java.util.Map;

public class CredentialRoutes extends BaseApi
{

    /**
     * Constructor to initialize CredentialRoutes with database client.
     *
     * @param client the SQL client for database operations.
     */
    public CredentialRoutes(SqlClient client)
    {
        super(client, Constants.CREDENTIAL_TABLE, Constants.CREDENTIAL_MODULE, CredentialSchema);
    }

    private static final Map<String, Boolean> CredentialSchema = Map.of(
            "name", true,
            "type", true,
            "attributes",true
    );

    /**
     * Initializes the API routes for the Credential module.
     *
     * @param router the Vert.x Router to register the routes.
     * @return
     */
    public Router init(Router router)
    {
        router.post("/" + "credentials" ).handler(this::create);

        router.put("/" + "credentials" + "/:id").handler(this::update);

        router.delete("/" + "credentials" + "/:id").handler(this::delete);

        router.get("/" + "credentials" + "/:id").handler(this::findOne);

        router.get("/" + "credentials").handler(this::findAll);

        return router;
    }
}
