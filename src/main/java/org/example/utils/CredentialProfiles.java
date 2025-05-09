package org.example.utils;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.constants.Constants;
import org.example.db.DbQueryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class for handling credential-related operations
 */
public class CredentialProfiles {
    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialProfiles.class);

    private final DbQueryHelper dbHelper;

    public CredentialProfiles(DbQueryHelper dbHelper)
    {
        this.dbHelper = dbHelper;
    }

    /**
     * Fetches credential profiles from the database based on the provided credential IDs
     *
     * @param credentialIds The array of credential IDs
     * @return Future that completes with the array of credential profiles
     */
    public Future<JsonArray> fetchCredentialProfiles(JsonArray credentialIds)
    {
        var profiles = new JsonArray();


        for (var i = 0; i < credentialIds.size(); i++)
        {
            var idObj = credentialIds.getValue(i);

            if (!(idObj instanceof Integer credentialId))
            {
                LOGGER.warn("Invalid credential ID format at index {}: {}", i, idObj);

                continue;
            }

            return Future.succeededFuture(profiles).compose(res ->
                    dbHelper.fetchOne(Constants.CREDENTIAL_TABLE, Constants.FIELD_ID, credentialId)
                            .map(credential -> {
                                if (credential != null)
                                {

                                    credential.put(Constants.CREDENTIAL_ID, credentialId);
                                    res.add(credential);
                                }
                                else
                                {
                                    LOGGER.warn("Credential not found with ID: {}", credentialId);
                                }
                                return res;
                            })
            );
        }
        return Future.succeededFuture(profiles);
    }

    /**
     * Formats the credentials into a JSON array for plugin input
     * @param credentialProfiles The array of credential profiles
     * @return Formatted JSON array of credentials
     */
    public JsonArray formatCredentials(JsonArray credentialProfiles)
    {
        var formattedCredentials = new JsonArray();

        for (var i = 0; i < credentialProfiles.size(); i++)
        {
            var credential = credentialProfiles.getJsonObject(i);

            if (credential != null)
            {
                var attributesStr = credential.getString(Constants.ATTRIBUTES);

                JsonObject attributes;

                try
                {
                    attributes = new JsonObject(attributesStr);
                }
                catch (Exception exception)
                {
                    LOGGER.warn("Skipping credential ID {} due to invalid attributes JSON: {}",
                            credential.getInteger(Constants.FIELD_ID, i), exception.getMessage());
                    continue;
                }

                var formattedCredential = new JsonObject()
                        .put(Constants.CREDENTIAL_NAME, credential.getString("name", "credential_" + credential.getInteger(Constants.FIELD_ID, i)))
                        .put(Constants.CREDENTIAL_TYPE, credential.getString(Constants.TYPE, Constants.SSH))
                        .put(Constants.ATTRIBUTES, attributes);

                formattedCredentials.add(formattedCredential);
            }
        }
        return formattedCredentials;
    }
}