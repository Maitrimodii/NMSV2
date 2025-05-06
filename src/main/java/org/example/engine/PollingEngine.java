package org.example.engine;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.constants.Constants;
import org.example.db.DbQueryHelper;
import org.example.utils.ProcessBuilderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollingVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(PollingVerticle.class);
    private final DbQueryHelper dbHelper;
    private long pollingTimerId = -1;
    private static final int POLLING_INTERVAL_MS = 60000; // 60 seconds

    public PollingVerticle(DbQueryHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    @Override
    public void start() {
        vertx.eventBus().consumer(Constants.POLLING_ADDRESS, message -> {
            JsonObject payload = (JsonObject) message.body();
            String action = payload.getString("action");
            if ("start".equalsIgnoreCase(action) && pollingTimerId == -1) {
                startPolling();
                message.reply(new JsonObject().put("status", "Polling started"));
            } else {
                message.reply(new JsonObject().put("status", "Polling already running or invalid action"));
            }
        });

        logger.info("PollingVerticle started, listening on {}", Constants.POLLING_ADDRESS);
    }

    private void startPolling() {
        pollingTimerId = vertx.setPeriodic(POLLING_INTERVAL_MS, id -> {
            logger.info("Running scheduled polling task студентов

                    dbHelper.fetchAll(Constants.PROVISION_TABLE)
                            .onSuccess(rows -> {
                                if (rows.isEmpty()) {
                                    logger.info("No provisioned devices found for polling");
                                    return;
                                }

                                for (Object rowObj : rows) {
                                    JsonObject row = (JsonObject) rowObj;
                                    Integer provisionId = row.getInteger("id");
                                    String ip = row.getString("ip");
                                    Integer port = row.getInteger("port", 22);
                                    Integer credentialId = row.getInteger("credential_profile_id");

                                    // Create plugin input
                                    JsonObject pluginInput = createPluginInput(ip, port, credentialId);
                                    JsonArray pluginInputArray = new JsonArray().add(pluginInput);

                                    // Call Go plugin
                                    ProcessBuilderUtil.spawnPluginEngine(vertx, pluginInputArray)
                                            .onSuccess(pluginResult -> {
                                                if (pluginResult == null || !"Success".equalsIgnoreCase(pluginResult)) {
                                                    logger.warn("Plugin failed for device ID {}: {}", provisionId, pluginResult);
                                                    return;
                                                }

                                                // Store metrics in polling table
                                                JsonObject metrics = new JsonObject()
                                                        .put("status", pluginResult)
                                                        .put("timestamp", System.currentTimeMillis());
                                                JsonObject pollingEntry = new JsonObject()
                                                        .put("provision_id", provisionId)
                                                        .put("timestamp", System.currentTimeMillis())
                                                        .put("metrics", metrics);

                                                dbHelper.insert("polling", pollingEntry)
                                                        .onSuccess(res -> logger.info("Polling result stored for device ID: {}", provisionId))
                                                        .onFailure(err -> logger.error("Failed to store polling result for device ID {}: {}", provisionId, err.getMessage()));

                                                // Update availability in provision table
                                                double availabilityPercent = row.getDouble("availability_percent", 0.0);
                                                JsonArray pollingResults = row.getJsonArray("polling_results", new JsonArray());
                                                pollingResults.add(metrics);
                                                double newAvailability = calculateAvailability(availabilityPercent, true);

                                                JsonObject update = new JsonObject()
                                                        .put("availability_percent", newAvailability)
                                                        .put("polling_results", pollingResults);

                                                dbHelper.update(Constants.PROVISION_TABLE, "id", provisionId, update)
                                                        .onFailure(err -> logger.error("Failed to update device {}: {}", provisionId, err.getMessage()));
                                            })
                                            .onFailure(err -> logger.error("Plugin execution failed for device ID {}: {}", provisionId, err.getMessage()));
                                }
                            })
                            .onFailure(err -> logger.error("Failed to fetch provisioned devices: {}", err.getMessage()));
        });

        logger.info("Polling started with interval {}ms", POLLING_INTERVAL_MS);
    }

    private JsonObject createPluginInput(String ip, Integer port, Integer credentialId) {
        JsonObject credential = new JsonObject()
                .put("credential.name", "credential_" + credentialId)
                .put("credential.type", "ssh")
                .put("attributes", new JsonObject());

        JsonArray credentials = new JsonArray().add(credential);
        JsonObject context = new JsonObject()
                .put("ip", ip)
                .put("port", port)
                .put("credentials", credentials);

        return new JsonObject()
                .put("requestType", "Metrics")
                .put("contexts", new JsonArray().add(context));
    }

    private double calculateAvailability(double currentAvailability, boolean isReachable) {
        double weight = 0.1;
        return currentAvailability * (1 - weight) + (isReachable ? 100.0 : 0.0) * weight;
    }
}