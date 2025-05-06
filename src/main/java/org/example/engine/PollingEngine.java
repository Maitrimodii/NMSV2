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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class PollingEngine extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PollingEngine.class);

    private static final long POLLING_INTERVAL_MS = 10_000;

    private final DbQueryHelper dbHelper;

    public PollingEngine(DbQueryHelper dbHelper)
    {
        this.dbHelper = dbHelper;
    }

    @Override
    public void start(Promise<Void> promise)
    {
        vertx.setPeriodic(POLLING_INTERVAL_MS, id -> pollDevices());

        LOGGER.info("PollingEngine started with interval {}ms", POLLING_INTERVAL_MS);

        promise.complete();
    }

    /**
     * Polls devices from the database and processes them
     */
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

    /**
     * Processes the list of provisioned devices
     *
     * @param devices List of provisioned devices
     * @return Future that completes when all devices are processed
     */
    private Future<Void> processDevices(List<JsonObject> devices)
    {
        LOGGER.info("Processing {} provisioned devices", devices.size());
        var contexts = new JsonArray();

        // Collect contexts for all devices
        var collectFuture = Future.succeededFuture(contexts);

        for (var device : devices)
        {
            collectFuture = collectFuture.compose(res ->
                    collectDeviceMetrics(device).map(context -> {
                        if (context != null) {
                            res.add(context);
                        }
                        return res;
                    })
            );
        }

        return collectFuture.compose(contextsArray -> {
            if (contextsArray.isEmpty())
            {
                LOGGER.debug("No valid device contexts to process");
                return Future.succeededFuture();
            }

            var goPluginInput = createGoPluginInput(contextsArray);

            var pluginInput = new JsonArray().add(goPluginInput);

            return ProcessBuilderUtil.spawnPluginEngine(vertx, pluginInput)
                    .compose(resultArray -> {
                        if (resultArray == null || resultArray.isEmpty())
                        {
                            LOGGER.warn("Failed to collect metrics for devices");
                            return Future.succeededFuture();
                        }

                        LOGGER.info("Successfully collected metrics for devices");

                        // Process and store the results from the Go plugin
                        return processPluginResults(resultArray);
                    });
        });
    }

    /**
     * Process plugin results and store them in the database
     *
     * @param resultArray The array of results returned from the plugin
     * @return Future that completes when all results are stored
     */
    private Future<Void> processPluginResults(JsonArray resultArray)
    {
        if (resultArray == null || resultArray.isEmpty())
        {
            return Future.succeededFuture();
        }

        LOGGER.info("Processing plugin results for {} entries", resultArray.size());

        Future<Void> resultFuture = Future.succeededFuture();

        // Process each result and store its metrics
        for (var i = 0; i < resultArray.size(); i++)
        {
            var result = resultArray.getJsonObject(i);
            if (result == null) {
                continue;
            }

            var status = result.getString(Constants.STATUS, "");
            if (!Constants.SUCCESS.equalsIgnoreCase(status))
            {
                LOGGER.warn("Result has non-success status: {}", status);
                continue;
            }

            var provisionId = result.getInteger(Constants.PROVISION_ID);
            if (provisionId == null)
            {
                LOGGER.warn("Result missing provision ID, skipping metrics storage");
                continue;
            }

            // Get metrics from the result
            var metrics = result.getJsonObject(Constants.RESULT, new JsonObject());

            if (metrics.isEmpty())
            {
                LOGGER.warn("No metrics data found for provision ID: {}", provisionId);
                continue;
            }

            // Chain this operation to our future chain
            final Integer finalProvisionId = provisionId;
            final JsonObject finalMetrics = metrics;

            resultFuture = resultFuture.compose(v ->
                    storeMetricsInDatabase(new JsonObject()
                            .put(Constants.STATUS, status)
                            .put(Constants.RESULT, finalMetrics)
                            .put(Constants.PROVISION_ID, finalProvisionId))
            );
        }

        return resultFuture;
    }

    /**
     * Collects device metrics by checking availability and fetching credentials
     *
     * @param device The device JSON object containing IP, port, and credential IDs
     * @return Future that completes with the context for the device
     */
    private Future<JsonObject> collectDeviceMetrics(JsonObject device)
    {
        var ip = device.getString(Constants.IP);

        var port = device.getInteger(Constants.PORT, 22);

        var credentialIdsStr = device.getString(Constants.CREDENTIAL_IDS);

        var provisionId = device.getInteger(Constants.FIELD_ID);

        JsonArray credentialIds;
        try
        {
            credentialIds = new JsonArray(credentialIdsStr);
        }
        catch (Exception exception)
        {
            LOGGER.warn("Invalid credential_ids format for device IP: {}: {}", ip, credentialIdsStr);

            return Future.succeededFuture(null);
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

                        return Future.succeededFuture(null);
                    }

                    LOGGER.debug("Device available, preparing context for IP: {}", ip);

                    return fetchCredentialProfiles(credentialIds)
                            .compose(profiles -> {
                                if (profiles.isEmpty())
                                {
                                    LOGGER.warn("No valid credentials for device IP: {}", ip);

                                    return Future.succeededFuture(null);
                                }

                                var context = new JsonObject()
                                        .put(Constants.IP, ip)
                                        .put(Constants.PORT, port)
                                        .put(Constants.CREDENTIALS, formatCredentials(profiles))
                                        .put(Constants.PROVISION_ID, provisionId);

                                return Future.succeededFuture(context);
                            });
                });
    }


    /**
     * Fetches credential profiles from the database based on the provided credential IDs
     *
     * @param credentialIds The array of credential IDs
     * @return Future that completes with the array of credential profiles
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
                                if (credential != null) {
                                    credential.put(Constants.CREDENTIAL_ID, credentialId);
                                    res.add(credential);
                                } else {
                                    LOGGER.warn("Credential not found with ID: {}", credentialId);
                                }
                                return res;
                            })
            );
        }
        return future;
    }

    /**
     * Formats the credentials into a JSON array
     * @param credentialProfiles
     * @return
     */
    private JsonArray formatCredentials(JsonArray credentialProfiles)
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

    /**
     * Creates the input for the Go plugin
     * @param contexts
     * @return
     */
    private JsonObject createGoPluginInput(JsonArray contexts)
    {
        return new JsonObject()
                .put(Constants.REQUEST_TYPE, Constants.COLLECT)
                .put(Constants.CONTEXTS, contexts);
    }

    /**
     * Stores metrics in the database
     *
     * @param result The result JsonObject with status, metrics data and provision ID
     * @return Future that completes when the metrics are stored
     */
    private Future<Void> storeMetricsInDatabase(JsonObject result)
    {
        if (result == null || !result.containsKey(Constants.STATUS) ||
                !result.getString(Constants.STATUS).equalsIgnoreCase(Constants.SUCCESS))
        {
            return Future.succeededFuture();
        }

        // Extract provision ID from the result
        var provisionId = result.getInteger(Constants.PROVISION_ID, -1);

        if (provisionId == -1)
        {
            LOGGER.warn("Missing provision ID in result, cannot store metrics");
            return Future.succeededFuture();
        }

        // Extract the data (metrics) from the result
        var data = result.getJsonObject(Constants.RESULT, new JsonObject());

        if (data.isEmpty())
        {
            LOGGER.warn("No metrics data found for provision ID: {}", provisionId);

            return Future.succeededFuture();
        }

        var timestampMillis = System.currentTimeMillis();

        var timestamp = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestampMillis), ZoneOffset.UTC);

        // Create insert record with provision ID, data and timestamp
        var record = new JsonObject()
                .put(Constants.PROVISION_ID, provisionId)
                .put(Constants.DATA, data)
                .put(Constants.TIMESTAMP, timestamp);

        LOGGER.info("Storing metrics in database for provision ID: {}", provisionId);

        return dbHelper.insert(Constants.POLLING_TABLE, record)
                .onSuccess(id -> LOGGER.info("Metrics stored"))
                .onFailure(err -> LOGGER.error("Failed to store metrics: {}", err.getMessage()))
                .mapEmpty();
    }
}