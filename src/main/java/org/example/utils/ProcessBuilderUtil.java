package org.example.utils;

import io.vertx.core.Future;
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

    private static final int PING_TIMEOUT_MS       = 2000; // 2 seconds
    private static final int SOCKET_TIMEOUT_MS     = 2000; // 2 seconds
    private static final int PROCESS_TIMEOUT_SEC   = 30;   // 30 seconds

    private static final String GO_BINARY_PATH     = "/go/nms-plugin";

    /**
     * Non-blocking version that checks device availability.
     * @param discoveryProfile JSON profile containing device information
     * @return Future that completes with the availability result
     */

    public static boolean checkAvailability(JsonObject discoveryProfile)
    {

        var ip   = discoveryProfile.getString("ip");

        var port = discoveryProfile.getInteger("port", 22);

        if (ip == null || ip.isEmpty()) {
            LOGGER.error("Invalid IP address: {}", ip);
            return false;
        }

        var isPingSuccessful = pingDevice(ip);

        if (!isPingSuccessful)
        {
            LOGGER.warn("Ping failed for IP: {}", ip);
            return false;
        }

        if (port > 0 && !checkPort(ip, port))
        {
            LOGGER.warn("Port {} is not open for IP: {}", port, ip);
            return false;
        }

        LOGGER.info("Device is available at IP: {}, Port: {}", ip, port);
        return true;
    }

    private static boolean pingDevice(String ip)
    {
        try
        {
            var command = new String[]{"ping", "-c", "1", "-W", String.valueOf(PING_TIMEOUT_MS / 1000), ip};
            var pb      = new ProcessBuilder(command);

            pb.redirectErrorStream(true);
            var process   = pb.start();
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
        catch (Exception e)
        {
            LOGGER.error("Error pinging IP: {}: {}", ip, e.getMessage(), e);
            return false;
        }
    }

    private static boolean checkPort(String ip, int port)
    {

        try (var socket = new Socket())
        {
            socket.connect(new InetSocketAddress(ip, port), SOCKET_TIMEOUT_MS);
            return true;
        }
        catch (Exception e)
        {
            LOGGER.error("Error checking port {} for IP: {}: {}", port, ip, e.getMessage());
            return false;
        }
    }


    public static String spawnPluginEngine(JsonArray pluginInput)
    {

        if (pluginInput == null || pluginInput.isEmpty())
        {
            LOGGER.error("Plugin input is null or empty");
            return null;
        }

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

            try (var writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)))
            {
                writer.write(inputString);
                writer.newLine();
                writer.flush();
            }



            var output = new StringBuilder();
            try (var reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    output.append(line);
                }
            }

            var completed = process.waitFor(PROCESS_TIMEOUT_SEC, TimeUnit.SECONDS);

            if (!completed)
            {
                LOGGER.error("Go plugin process timed out");
                process.destroyForcibly();
                return null;
            }

            var exitCode = process.exitValue();

            if (exitCode != 0)
            {
                LOGGER.error("Go plugin process failed with exit code: {}", exitCode);
                return null;
            }

            var outputStr = output.toString().trim();

            try
            {
                var resultArray = new JsonArray(outputStr);

                LOGGER.info("Go plugin output array: {}", resultArray.encode());

                if (resultArray.isEmpty())
                {
                    LOGGER.error("Go plugin returned empty JSON array");
                    return null;
                }

                var firstResult = resultArray.getJsonObject(0);

                if (firstResult == null)
                {
                    LOGGER.error("Go plugin returned null JSON object");

                    return null;
                }

                var status = firstResult.getString("status", "");

                if ("Success".equalsIgnoreCase(status))
                {
                    LOGGER.warn("Go plugin operation succeed: {}", firstResult.encode());
                }

                return status;
            }
            catch (Exception e)
            {
                try
                {
                    var result = new JsonObject(outputStr);

                    LOGGER.info("Go plugin output object: {}", result.encode());

                    return "fail";
                }
                catch (Exception e2)
                {
                    LOGGER.error("Failed to parse JSON output: {}, raw output: {}", e.getMessage(), outputStr);

                    return null;
                }
            }
        }
        catch (IOException e)
        {
            LOGGER.error("I/O error spawning Go plugin: {}", e.getMessage(), e);
            return null;
        }
        catch (InterruptedException e)
        {
            LOGGER.error("Process interrupted while spawning Go plugin: {}", e.getMessage(), e);
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
    }
}