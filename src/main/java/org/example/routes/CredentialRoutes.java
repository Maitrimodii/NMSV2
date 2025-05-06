package org.example.routes;

import io.vertx.ext.web.Router;
import io.vertx.sqlclient.SqlClient;
import org.example.constants.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CredentialRoutes extends BaseApi
{

    private static final Logger logger = LoggerFactory.getLogger(CredentialRoutes.class);

    /**
     * Constructor to initialize CredentialRoutes with database client.
     *
     * @param client the SQL client for database operations.
     */
    public CredentialRoutes(SqlClient client)
    {
        super(client, Constants.CREDENTIAL_TABLE, Constants.CREDENTIAL_MODULE, Constants.CREDENTIAL_SCEHMA);

        logger.info("Initialized Credential API with table {}", Constants.CREDENTIAL_TABLE);
    }

    /**
     * Initializes the API routes for the Credential module.
     *
     * @param router the Vert.x Router to register the routes.
     * @return the configured router.
     */
    public Router init(Router router)
    {
        router.post("/").handler(this::create);

        router.get("/").handler(this::findAll);

        router.put("/:id").handler(this::update);

        router.delete("/:id").handler(this::delete);

        router.get("/:id").handler(this::findOne);

        return router;
    }
}