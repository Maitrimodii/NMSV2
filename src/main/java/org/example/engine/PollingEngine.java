package org.example.engine;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.constants.Constants;
import org.example.db.DbQueryHelper;
import org.example.utils.CredentialProfiles;
import org.example.utils.ProcessBuilderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PollingEngine extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PollingEngine.class);

    private static final long POLLING_INTERVAL_MS = 10000;

    private final Map<Integer, Long> lastPollTimeMap = new HashMap<>();

    private final DbQueryHelper dbHelper;

    private final CredentialProfiles credentialProfiles;

    public PollingEngine(DbQueryHelper dbHelper)
    {
        this.dbHelper = dbHelper;

        this.credentialProfiles = new CredentialProfiles(dbHelper);
    }

    @Override
    public void start(Promise<Void> promise)
    {
        try {
            vertx.setPeriodic(POLLING_INTERVAL_MS, id -> pollDevices());

            LOGGER.info("PollingEngine started with interval {}ms", POLLING_INTERVAL_MS);

            promise.complete();
        }
        catch (Exception exception)
        {

            LOGGER.error("Failed to start PollingEngine: {}", exception.getMessage(), exception);

            promise.fail(exception);
        }
    }

    /**
     * Polls devices from the database and processes them if they're due for polling
     */
    private void pollDevices()
    {
        LOGGER.debug("Starting device polling cycle");

        try {
            dbHelper.fetchAll(Constants.PROVISION_TABLE)
                    .onSuccess(devices -> {
                        if (devices == null || devices.isEmpty()) {
                            LOGGER.debug("No provisioned devices found");
                            return;
                        }

                        // Filter devices that are due for polling
                        var devicesToProcess = filterDevicesForPolling(devices);

                        if (devicesToProcess.isEmpty()) {
                            LOGGER.debug("No devices due for polling in this cycle");
                            return;
                        }

                        LOGGER.info("Processing {} devices that are due for polling", devicesToProcess.size());

                        processDevices(devicesToProcess)
                                .onSuccess(v -> {
                                    // Update last poll time for processed devices
                                    var currentTime = System.currentTimeMillis();

                                    for (var device : devicesToProcess) {
                                        var provisionId = device.getInteger(Constants.FIELD_ID);

                                        if (provisionId != null) {
                                            lastPollTimeMap.put(provisionId, currentTime);
                                        }

                                    }
                                    LOGGER.debug("Updated last poll timestamps for {} devices", devicesToProcess.size());
                                })
                                .onFailure(err -> LOGGER.error("Failed to process devices: {}", err.getMessage()));
                    })
                    .onFailure(err -> LOGGER.error("Failed to fetch provisions: {}", err.getMessage()));
        }
        catch (Exception exception)
        {
                LOGGER.error("Error in pollDevices: {}", exception.getMessage(), exception);
            }
    }

    /**
     * Filter devices that are due for polling based on their last poll time
     *
     * @param allDevices List of all provisioned devices
     * @return List of devices that should be polled in this cycle
     */
    private List<JsonObject> filterDevicesForPolling(List<JsonObject> allDevices)
    {
        try
        {
            var currentTime = System.currentTimeMillis();


            return allDevices.stream()
                    .filter(device -> {
                        var provisionId = device.getInteger(Constants.FIELD_ID);

                        if (provisionId == null)
                        {
                            return false;
                        }


                        // Get last poll time, default to 0 if never polled
                        var lastPollTime = lastPollTimeMap.getOrDefault(provisionId, 0L);

                        // Check if enough time has passed since last poll
                        return (currentTime - lastPollTime >= POLLING_INTERVAL_MS);
                    })
                    .toList();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in filterDevicesForPolling: {}", exception.getMessage(), exception);

            return List.of();
        }

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

        try
        {
            var contexts = new JsonArray();

            // Collect contexts for all devices
            var collectFuture = Future.succeededFuture(contexts);

            for (var device : devices)
            {
                collectFuture = collectFuture.compose(res ->
                        collectDeviceMetrics(device).map(context -> {
                            if (context != null)
                            {
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
        catch (Exception e)
        {
            LOGGER.error("Error in processing Devices: {}", e.getMessage(), e);

            return Future.failedFuture("error in processing devices");
        }

    }

    /**
     * Process plugin results and store them in the database
     *
     * @param resultArray The array of results returned from the plugin
     * @return Future that completes when all results are stored
     */
    private Future<Void> processPluginResults(JsonArray resultArray)
    {
        try {
            if (resultArray == null || resultArray.isEmpty()) {
                return Future.succeededFuture();
            }

            LOGGER.info("Processing plugin results for {} entries", resultArray.size());

            // Process each result and store its metrics
            for (var i = 0; i < resultArray.size(); i++) {
                var result = resultArray.getJsonObject(i);

                if (result == null) {
                    continue;
                }

                var status = result.getString(Constants.STATUS, "");

                if (!Constants.SUCCESS.equalsIgnoreCase(status)) {
                    LOGGER.warn("Result has non-success status: {}", status);

                    continue;
                }

                var provisionId = result.getInteger(Constants.PROVISION_ID);

                if (provisionId == null) {
                    LOGGER.warn("Result missing provision ID, skipping metrics storage");

                    continue;
                }

                // Get metrics from the result
                var metrics = result.getJsonObject(Constants.RESULT, new JsonObject());

                if (metrics.isEmpty()) {
                    LOGGER.warn("No metrics data found for provision ID: {}", provisionId);

                    continue;
                }

                final var finalProvisionId = provisionId;

                final var finalMetrics = metrics;

                return Future.succeededFuture().compose(v ->
                        storeMetricsInDatabase(new JsonObject()
                                .put(Constants.STATUS, status)
                                .put(Constants.RESULT, finalMetrics)
                                .put(Constants.PROVISION_ID, finalProvisionId))
                );
            }

            return Future.succeededFuture();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in processPluginResults: {}", exception.getMessage(), exception);

            return Future.succeededFuture();
        }
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

                    return credentialProfiles.fetchCredentialProfiles(credentialIds)
                            .compose(profiles -> {
                                if (profiles.isEmpty())
                                {
                                    LOGGER.warn("No valid credentials for device IP: {}", ip);

                                    return Future.succeededFuture(null);
                                }

                                var context = new JsonObject()
                                        .put(Constants.IP, ip)
                                        .put(Constants.PORT, port)
                                        .put(Constants.CREDENTIALS, credentialProfiles.formatCredentials(profiles))
                                        .put(Constants.PROVISION_ID, provisionId);

                                return Future.succeededFuture(context);
                            });
                });
    }

    /**
     * Creates the input for the Go plugin
     * @param contexts The array of contexts for the devices
     * @return JsonObject representing the input for the Go plugin
     */
    private JsonObject createGoPluginInput(JsonArray contexts)
    {
        try
        {
            return new JsonObject()
                    .put(Constants.REQUEST_TYPE, Constants.COLLECT)
                    .put(Constants.CONTEXTS, contexts);
        }
        catch (Exception exception)
        {
            LOGGER.error("Error creating Go plugin input: {}", exception.getMessage(), exception);

            return new JsonObject();
        }
    }

    /**
     * Stores metrics in the database
     *
     * @param result The result JsonObject with status, metrics data and provision ID
     * @return Future that completes when the metrics are stored
     */
    private Future<Void> storeMetricsInDatabase(JsonObject result)
    {
        try {
            if (result == null || !result.containsKey(Constants.STATUS) ||
                    !result.getString(Constants.STATUS).equalsIgnoreCase(Constants.SUCCESS)) {
                return Future.failedFuture("result are not present");
            }

            // Extract provision ID from the result
            var provisionId = result.getInteger(Constants.PROVISION_ID, -1);

            if (provisionId == -1) {
                LOGGER.warn("Missing provision ID in result, cannot store metrics");

                return Future.failedFuture("Missing provision ID in result, cannot store metrics");
            }

            // Extract the data (metrics) from the result
            var data = result.getJsonObject(Constants.RESULT, new JsonObject());

            if (data.isEmpty()) {
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
        catch (Exception exception)
        {
            LOGGER.error("Error in storeMetricsInDatabase: {}", exception.getMessage(), exception);

            return Future.failedFuture("Error in storeMetricsInDatabase");
        }
    }
}