package org.example.utils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.constants.Constants;
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

    private static final int PING_TIMEOUT_MS = 2000;     // 2 seconds

    private static final int SOCKET_TIMEOUT_MS = 2000;   // 2 seconds

    private static final int PROCESS_TIMEOUT_SEC = 30;   // 30 seconds

    private static final String GO_BINARY_PATH = "go/nms-plugin";

    /**
     * Asynchronously checks device availability
     *
     * @param vertx Vertx instance to execute blocking operations
     * @param profile JSON profile containing device information
     * @return Future that completes with the availability result
     */
    public static Future<Boolean> checkAvailability(Vertx vertx, JsonObject profile)
    {
        return vertx.executeBlocking(() -> {
            try
            {
                var ip = profile.getString(Constants.IP);

                var port = profile.getInteger(Constants.PORT, 22);

                if (ip == null || ip.isEmpty())
                {

                    LOGGER.error("Invalid IP address: {}", ip);

                    return false;
                }

                // Check ping first
                var isPingSuccessful = pingDevice(ip);

                if (!isPingSuccessful)
                {
                    LOGGER.warn("Ping failed for IP: {}", ip);
                    return false;
                }

                // Check port if needed
                if (port > 0 && !checkPort(ip, port))
                {
                    LOGGER.warn("Port {} is not open for IP: {}", port, ip);

                    return false;
                }

                LOGGER.info("Device is available at IP: {}, Port: {}", ip, port);

                return true;

            }
            catch (Exception exception)
            {
                LOGGER.error("Error checking availability: {}", exception.getMessage(), exception);

                return false;
            }
        }, false);
    }


    /**
     * Pings the device to check its availability
     * @param ip Device IP address
     * @return true if the device is reachable, false otherwise
     */
    private static boolean pingDevice(String ip)
    {
        try {
            var command = new String[]{"ping", "-c", "1", "-W", String.valueOf(PING_TIMEOUT_MS / 1000), ip};

            var pb = new ProcessBuilder(command);

            pb.redirectErrorStream(true);

            var process = pb.start();

            var completed = process.waitFor(PING_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS);

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
        catch (Exception exception)
        {

            LOGGER.error("Error pinging IP: {}: {}", ip, exception.getMessage(), exception);

            return false;
        }
    }

    /**
     * Checks if the specified port is open on the device
     * @param ip Device IP address
     * @param port Port number to check
     * @return true if the port is open, false otherwise
     */
    private static boolean checkPort(String ip, int port)
    {
        try (var socket = new Socket())
        {
            socket.connect(new InetSocketAddress(ip, port), SOCKET_TIMEOUT_MS);

            return true;
        }
        catch (Exception exception)
        {
            LOGGER.error("Error checking port {} for IP: {}: {}", port, ip, exception.getMessage());

            return false;
        }
    }

    /**
     * Asynchronously spawns plugin engine and streams results
     *
     * @param vertx Vertx instance to execute blocking operations
     * @param pluginInput JSON array with plugin input
     * @return Future with plugin execution results as a JsonArray or null on failure
     */
    public static Future<JsonArray> spawnPluginEngine(Vertx vertx, JsonArray pluginInput)
    {
        if (pluginInput == null || pluginInput.isEmpty())
        {
            LOGGER.error("Plugin input is null or empty");

            return Future.succeededFuture(null);
        }

        return vertx.executeBlocking(() -> {
            Process process = null;

            try
            {
                if (!new File(GO_BINARY_PATH).exists())
                {
                    LOGGER.error("Go binary not found at: {}", GO_BINARY_PATH);

                    return null;
                }

                var pb = new ProcessBuilder(GO_BINARY_PATH);

                pb.redirectErrorStream(false);

                process = pb.start();

                var inputString = pluginInput.encode();

                LOGGER.info("Sending input to Go plugin: {}", inputString);

                try (var writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)))
                {

                    writer.write(inputString);

                    writer.newLine();

                    writer.flush();
                }

                var allResults = new JsonArray();

                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
                {
                    String line;

                    while ((line = reader.readLine()) != null)
                    {
                        if (!line.trim().isEmpty())
                        {
                            try
                            {
                                var resultObj = new JsonObject(line);

                                allResults.add(resultObj);

                                LOGGER.info("Received result from Go plugin: {}", resultObj.encode());
                            }
                            catch (Exception exception)
                            {
                                LOGGER.error("Failed to parse JSON line: {}, raw output: {}", exception.getMessage(), line);
                            }
                        }
                    }
                }

                var completed = process.waitFor(PROCESS_TIMEOUT_SEC, TimeUnit.SECONDS);

                if (!completed)
                {
                    LOGGER.error("Go plugin process timed out");

                    process.destroyForcibly();

                    return allResults.isEmpty() ? null : allResults;
                }

                var exitCode = process.exitValue();

                if (exitCode != 0)
                {
                    LOGGER.error("Go plugin process failed with exit code: {}", exitCode);

                    return allResults.isEmpty() ? null : allResults;
                }

                LOGGER.info("Go plugin completed successfully with {} results", allResults.size());

                return allResults;
            }
            catch (IOException exception)
            {

                LOGGER.error("I/O error spawning Go plugin: {}", exception.getMessage(), exception);

                return null;
            }
            catch (InterruptedException exception)
            {
                LOGGER.error("Process interrupted while spawning Go plugin: {}", exception.getMessage(), exception);

                Thread.currentThread().interrupt();

                return null;
            }
            finally
            {
                if (process != null && process.isAlive())
                {
                    process.destroyForcibly();
                }
            }
        }, false);
    }
}