package org.example.engine;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.constants.Constants;
import org.example.db.DbQueryHelper;
import org.example.utils.CredentialProfiles;
import org.example.utils.ProcessBuilderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscoveryEngine extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryEngine.class);

    private final DbQueryHelper dbHelper;

    private final CredentialProfiles credentialProfiles;

    public DiscoveryEngine(DbQueryHelper dbHelper)
    {
        this.dbHelper = dbHelper;

        this.credentialProfiles = new CredentialProfiles(dbHelper);
    }

    @Override
    public void start(Promise<Void> promise)
    {
        try
        {
            vertx.eventBus().consumer(Constants.DISCOVERY_ADDRESS, this::handleDiscoveryRequest);

            LOGGER.info("DiscoveryEngine started, listening on {}", Constants.DISCOVERY_ADDRESS);

            promise.complete();
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to start DiscoveryEngine: {}", exception.getMessage(), exception);

            promise.fail("Failed to start DiscoveryEngine: " + exception.getMessage());
        }

    }

    /**
     * Handles incoming discovery requests from the event bus
     * @param message Message containing the discovery request payload
     */
    private void handleDiscoveryRequest(Message<JsonObject> message)
    {
        var payload = message.body();

        var discoveryId = payload.getString(Constants.DISCOVERY_ID);

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

        try
        {
            return credentialProfiles.fetchCredentialProfiles(credentialIds)
                    .compose(profiles -> {

                        if (profiles.isEmpty())
                        {
                            result.put(Constants.STATUS, Constants.FAIL);

                            return Future.succeededFuture(result);
                        }

                        // Format data according to Go plugin expectations
                        var goPluginInput = createGoPluginInput(ip, port, profiles);

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
        catch (Exception exception)
        {
            LOGGER.error("Unexpected error during discovery process for id={}: {}", discoveryId, exception.getMessage(), exception);

            result.put(Constants.STATUS, Constants.FAIL);

            return Future.succeededFuture(result);
        }
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
        var formattedCredentials = this.credentialProfiles.formatCredentials(credentialProfiles);

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

}