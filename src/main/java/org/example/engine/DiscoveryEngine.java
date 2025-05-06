package org.example.engine;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.constants.Constants;
import org.example.db.DbQueryHelper;
import org.example.utils.ProcessBuilderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscoveryEngine extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryEngine.class);

    private final DbQueryHelper dbHelper;

    public DiscoveryEngine(DbQueryHelper dbHelper)
    {
        this.dbHelper = dbHelper;
    }

    @Override
    public void start(Promise<Void> promise)
    {
        vertx.eventBus().consumer(Constants.DISCOVERY_ADDRESS, this::handleDiscoveryRequest);

        LOGGER.info("DiscoveryEngine started, listening on {}", Constants.DISCOVERY_ADDRESS);

        promise.complete();
    }

    /**
     * Handles incoming discovery requests from the event bus
     * @param message Message containing the discovery request payload
     */
    private void handleDiscoveryRequest(Message<JsonObject> message)
    {
        var payload = message.body();

        var discoveryId = payload.getString(Constants.DISCOVERY_ID);

        // Process discovery asynchronously
        processDiscovery(payload, discoveryId)
                .onSuccess(discoveryResult -> {
                    LOGGER.info("Discovery process completed for id={}: {}", discoveryId, discoveryResult.encode());
                    message.reply(discoveryResult);
                })
                .onFailure(err -> {
                    var result = new JsonObject().put(Constants.STATUS, Constants.FAIL);
                    message.reply(result);
                    LOGGER.error("Discovery failed for id={}: {}", discoveryId, err.getMessage());
                });
    }

    /**
     * Processes a discovery request asynchronously
     * @param payload Request payload with discovery parameters
     * @param discoveryId ID of the discovery operation
     * @return Future with the discovery result containing only status
     */
    private Future<JsonObject> processDiscovery(JsonObject payload, String discoveryId)
    {
        var result = new JsonObject();

        var ip = payload.getString(Constants.IP);

        var port = payload.getInteger(Constants.PORT, 22);

        var credentialIds = payload.getJsonArray(Constants.CREDENTIAL_IDS);

        // Fetch credential profiles
        return fetchCredentialProfiles(credentialIds)
                .compose(profiles -> {

                    if (profiles.isEmpty())
                    {
                        result.put(Constants.STATUS, Constants.FAIL);

                        return Future.succeededFuture(result);
                    }

                    // Format data according to Go plugin expectations
                    var goPluginInput = createGoPluginInput(ip, port, profiles);

                    // Check device availability asynchronously
                    var checkObject = new JsonObject()
                            .put(Constants.IP, ip)
                            .put(Constants.PORT, port);

                    return ProcessBuilderUtil.checkAvailability(vertx, checkObject)
                            .compose(isAvailable -> {

                                if (!isAvailable)
                                {
                                    result.put(Constants.STATUS, Constants.FAIL);

                                    return Future.succeededFuture(result);
                                }

                                // Spawn plugin engine with correctly formatted input
                                var pluginInput = new JsonArray().add(goPluginInput);

                                LOGGER.info("Plugin input: {}", pluginInput.encode());

                                return ProcessBuilderUtil.spawnPluginEngine(vertx, pluginInput)
                                        .compose(resultArray -> {
                                            if (resultArray == null || resultArray.isEmpty())
                                            {
                                                result.put(Constants.STATUS, Constants.FAIL);
                                                return Future.succeededFuture(result);
                                            }

                                            // For discovery, extract the status from the first result object
                                            var firstResult = resultArray.getJsonObject(0);
                                            if (firstResult != null && Constants.SUCCESS.equalsIgnoreCase(firstResult.getString(Constants.STATUS, "")))
                                            {
                                                result.put(Constants.STATUS, Constants.SUCCESS);
                                            }
                                            else
                                            {
                                                result.put(Constants.STATUS, Constants.FAIL);
                                            }

                                            return Future.succeededFuture(result);
                                        });
                            })
                            .compose(res -> {
                                var finalStatus = res.getString(Constants.STATUS).equals(Constants.SUCCESS) ? Constants.UP : Constants.DOWN;

                                var updateFields = new JsonObject().put(Constants.STATUS, finalStatus);

                                var id = Integer.parseInt(discoveryId);

                                // Update database with discovery status
                                return dbHelper.update(Constants.DISCOVERY_TABLE, Constants.FIELD_ID, id, updateFields)
                                        .map(updateResult -> {
                                            LOGGER.info("Discovery status updated successfully for id={}", discoveryId);
                                            return res;
                                        })
                                        .otherwise(err -> {
                                            LOGGER.error("Failed to update discovery status for id={}: {}", discoveryId, err.getMessage());
                                            return res;
                                        });
                            });
                });
    }

    /**
     * Creates input in the format expected by Go plugin
     * @param ip The IP address
     * @param port The port number
     * @param credentialProfiles The credential profiles
     * @return Formatted JsonObject for Go plugin
     */
    private JsonObject createGoPluginInput(String ip, Integer port, JsonArray credentialProfiles)
    {
        var formattedCredentials = new JsonArray();

        // Format each credential according to Go expectations
        for (var i = 0; i < credentialProfiles.size(); i++)
        {
            var credential = credentialProfiles.getJsonObject(i);

            if (credential != null)
            {
                // Extract the attributes field as a string
                var attributesStr = credential.getString(Constants.ATTRIBUTES);

                // Parse the attributes string into a JsonObject
                JsonObject attributes;
                try
                {
                    attributes = new JsonObject(attributesStr);
                }
                catch (Exception e)
                {
                    LOGGER.warn("Skipping credential ID {} due to invalid attributes JSON: {}",

                            credential.getInteger(Constants.FIELD_ID, i), e.getMessage());
                    continue;
                }

                // Create formatted credential object
                var formattedCredential = new JsonObject()
                        .put(Constants.CREDENTIAL_NAME, credential.getString("name", "credential_" + credential.getInteger(Constants.FIELD_ID, i)))
                        .put(Constants.CREDENTIAL_TYPE, credential.getString(Constants.TYPE, Constants.SSH))
                        .put(Constants.ATTRIBUTES, attributes);

                formattedCredentials.add(formattedCredential);
            }
        }

        // Create input object with requestType and contexts array
        var context = new JsonObject()
                .put(Constants.IP, ip)
                .put(Constants.PORT, port)
                .put(Constants.CREDENTIALS, formattedCredentials);

        // Create array of contexts
        var contextsArray = new JsonArray().add(context);

        return new JsonObject()
                .put(Constants.REQUEST_TYPE, Constants.DISCOVERY)
                .put(Constants.CONTEXTS, contextsArray);
    }

    /**
     * Fetches credential profiles for the given credential IDs
     * @param credentialIds Array of credential IDs
     * @return Future with array of credential profiles
     */
    private Future<JsonArray> fetchCredentialProfiles(JsonArray credentialIds)
    {
        var profiles = new JsonArray();

        var future = Future.succeededFuture(profiles);

        for (var i = 0; i < credentialIds.size(); i++)
        {

            var idObj = credentialIds.getValue(i);

            if (!(idObj instanceof Integer credentialId))
            {
                LOGGER.warn("Invalid credential ID format at index {}: {}", i, idObj);

                continue;
            }

            future = future.compose(res ->
                    dbHelper.fetchOne(Constants.CREDENTIAL_TABLE, Constants.FIELD_ID, credentialId)
                            .map(credential -> {

                                if (credential != null)
                                {
                                    // Store original credential ID for later reference

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
        return future;
    }
}