package org.example.engine;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.constants.Constants;
import org.example.db.DbQueryHelper;
import org.example.utils.ProcessBuilderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PollingEngine extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PollingEngine.class);

    private static final long POLLING_INTERVAL_MS = 10_000; // 10 seconds

    private final DbQueryHelper dbHelper;

    public PollingEngine(DbQueryHelper dbHelper)
    {
        this.dbHelper = dbHelper;
    }

    public void start(Promise<Void> promise)
    {
        vertx.setPeriodic(POLLING_INTERVAL_MS, id -> pollDevices());

        LOGGER.info("PollingEngine started with interval {}ms", POLLING_INTERVAL_MS);

        promise.complete();
    }

    private void pollDevices()
    {
        LOGGER.debug("Starting device polling cycle");

        dbHelper.fetchAll(Constants.PROVISION_TABLE)
                .onSuccess(devices -> {
                    if (devices == null || devices.isEmpty())
                    {

                        LOGGER.debug("No provisioned devices found");

                        return;
                    }
                    processDevices(devices)
                            .onFailure(err -> LOGGER.error("Failed to process devices: {}", err.getMessage()));
                })
                .onFailure(err -> LOGGER.error("Failed to fetch provisions: {}", err.getMessage()));
    }

    private Future<Void> processDevices(List<JsonObject> devices)
    {
        LOGGER.info("Processing {} provisioned devices", devices.size());

        var future = Future.<Void>succeededFuture();

        for (var device : devices)
        {
            future = future.compose(v -> collectDeviceMetrics(device));
        }
        return future;
    }

    private Future<Void> collectDeviceMetrics(JsonObject device)
    {
        var ip = device.getString(Constants.IP);

        var port = device.getInteger(Constants.PORT, 22);

        var credentialIdsStr = device.getString(Constants.CREDENTIAL_IDS);

        JsonArray credentialIds;
        try
        {
            credentialIds = new JsonArray(credentialIdsStr);
        }
        catch (Exception exception)
        {
            LOGGER.warn("Invalid credential_ids format for device IP: {}: {}", ip, credentialIdsStr);

            return Future.succeededFuture();
        }

        LOGGER.debug("Checking availability for device IP: {}, Port: {}", ip, port);

        var profile = new JsonObject()
                .put(Constants.IP, ip)
                .put(Constants.PORT, port);

        return ProcessBuilderUtil.checkAvailability(vertx, profile)
                .compose(isAvailable -> {
                    if (!isAvailable)
                    {
                        LOGGER.warn("Device not available at IP: {}, Port: {}", ip, port);

                        return Future.succeededFuture();
                    }

                    LOGGER.debug("Device available, collecting metrics for IP: {}", ip);
                    return fetchCredentialProfiles(credentialIds)
                            .compose(profiles -> {
                                if (profiles.isEmpty())
                                {

                                    LOGGER.warn("No valid credentials for device IP: {}", ip);

                                    return Future.succeededFuture();
                                }

                                var goPluginInput = createGoPluginInput(ip, port, profiles);

                                var pluginInput = new JsonArray().add(goPluginInput);

                                return ProcessBuilderUtil.spawnPluginEngine(vertx, pluginInput)
                                        .compose(pluginResult -> {
                                            if (pluginResult == null || !pluginResult.equalsIgnoreCase("Success")) {
                                                LOGGER.warn("Failed to collect metrics for device IP: {}", ip);
                                                return Future.succeededFuture();
                                            }

                                            LOGGER.info("Successfully collected metrics for device IP: {}", ip);
                                            return Future.succeededFuture();
                                        });
                            });
                });
    }

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

    private JsonObject createGoPluginInput(String ip, Integer port, JsonArray credentialProfiles)
    {
        var formattedCredentials = new JsonArray();

        for (var i = 0; i < credentialProfiles.size(); i++)
        {
            var credential = credentialProfiles.getJsonObject(i);

            if (credential != null)
            {
                var attributesStr = credential.getString("attributes");

                JsonObject attributes;

                try {
                    attributes = new JsonObject(attributesStr);
                }
                catch (Exception exception)
                {
                    LOGGER.warn("Skipping credential ID {} due to invalid attributes JSON: {}",
                            credential.getInteger("id", i), exception.getMessage());
                    continue;
                }

                var formattedCredential = new JsonObject()
                        .put("credential.name", credential.getString("name", "credential_" + credential.getInteger("id", i)))
                        .put("credential.type", credential.getString("type", "ssh"))
                        .put("attributes", attributes);

                formattedCredentials.add(formattedCredential);
            }
        }

        var context = new JsonObject()
                .put("ip", ip)
                .put("port", port)
                .put("credentials", formattedCredentials);

        return new JsonObject()
                .put("requestType", "Collect")
                .put("contexts", new JsonArray().add(context));
    }
}
