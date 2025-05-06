package org.example.utils;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class ProcessBuilderUtil
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessBuilderUtil.class);

    private static final int PING_TIMEOUT_MS     = 2000; // 2 seconds

    private static final int SOCKET_TIMEOUT_MS   = 2000; // 2 seconds

    private static final int PROCESS_TIMEOUT_SEC = 30;   // 30 seconds

    private static final String GO_BINARY_PATH   = "go/nms-plugin";

    /**
     * Asynchronously checks device availability
     *
     * @param vertx Vertx instance to execute blocking operations
     * @param discoveryProfile JSON profile containing device information
     * @return Future that completes with the availability result
     */
    public static Future<Boolean> checkAvailability(Vertx vertx, JsonObject discoveryProfile)
    {
        return vertx.executeBlocking(promise ->
        {
            try
            {
                var ip = discoveryProfile.getString("ip");

                var port = discoveryProfile.getInteger("port", 22);

                if (ip == null || ip.isEmpty())
                {
                    LOGGER.error("Invalid IP address: {}", ip);

                    promise.complete(false);

                    return;
                }

                var isPingSuccessful = pingDevice(ip);

                if (!isPingSuccessful)
                {
                    LOGGER.warn("Ping failed for IP: {}", ip);

                    promise.complete(false);

                    return;
                }

                if (port > 0 && !checkPort(ip, port))
                {
                    LOGGER.warn("Port {} is not open for IP: {}", port, ip);

                    promise.complete(false);

                    return;
                }

                LOGGER.info("Device is available at IP: {}, Port: {}", ip, port);
                promise.complete(true);
            }
            catch (Exception e)
            {
                LOGGER.error("Error checking availability: {}", e.getMessage(), e);

                promise.complete(false);
            }
        });
    }

    private static boolean pingDevice(String ip)
    {
        try
        {
            var command = new String[]{"ping", "-c", "1", "-W", String.valueOf(PING_TIMEOUT_MS / 1000), ip};

            var pb = new ProcessBuilder(command);

            pb.redirectErrorStream(true);

            var process = pb.start();

            var completed = process.waitFor(PING_TIMEOUT_MS*2, TimeUnit.MILLISECONDS);

            if (!completed)
            {
                LOGGER.error("Ping process timed out for IP: {}", ip);

                process.destroyForcibly();

                return false;
            }

            var exitCode = process.exitValue();

            if (exitCode != 0)
            {
                LOGGER.error("Ping process failed for IP: {} with exit code: {}", ip, exitCode);
                return false;
            }

            return true;
        }
        catch (Exception e)
        {
            LOGGER.error("Error pinging IP: {}: {}", ip, e.getMessage(), e);

            return false;
        }
    }

    private static boolean checkPort(String ip, int port)
    {
        try (var socket = new Socket()) {

            socket.connect(new InetSocketAddress(ip, port), SOCKET_TIMEOUT_MS);

            return true;
        }
        catch (Exception e)
        {
            LOGGER.error("Error checking port {} for IP: {}: {}", port, ip, e.getMessage());
            return false;
        }
    }

    /**
     * Asynchronously spawns plugin engine
     *
     * @param vertx Vertx instance to execute blocking operations
     * @param pluginInput JSON array with plugin input
     * @return Future with plugin execution result
     */
    public static Future<String> spawnPluginEngine(Vertx vertx, JsonArray pluginInput)
    {
        Promise<String> promise = Promise.promise();

        vertx.executeBlocking(blockingPromise ->
        {

            if (pluginInput == null || pluginInput.isEmpty()) {

                LOGGER.error("Plugin input is null or empty");

                blockingPromise.complete(null);

                return;
            }

            Process process = null;
            try {
                if (!new File(GO_BINARY_PATH).exists()) {
                    LOGGER.error("Go binary not found at: {}", GO_BINARY_PATH);

                    blockingPromise.complete(null);

                    return;
                }

                var pb = new ProcessBuilder(GO_BINARY_PATH);

                pb.redirectErrorStream(false);

                process = pb.start();

                var inputString = pluginInput.encode();

                LOGGER.info("Sending input to Go plugin: {}", inputString);

                try (var writer = new BufferedWriter(
                        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {

                    writer.write(inputString);

                    writer.newLine();

                    writer.flush();
                }

                var output = new StringBuilder();

                try (var reader = new BufferedReader(

                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

                    String line;

                    while ((line = reader.readLine()) != null) {

                        output.append(line);

                    }
                }

                var completed = process.waitFor(PROCESS_TIMEOUT_SEC, TimeUnit.SECONDS);

                if (!completed) {
                    LOGGER.error("Go plugin process timed out");

                    process.destroyForcibly();

                    blockingPromise.complete(null);

                    return;
                }

                var exitCode = process.exitValue();

                if (exitCode != 0)
                {
                    LOGGER.error("Go plugin process failed with exit code: {}", exitCode);

                    blockingPromise.complete(null);

                    return;
                }

                var outputStr = output.toString().trim();

                try
                {
                    var resultArray = new JsonArray(outputStr);

                    LOGGER.info("Go plugin output array: {}", resultArray.encode());

                    if (resultArray.isEmpty()) {
                        LOGGER.error("Go plugin returned empty JSON array");

                        blockingPromise.complete(null);

                        return;
                    }

                    var firstResult = resultArray.getJsonObject(0);

                    if (firstResult == null) {
                        LOGGER.error("Go plugin returned null JSON object");

                        blockingPromise.complete(null);

                        return;
                    }

                    var status = firstResult.getString("status", "");

                    if ("Success".equalsIgnoreCase(status)) {
                        LOGGER.warn("Go plugin operation succeed: {}", firstResult.encode());
                    }

                    blockingPromise.complete(status);
                } catch (Exception exception) {
                    try {
                        var result = new JsonObject(outputStr);

                        LOGGER.info("Go plugin output object: {}", result.encode());

                        blockingPromise.complete("fail");
                    } catch (Exception exception2) {

                        LOGGER.error("Failed to parse JSON output: {}, raw output: {}", exception.getMessage(), outputStr);

                        blockingPromise.complete(null);
                    }
                }
            }
            catch (IOException exception)
            {

                LOGGER.error("I/O error spawning Go plugin: {}", exception.getMessage(), exception);

                blockingPromise.complete(null);
            }
            catch (InterruptedException exception)
            {
                LOGGER.error("Process interrupted while spawning Go plugin: {}", exception.getMessage(), exception);

                Thread.currentThread().interrupt();

                blockingPromise.complete(null);
            }
            finally
            {
                if (process != null && process.isAlive())
                {
                    process.destroyForcibly();
                }
            }
        }).onComplete(ar -> {
            if (ar.succeeded())
            {
                promise.complete((String) ar.result());
            }
            else
            {
                LOGGER.error("Error executing Go plugin: {}", ar.cause().getMessage(), ar.cause());

                promise.complete(null);
            }
        });

        return promise.future();
    }
}