package org.example.routes;

import io.vertx.ext.web.Router;
import io.vertx.sqlclient.SqlClient;
import org.example.constants.Constants;

public class CredentialRoutes extends BaseApi
{

    /**
     * Constructor to initialize CredentialRoutes with database client.
     *
     * @param client the SQL client for database operations.
     */
    public CredentialRoutes(SqlClient client)
    {
        super(client, Constants.CREDENTIAL_TABLE, Constants.CREDENTIAL_MODULE);
    }

    /**
     * Initializes the API routes for the Credential module.
     *
     * @param router the Vert.x Router to register the routes.
     */
    public void initRoutes(Router router)
    {
        router.post("/" +Constants.CREDENTIAL_MODULE ).handler(this::create);

        router.put("/" + Constants.CREDENTIAL_MODULE + "/:id").handler(this::update);

        router.delete("/" + Constants.CREDENTIAL_MODULE + "/:id").handler(this::delete);

        router.get("/" + Constants.CREDENTIAL_MODULE + "/:id").handler(this::findOne);

        router.get("/" + Constants.CREDENTIAL_MODULE).handler(this::findAll);
    }
}
